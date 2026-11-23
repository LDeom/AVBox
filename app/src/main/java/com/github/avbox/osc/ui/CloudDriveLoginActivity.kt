package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ClipboardManager
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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.net.CloudDriveProviderCatalog
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.security.CloudCredentialVault
import org.json.JSONObject

/** External browser authorization handoff. External browser cookies are not accessed. */
class CloudDriveLoginActivity : BaseActivity() {
    companion object {
        private const val EXTRA_PROVIDER_ID = "cloud_provider_id"
        fun start(context: Context, providerId: String) {
            context.startActivity(Intent(context, CloudDriveLoginActivity::class.java).putExtra(EXTRA_PROVIDER_ID, providerId))
        }
    }

    override fun hideSysBar() = Unit

    override fun init() {
        val provider = CloudDriveProviderCatalog.find(intent.getStringExtra(EXTRA_PROVIDER_ID).orEmpty())
        if (provider == null) { finish(); return }
        enableTransparentEdgeToEdge()
        findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                val context = LocalContext.current
                var message by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(provider.title), style = MaterialTheme.typography.headlineSmall)
                    Text("改用手机系统浏览器登录，避免内置浏览器兼容问题。外部浏览器的 Cookie 不会被 AVBox 读取；登录后请在网盘页面复制授权 Cookie，返回此页面导入。", style = MaterialTheme.typography.bodyMedium)
                    Button(Modifier.fillMaxWidth(), onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.loginUrl))) }
                            .onFailure { message = "无法打开外部浏览器：${it.message.orEmpty()}" }
                    }) { Text("打开系统浏览器登录") }
                    Button(Modifier.fillMaxWidth(), onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val raw = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
                        if (raw.isEmpty()) {
                            message = "剪贴板为空。请先复制 Cookie。"
                        } else {
                            val credential = runCatching {
                                if (raw.startsWith("{")) {
                                    val input = JSONObject(raw)
                                    val valid = JSONObject()
                                    val names = mutableSetOf<String>()
                                    provider.cookieHosts.forEach { host ->
                                        val value = input.optString(host).takeIf { it.isNotBlank() }
                                        if (value != null) { valid.put(host, value); names.addAll(cookieNames(value)) }
                                    }
                                    if (!CloudDriveProviderCatalog.hasLoginCookie(provider.id, names.associateWith { "1" })) error("未识别到该网盘所需的登录凭据")
                                    valid.toString()
                                } else {
                                    if (!CloudDriveProviderCatalog.hasLoginCookie(provider.id, cookieNames(raw).associateWith { "1" })) error("剪贴板内容未包含该网盘可识别的登录 Cookie")
                                    raw
                                }
                            }.getOrElse { message = it.message ?: "凭据格式无法识别"; null }
                            if (credential != null) runCatching { CloudCredentialVault.write(provider.id, credential) }
                                .onSuccess {
                                    AppLog.i("cloud-credential import=success provider=${provider.id} mode=clipboard")
                                    message = "导入成功，已安全保存并自动接入配置中心。"
                                    setResult(RESULT_OK); finish()
                                }.onFailure { message = "保存失败：${it.message.orEmpty()}" }
                        }
                    }) { Text("导入剪贴板并自动注入") }
                    Text("支持单条 Cookie，或 JSON（按授权域名保存 Cookie）。请勿将 Cookie/Token 分享给他人。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            }
        }
    }
}

private fun cookieNames(raw: String): Set<String> = raw.trim().removePrefix("Cookie:").split(';').mapNotNull { part ->
    val index = part.indexOf('=')
    if (index <= 0) null else part.substring(0, index).trim().takeIf { it.isNotEmpty() }
}.toSet()
