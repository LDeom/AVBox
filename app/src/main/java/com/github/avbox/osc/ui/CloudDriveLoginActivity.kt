package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.github.avbox.osc.R
import com.github.avbox.osc.net.CloudDriveProviderCatalog
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.security.CloudCredentialVault
import org.json.JSONObject

class CloudDriveLoginActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PROVIDER_ID = "cloud_provider_id"

        fun start(context: Context, providerId: String) {
            context.startActivity(Intent(context, CloudDriveLoginActivity::class.java).putExtra(EXTRA_PROVIDER_ID, providerId))
        }
    }

    override fun hideSysBar() {
    }

    override fun init() {
        val provider = CloudDriveProviderCatalog.find(intent.getStringExtra(EXTRA_PROVIDER_ID).orEmpty())
        if (provider == null) {
            finish()
            return
        }
        enableTransparentEdgeToEdge()
        findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                val context = LocalContext.current
                var webView by remember { mutableStateOf<WebView?>(null) }
                var pageTitle by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(provider.title) + " · " + stringResource(R.string.cloud_drive_browser_login),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.cloud_drive_login_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pageTitle.isNotBlank()) {
                        Text(pageTitle, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webChromeClient = object : WebChromeClient() {
                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        pageTitle = title.orEmpty()
                                    }
                                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return request?.url?.scheme != "https"
                                    }
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        message = ""
                                    }
                                }
                                loadUrl(provider.loginUrl)
                                webView = this
                            }
                        },
                        update = { webView = it },
                    )
                    if (message.isNotBlank()) {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val manager = CookieManager.getInstance()
                                val cookiesByHost = JSONObject()
                                provider.cookieHosts.forEach { host ->
                                    val cookie = manager.getCookie("https://$host/").orEmpty()
                                    if (cookie.isNotBlank()) cookiesByHost.put(host, cookie)
                                }
                                if (cookiesByHost.length() == 0) {
                                    message = context.getString(R.string.cloud_drive_login_cookie_missing)
                                } else {
                                    runCatching {
                                        CloudCredentialVault.write(provider.id, cookiesByHost.toString())
                                    }.onSuccess {
                                        setResult(RESULT_OK)
                                        finish()
                                    }.onFailure {
                                        message = context.getString(R.string.cloud_drive_cookie_save_failed)
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.cloud_drive_login_save))
                        }
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
