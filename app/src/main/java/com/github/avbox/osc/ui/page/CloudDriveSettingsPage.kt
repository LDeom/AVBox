package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.WindowSize
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.SettingsCard
import com.github.avbox.osc.ui.components.SettingsCardPosition
import com.github.avbox.osc.ui.components.SettingsGroup
import com.github.avbox.osc.ui.components.TopBarActionBox
import com.github.avbox.osc.util.security.CloudCredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CloudCookieProvider(val id: String, val title: Int)

private val cloudCookieProviders = listOf(
    CloudCookieProvider("quark", R.string.cloud_drive_quark),
    CloudCookieProvider("uc", R.string.cloud_drive_uc),
    CloudCookieProvider("baidu", R.string.cloud_drive_baidu),
    CloudCookieProvider("xunlei", R.string.cloud_drive_xunlei),
    CloudCookieProvider("aliyun", R.string.cloud_drive_aliyun),
    CloudCookieProvider("guangya", R.string.cloud_drive_guangya),
    CloudCookieProvider("tianyi", R.string.cloud_drive_tianyi),
    CloudCookieProvider("pan123", R.string.cloud_drive_123),
)

@Composable
fun CloudDriveSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<String, String>() }
    val configured = remember { mutableStateMapOf<String, Boolean>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    var message by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cloudCookieProviders.forEach { provider ->
                val exists = runCatching { !CloudCredentialVault.read(provider.id).isNullOrBlank() }
                    .getOrElse { false }
                configured[provider.id] = exists
            }
        }
    }

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
                text = stringResource(R.string.cloud_drive_cookie_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsGroup(title = stringResource(R.string.cloud_drive_accounts)) {
                cloudCookieProviders.forEachIndexed { index, provider ->
                    SettingsCard(
                        when {
                            cloudCookieProviders.size == 1 -> SettingsCardPosition.SINGLE
                            index == 0 -> SettingsCardPosition.FIRST
                            index == cloudCookieProviders.lastIndex -> SettingsCardPosition.LAST
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
                            OutlinedTextField(
                                value = values[provider.id].orEmpty(),
                                onValueChange = { values[provider.id] = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.cloud_drive_cookie_input)) },
                                placeholder = { Text(stringResource(R.string.cloud_drive_cookie_placeholder)) },
                                minLines = 2,
                                maxLines = 4,
                                enabled = busy[provider.id] != true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = busy[provider.id] != true && values[provider.id].orEmpty().isNotBlank(),
                                    onClick = {
                                        val cookie = values[provider.id].orEmpty().trim()
                                        scope.launch {
                                            busy[provider.id] = true
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    CloudCredentialVault.write(provider.id, cookie)
                                                }
                                            }
                                            result.onSuccess {
                                                configured[provider.id] = true
                                                values[provider.id] = ""
                                                message = context.getString(R.string.cloud_drive_cookie_saved)
                                            }.onFailure {
                                                message = it.message ?: context.getString(R.string.cloud_drive_cookie_save_failed)
                                            }
                                            busy[provider.id] = false
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.common_save))
                                }
                                TextButton(
                                    enabled = busy[provider.id] != true && configured[provider.id] == true,
                                    onClick = {
                                        scope.launch {
                                            busy[provider.id] = true
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    CloudCredentialVault.remove(provider.id)
                                                }
                                            }
                                            result.onSuccess {
                                                configured[provider.id] = false
                                                values[provider.id] = ""
                                                message = context.getString(R.string.cloud_drive_cookie_cleared)
                                            }.onFailure {
                                                message = it.message ?: context.getString(R.string.cloud_drive_cookie_save_failed)
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
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
