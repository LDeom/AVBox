package com.github.avbox.osc.ui.page

import android.content.Context

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import android.content.ClipData
import android.content.ClipboardManager
import android.webkit.CookieManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.avbox.osc.R
import com.github.avbox.osc.net.CloudDriveProviderCatalog
import com.github.avbox.osc.ui.CloudDriveLoginActivity
import com.github.avbox.osc.ui.WindowSize
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.SettingsCard
import com.github.avbox.osc.ui.components.SettingsCardPosition
import com.github.avbox.osc.ui.components.SettingsGroup
import com.github.avbox.osc.ui.components.TopBarActionBox
import com.github.avbox.osc.util.security.CloudCredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext

@Composable
fun CloudDriveSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configured = remember { mutableStateMapOf<String, Boolean>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    var message by rememberSaveable { mutableStateOf("") }

    suspend fun refreshStatus() {
        val results = withContext(Dispatchers.IO) {
            CloudDriveProviderCatalog.providers.associate { provider ->
                provider.id to runCatching { !CloudCredentialVault.read(provider.id).isNullOrBlank() }.getOrDefault(false)
            }
        }
        results.forEach { (id, value) -> configured[id] = value }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refreshStatus() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refreshStatus() }

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.settings_cloud_drive),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(R.drawable.ic_arrow_left, stringResource(R.string.common_back), onClick = onNavigateBack)
        },
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = WindowSize.CONTENT_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = topPad + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.cloud_drive_login_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsGroup(title = stringResource(R.string.cloud_drive_accounts)) {
                CloudDriveProviderCatalog.providers.forEachIndexed { index, provider ->
                    SettingsCard(
                        when {
                            CloudDriveProviderCatalog.providers.size == 1 -> SettingsCardPosition.SINGLE
                            index == 0 -> SettingsCardPosition.FIRST
                            index == CloudDriveProviderCatalog.providers.lastIndex -> SettingsCardPosition.LAST
                            else -> SettingsCardPosition.MIDDLE
                        },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(provider.title),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(
                                        if (configured[provider.id] == true) R.string.common_set else R.string.common_not_set,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    enabled = busy[provider.id] != true,
                                    onClick = { CloudDriveLoginActivity.start(context, provider.id) },
                                ) {
                                    Text(stringResource(if (configured[provider.id] == true) R.string.cloud_drive_login_again else R.string.cloud_drive_browser_login))
                                }
                                TextButton(
                                    enabled = configured[provider.id] == true,
                                    onClick = {
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                CookieManager.getInstance().apply { flush() }.let { manager ->
                                                    provider.cookieHosts.mapNotNull { host ->
                                                        manager.getCookie("https://$host/").orEmpty().takeIf { it.isNotBlank() }?.let { "$host\n$it" }
                                                    }.joinToString("\n\n")
                                                }
                                            }
                                            if (result.isBlank()) {
                                                message = context.getString(R.string.cloud_drive_login_cookie_missing)
                                            } else {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText(provider.id, result))
                                                message = context.getString(R.string.cloud_drive_cookie_copied)
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.cloud_drive_cookie_copy))
                                }
                                TextButton(
                                    enabled = busy[provider.id] != true && configured[provider.id] == true,
                                    onClick = {
                                        scope.launch {
                                            busy[provider.id] = true
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) { CloudCredentialVault.remove(provider.id) }
                                                clearProviderCookies(provider.cookieHosts)
                                            }
                                            result.onSuccess {
                                                configured[provider.id] = false
                                                message = context.getString(R.string.cloud_drive_cookie_cleared)
                                            }.onFailure {
                                                message = context.getString(R.string.cloud_drive_cookie_save_failed)
                                            }
                                            busy[provider.id] = false
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.common_clear))
                                }
                            }
                        }
                    }
                }
            }
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private suspend fun clearProviderCookies(hosts: Set<String>) {
    val manager = CookieManager.getInstance()
    for (host in hosts) {
        val url = "https://$host/"
        val cookies = manager.getCookie(url).orEmpty()
        val names = cookies.split(";").mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator).trim().takeIf { it.isNotEmpty() }
        }.distinct()
        val domains = buildList {
            add(host)
            val labels = host.split('.')
            for (start in 1 until labels.lastIndex) {
                add(labels.drop(start).joinToString("."))
            }
        }.distinct()
        for (name in names) {
            val expired = linkedSetOf("$name=; Max-Age=0; Path=/; Secure")
            domains.forEach { domain ->
                expired += "$name=; Max-Age=0; Path=/; Domain=$domain; Secure"
            }
            for (cookie in expired) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    manager.setCookie(url, cookie) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }
    }
    manager.flush()
}
