package com.github.avbox.osc.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.R
import com.github.avbox.osc.cloud.GuangYaFile
import com.github.avbox.osc.cloud.GuangYaPanClient
import com.github.avbox.osc.ui.WindowSize
import com.github.avbox.osc.ui.CloudDrivePlaybackActivity
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.SettingsCard
import com.github.avbox.osc.ui.components.SettingsCardPosition
import com.github.avbox.osc.ui.components.SettingsGroup
import com.github.avbox.osc.ui.components.SettingsRow
import com.github.avbox.osc.ui.components.TopBarActionBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CloudDriveSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by rememberSaveable { mutableStateOf(GuangYaPanClient.accountPhone()) }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(GuangYaPanClient.isLoggedIn()) }
    val files = remember { mutableStateListOf<GuangYaFile>() }
    val folderStack = remember { mutableStateListOf<String>() }
    val currentParent = folderStack.lastOrNull().orEmpty()

    suspend fun loadFiles(parentId: String = currentParent) {
        busy = true
        val result = runCatching {
            withContext(Dispatchers.IO) { GuangYaPanClient.listFiles(parentId) }
        }
        files.clear()
        result.onSuccess {
            files.addAll(it)
            message = context.getString(R.string.cloud_drive_file_count, it.size)
        }.onFailure {
            message = it.message ?: context.getString(R.string.cloud_drive_request_failed)
        }
        busy = false
    }


    LaunchedEffect(Unit) {
        if (loggedIn) loadFiles("")
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
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SettingsGroup(title = stringResource(R.string.cloud_drive_accounts)) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsRow(
                        title = stringResource(R.string.cloud_drive_aliyun),
                        subtitle = stringResource(R.string.cloud_drive_provider_pending),
                        valueText = stringResource(R.string.common_not_set),
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.cloud_drive_quark),
                        subtitle = stringResource(R.string.cloud_drive_provider_pending),
                        valueText = stringResource(R.string.common_not_set),
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.cloud_drive_baidu),
                        subtitle = stringResource(R.string.cloud_drive_provider_pending),
                        valueText = stringResource(R.string.common_not_set),
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.cloud_drive_115),
                        subtitle = stringResource(R.string.cloud_drive_provider_pending),
                        valueText = stringResource(R.string.common_not_set),
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.cloud_drive_uc),
                        subtitle = stringResource(R.string.cloud_drive_provider_pending),
                        valueText = stringResource(R.string.common_not_set),
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsRow(
                        title = stringResource(R.string.cloud_drive_guangya),
                        subtitle = stringResource(R.string.cloud_drive_guangya_scope),
                        valueText = stringResource(if (loggedIn) R.string.common_set else R.string.common_not_set),
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.cloud_drive_guangya)) {
                SettingsCard(SettingsCardPosition.SINGLE) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.cloud_drive_guangya_scope), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.cloud_drive_phone)) },
                            singleLine = true,
                            enabled = !busy,
                        )
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = { smsCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.cloud_drive_sms_code)) },
                            singleLine = true,
                            enabled = !busy,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !busy && phone.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        message = ""
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) { GuangYaPanClient.sendSmsCode(phone) }
                                        }
                                        message = result.getOrElse { it.message ?: context.getString(R.string.cloud_drive_request_failed) }
                                        busy = false
                                    }
                                },
                            ) { Text(stringResource(R.string.cloud_drive_send_code)) }
                            Button(
                                enabled = !busy && phone.isNotBlank() && smsCode.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                GuangYaPanClient.login(phone, smsCode)
                                                GuangYaPanClient.checkAccount()
                                            }
                                        }
                                        result.onSuccess {
                                            loggedIn = true
                                            smsCode = ""
                                            message = context.getString(R.string.cloud_drive_login_success)
                                            folderStack.clear()
                                            loadFiles("")
                                        }.onFailure {
                                            message = it.message ?: context.getString(R.string.cloud_drive_login_failed)
                                        }
                                        busy = false
                                    }
                                },
                            ) { Text(stringResource(R.string.cloud_drive_login)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = !busy && loggedIn,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) { GuangYaPanClient.checkAccount() }
                                        }
                                        message = result.fold(
                                            { context.getString(R.string.cloud_drive_account_valid, it.phoneNumber) },
                                            { it.message ?: context.getString(R.string.cloud_drive_account_invalid) },
                                        )
                                        busy = false
                                    }
                                },
                            ) { Text(stringResource(R.string.cloud_drive_check_status)) }
                            TextButton(
                                enabled = !busy && loggedIn,
                                onClick = {
                                    GuangYaPanClient.logout()
                                    loggedIn = false
                                    files.clear()
                                    folderStack.clear()
                                    message = context.getString(R.string.cloud_drive_logged_out)
                                },
                            ) { Text(stringResource(R.string.cloud_drive_logout)) }
                        }
                        if (message.isNotBlank()) {
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (loggedIn) {
                SettingsGroup(title = stringResource(R.string.cloud_drive_files)) {
                    SettingsCard(SettingsCardPosition.FIRST) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (folderStack.isEmpty()) stringResource(R.string.cloud_drive_root)
                                    else stringResource(R.string.cloud_drive_folder_depth, folderStack.size),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                TextButton(
                                    enabled = !busy && folderStack.isNotEmpty(),
                                    onClick = {
                                        if (folderStack.isNotEmpty()) folderStack.removeAt(folderStack.lastIndex)
                                        scope.launch { loadFiles(folderStack.lastOrNull().orEmpty()) }
                                    },
                                ) { Text(stringResource(R.string.cloud_drive_parent)) }
                                TextButton(enabled = !busy, onClick = { scope.launch { loadFiles() } }) {
                                    Text(stringResource(R.string.common_refresh))
                                }
                            }
                            if (files.isEmpty()) {
                                Text(stringResource(R.string.cloud_drive_empty), style = MaterialTheme.typography.bodySmall)
                            }
                            files.forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = (file.isFolder || isPlayableCloudFile(file.fileName)) && !busy) {
                                            if (file.isFolder) {
                                                folderStack.add(file.fileId)
                                                scope.launch { loadFiles(file.fileId) }
                                            } else {
                                                CloudDrivePlaybackActivity.start(context, file.fileId, file.fileName)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (file.isFolder) "📁" else if (isPlayableCloudFile(file.fileName)) "🎬" else "📄")
                                    Text(
                                        file.fileName,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        if (file.isFolder) "" else android.text.format.Formatter.formatFileSize(context, file.fileSize),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun isPlayableCloudFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in setOf("mp4", "mkv", "mov", "avi", "ts", "m3u8", "mpg", "mpeg", "wmv", "flv", "webm", "m4v")
}
