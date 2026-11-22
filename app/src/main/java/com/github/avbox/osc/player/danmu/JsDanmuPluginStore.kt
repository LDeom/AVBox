package com.github.avbox.osc.player.danmu

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class JsDanmuPlugin(val id: String, val name: String, val fileName: String, val enabled: Boolean, val sizeBytes: Long)

object JsDanmuPluginStore {
    private const val PREFS = "js_danmu_plugins"
    private const val KEY_ITEMS = "items"
    private const val MAX_PLUGIN_BYTES = 2L * 1024L * 1024L
    private fun directory(context: Context) = File(context.filesDir, "danmaku-js-plugins").apply { if (!exists()) mkdirs() }

    @Synchronized fun list(context: Context): List<JsDanmuPlugin> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]")) }.getOrDefault(JSONArray())
        val result = ArrayList<JsDanmuPlugin>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val fileName = item.optString("file")
            if (id.isBlank() || fileName.isBlank()) continue
            val file = File(directory(context), fileName)
            if (!file.isFile) continue
            result += JsDanmuPlugin(id, item.optString("name", file.nameWithoutExtension), fileName, item.optBoolean("enabled", true), file.length())
        }
        return result
    }

    @Synchronized fun import(context: Context, uri: Uri): JsDanmuPlugin {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "danmaku-plugin.js"
        require(displayName.endsWith(".js", ignoreCase = true)) { "请选择 .js 插件文件" }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_PLUGIN_BYTES) { "插件文件不能超过 2 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("无法读取插件文件")
        require(bytes.isNotEmpty()) { "插件文件为空" }
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        require(!text.contains("\u0000")) { "插件文件不是有效的 UTF-8 文本" }
        val id = MessageDigest.getInstance("SHA-256").digest(bytes).take(12).joinToString("") { "%02x".format(it) }
        val fileName = "$id.js"
        File(directory(context), fileName).writeBytes(bytes)
        val updated = list(context).filterNot { it.id == id } + JsDanmuPlugin(id, displayName.removeSuffix(".js"), fileName, true, bytes.size.toLong())
        persist(context, updated)
        return updated.last()
    }

    @Synchronized fun setEnabled(context: Context, id: String, enabled: Boolean) =
        persist(context, list(context).map { if (it.id == id) it.copy(enabled = enabled) else it })

    @Synchronized fun remove(context: Context, id: String) {
        val current = list(context)
        current.firstOrNull { it.id == id }?.let { File(directory(context), it.fileName).delete() }
        persist(context, current.filterNot { it.id == id })
    }

    private fun persist(context: Context, plugins: List<JsDanmuPlugin>) {
        val array = JSONArray()
        plugins.forEach { p -> array.put(JSONObject().put("id", p.id).put("name", p.name).put("file", p.fileName).put("enabled", p.enabled)) }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).commit()) { "保存插件列表失败" }
    }
}