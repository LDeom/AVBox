package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.avbox.osc.R
import com.github.avbox.osc.net.CloudDriveProviderCatalog
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.security.CloudCredentialVault
import org.json.JSONObject
import kotlin.math.roundToInt

class CloudDriveLoginActivity : BaseActivity() {
    private var loginWebView: WebView? = null
    private var lastLoggedProgressBucket = -1

    companion object {
        private const val EXTRA_PROVIDER_ID = "cloud_provider_id"
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun start(context: Context, providerId: String) {
            context.startActivity(Intent(context, CloudDriveLoginActivity::class.java).putExtra(EXTRA_PROVIDER_ID, providerId))
        }
    }

    override fun hideSysBar() = Unit

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
                var pageTitle by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                var loading by remember { mutableStateOf(false) }
                var progress by remember { mutableStateOf(0) }
                var zoomPercent by remember { mutableStateOf(100) }

                LaunchedEffect(provider.id) {
                    AppLog.i("echo-webview-login provider=${provider.id} login=started")
                }

                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(provider.title) + " · " + stringResource(R.string.cloud_drive_browser_login),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.cloud_drive_login_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pageTitle.isNotBlank()) {
                        Text(pageTitle, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    if (loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.padding(2.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(stringResource(R.string.cloud_drive_login_loading, progress))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = {
                            loginWebView?.let { view ->
                                view.adjustZoom(0.9f)
                            }
                        }) { Text("−") }
                        Text("${zoomPercent}% ", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = {
                            loginWebView?.let { view ->
                                view.adjustZoom(1.1f)
                            }
                        }) { Text("+") }
                        TextButton(onClick = {
                            loginWebView?.let { view ->
                                view.setZoomScale(1f)
                            }
                        }) { Text("重置") }
                        TextButton(onClick = { loginWebView?.goBack() }, enabled = loginWebView?.canGoBack() == true) { Text("后退") }
                        TextButton(onClick = { loginWebView?.goForward() }, enabled = loginWebView?.canGoForward() == true) { Text("前进") }
                        TextButton(onClick = { loginWebView?.reload() }) { Text("刷新") }
                    }

                    if (message.isNotBlank()) {
                        TextButton(onClick = { loginWebView?.reload() }) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }

                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { viewContext ->
                            DesktopPanWebView(viewContext).apply {
                                onZoomChanged = { zoomPercent = it }
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    userAgentString = DESKTOP_UA
                                }
                                isVerticalScrollBarEnabled = true
                                isHorizontalScrollBarEnabled = true
                                isFocusable = true
                                isFocusableInTouchMode = true

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                        val sourceHost = runCatching {
                                            android.net.Uri.parse(consoleMessage.sourceId()).host.orEmpty()
                                        }.getOrDefault("")
                                        AppLog.i(
                                            "echo-webview-login provider=${provider.id} js-console level=${consoleMessage.messageLevel()} host=$sourceHost line=${consoleMessage.lineNumber()}",
                                        )
                                        return true
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        pageTitle = title.orEmpty()
                                    }

                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                        loading = newProgress < 100
                                        val bucket = newProgress / 25
                                        if (bucket != lastLoggedProgressBucket) {
                                            lastLoggedProgressBucket = bucket
                                            AppLog.i("echo-webview-login provider=${provider.id} progress=${bucket * 25}%")
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val scheme = request?.url?.scheme?.lowercase().orEmpty()
                                        val allowed = scheme == "https" || scheme == "http"
                                        if (!allowed) {
                                            AppLog.i("echo-webview-login provider=${provider.id} navigation=blocked scheme=$scheme")
                                        }
                                        return !allowed
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        AppLog.operationEvent(AppLog.newOperationId(), "cloud.browser.navigation", "start", "provider=${provider.id} host=${runCatching { android.net.Uri.parse(url.orEmpty()).host.orEmpty() }.getOrDefault("")}")
                                        message = ""
                                        loading = true
                                        progress = 0
                                        lastLoggedProgressBucket = -1
                                        val host = runCatching { android.net.Uri.parse(url).host.orEmpty() }.getOrDefault("")
                                        AppLog.i("echo-webview-login provider=${provider.id} page=start host=$host")
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        AppLog.operationEvent(AppLog.newOperationId(), "cloud.browser.navigation", "finished", "provider=${provider.id} host=${runCatching { android.net.Uri.parse(url.orEmpty()).host.orEmpty() }.getOrDefault("")}")
                                        loading = false
                                        progress = 100
                                        val host = runCatching { android.net.Uri.parse(url).host.orEmpty() }.getOrDefault("")
                                        AppLog.i("echo-webview-login provider=${provider.id} page=finished host=$host")
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?,
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            loading = false
                                            AppLog.operationEvent(AppLog.newOperationId(), "cloud.browser.navigation", "error", "provider=${provider.id} errorCode=${error?.errorCode ?: -1}")
                                            message = context.getString(
                                                R.string.cloud_drive_login_page_error,
                                                error?.errorCode ?: -1,
                                            )
                                            AppLog.i(
                                                "echo-webview-login provider=${provider.id} page=error host=${request.url.host.orEmpty()} code=${error?.errorCode ?: -1}",
                                            )
                                        }
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        response: WebResourceResponse?,
                                    ) {
                                        if (request?.isForMainFrame == true && (response?.statusCode ?: 0) >= 400) {
                                            loading = false
                                            AppLog.operationEvent(AppLog.newOperationId(), "cloud.browser.navigation", "http_error", "provider=${provider.id} status=${response?.statusCode ?: -1}")
                                            message = context.getString(
                                                R.string.cloud_drive_login_http_error,
                                                response?.statusCode ?: -1,
                                            )
                                            AppLog.i(
                                                "echo-webview-login provider=${provider.id} page=http-error host=${request.url.host.orEmpty()} status=${response?.statusCode ?: -1}",
                                            )
                                        }
                                    }
                                }
                                loginWebView = this
                                loadUrl(provider.loginUrl)
                            }
                        },
                    )

                    if (message.isNotBlank()) {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val manager = CookieManager.getInstance()
                                manager.flush()
                                val cookiesByHost = JSONObject()
                                provider.cookieHosts.forEach { host ->
                                    manager.getCookie("https://$host/")
                                        .orEmpty()
                                        .takeIf { it.isNotBlank() }
                                        ?.let { raw -> cookiesByHost.put(host, raw) }
                                }
                                if (cookiesByHost.length() == 0) {
                                    message = context.getString(R.string.cloud_drive_login_cookie_missing)
                                } else {
                                    runCatching { CloudCredentialVault.write(provider.id, cookiesByHost.toString()) }
                                        .onSuccess {
                                            AppLog.i("echo-webview-login provider=${provider.id} cookie-save=success hosts=${cookiesByHost.length()}")
                                            setResult(RESULT_OK)
                                            finish()
                                        }
                                        .onFailure {
                                            AppLog.i("echo-webview-login provider=${provider.id} cookie-save=failed")
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
        AppLog.i("echo-webview-login activity=destroy")
        loginWebView?.let { view ->
            view.stopLoading()
            view.clearHistory()
            view.removeAllViews()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        loginWebView = null
        super.onDestroy()
    }
}

private class DesktopPanWebView(context: Context) : WebView(context) {
    var onZoomChanged: ((Int) -> Unit)? = null
    private var mouseDragging = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    fun adjustZoom(factor: Float) {
        val currentScale = scale.takeIf { it > 0f } ?: 1f
        val targetScale = (currentScale * factor).coerceIn(0.5f, 3f)
        if (targetScale != currentScale) {
            zoomBy(targetScale / currentScale)
            onZoomChanged?.invoke((targetScale * 100).roundToInt())
        }
    }

    fun setZoomScale(targetScale: Float) {
        val currentScale = scale.takeIf { it > 0f } ?: 1f
        val safeTarget = targetScale.coerceIn(0.5f, 3f)
        if (safeTarget != currentScale) zoomBy(safeTarget / currentScale)
        onZoomChanged?.invoke((safeTarget * 100).roundToInt())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                        mouseDragging = true
                        lastMouseX = event.x
                        lastMouseY = event.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mouseDragging) {
                        val dx = (lastMouseX - event.x).roundToInt()
                        val dy = (lastMouseY - event.y).roundToInt()
                        if (dx != 0 || dy != 0) scrollBy(dx, dy)
                        lastMouseX = event.x
                        lastMouseY = event.y
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mouseDragging) {
                        mouseDragging = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (event.metaState and android.view.KeyEvent.META_CTRL_ON != 0 && vertical != 0f) {
                val factor = if (vertical > 0f) 1.1f else 0.9f
                adjustZoom(factor)
            } else if (vertical != 0f || horizontal != 0f) {
                scrollBy((-horizontal * 64f).roundToInt(), (-vertical * 64f).roundToInt())
            } else {
                return super.onGenericMotionEvent(event)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
