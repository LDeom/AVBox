package com.github.avbox.osc.ui.page

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.player.danmu.JsDanmuPluginStore
import com.github.avbox.osc.player.danmu.JsDanmuPluginRuntime
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.TopBarActionBox
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun JsDanmuPluginScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf(JsDanmuPluginStore.list(context)) }
    var importing by remember { mutableStateOf(false) }
    var checkingId by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) { runCatching { JsDanmuPluginStore.import(context, uri) } }
            plugins = JsDanmuPluginStore.list(context)
            importing = false
            result.exceptionOrNull()?.let { Toast.makeText(context, it.message ?: "导入失败", Toast.LENGTH_LONG).show() }
                ?: Toast.makeText(context, "插件已导入；播放器会在常规弹幕源无结果时尝试调用已启用插件。请先运行检查。", Toast.LENGTH_LONG).show()
        }
    }
    AppTopBarScaffold(
        titleContent = { Text("JS 弹幕插件", style = MaterialTheme.typography.headlineSmall) },
        navigationIcon = { TopBarActionBox(com.github.avbox.osc.R.drawable.ic_arrow_left, "返回", onClick = onNavigateBack) },
    ) { topPad, _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(topPad + 8.dp))
            Text("导入 QuickJS 可执行的 CommonJS 风格 .js 弹幕插件。播放器要求导出 search(query) 和 load(candidate)。文件保存在应用私有目录；单个文件最大 2 MB。完整 Node.js 原生模块、require 依赖树及 Promise 目前尚不支持。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = { launcher.launch(arrayOf("application/javascript", "text/javascript", "text/plain", "application/octet-stream")) }, enabled = !importing) {
                Text(if (importing) "正在导入…" else "导入 JS 插件")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                items(plugins, key = { it.id }) { plugin ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                                Text("${plugin.fileName} · ${plugin.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = plugin.enabled, onCheckedChange = { enabled ->
                                JsDanmuPluginStore.setEnabled(context, plugin.id, enabled)
                                plugins = JsDanmuPluginStore.list(context)
                            })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(enabled = checkingId == null, onClick = {
                                scope.launch {
                                    checkingId = plugin.id
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            JsDanmuPluginRuntime(JsDanmuPluginStore.readSource(context, plugin.id)).use { runtime ->
                                                runtime.initialize()
                                            }
                                        }
                                    }
                                    checkingId = null
                                    result.onSuccess { meta ->
                                        Toast.makeText(context, "运行检查通过：${meta.optString("name")}", Toast.LENGTH_LONG).show()
                                    }.onFailure {
                                        Toast.makeText(context, "运行检查失败：${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) { Text(if (checkingId == plugin.id) "检查中…" else "运行检查") }
                            TextButton(onClick = {
                                JsDanmuPluginStore.remove(context, plugin.id)
                                plugins = JsDanmuPluginStore.list(context)
                            }) { Text("删除") }
                        }
                    }
                }
                if (plugins.isEmpty()) item { Text("尚未导入 JS 弹幕插件", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)) }
            }
        }
    }
}