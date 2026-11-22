package com.github.avbox.osc.player.danmu

import android.content.Context
import com.github.avbox.osc.util.AppLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs enabled user plugins as a fallback when the configured built-in/remote danmaku
 * source returned no URL. Ambiguous search results are deliberately not auto-selected.
 */
object JsDanmuPluginLoader {
    fun searchAndLoad(context: Context, title: String, episode: String): String? {
        val plugins = JsDanmuPluginStore.list(context).filter { it.enabled }
        val operationId = AppLog.newOperationId()
        AppLog.operationEvent(operationId, "danmu.plugin", "start", "enabledCount=${plugins.size}")
        for (plugin in plugins) {
            try {
                JsDanmuPluginStore.readSource(context, plugin.id).let { source ->
                    JsDanmuPluginRuntime(source).use { runtime ->
                        AppLog.operationEvent(operationId, "danmu.plugin", "attempt", "plugin=${plugin.id}")
                        val meta = runtime.initialize()
                        if (!meta.optBoolean("hasSearch") || !meta.optBoolean("hasLoad")) {
                            AppLog.i("echo-danmu JS plugin skipped: missing search/load " + plugin.id)
                            return@use
                        }
                        val candidates = runtime.search(title, episode)
                        val candidateList = ArrayList<JSONObject>(candidates.length())
                        for (index in 0 until candidates.length()) candidates.optJSONObject(index)?.let(candidateList::add)
                        val selected = selectCandidate(candidateList, title, episode)
                        if (selected == null) {
                            AppLog.i("echo-danmu JS plugin no unique candidate: " + plugin.id)
                            return@use
                        }
                        val comments = runtime.load(selected)
                        val xml = JsDanmuPluginResultAdapter.toDanmakuXml(comments)
                        if (xml != "<i></i>") {
                            AppLog.operationEvent(operationId, "danmu.plugin", "success", "plugin=${plugin.id}")
                            return xml
                        }
                    }
                }
            } catch (error: Throwable) {
                AppLog.e("echo-danmu JS plugin error id=" + plugin.id + ": " + (error.message ?: error.javaClass.simpleName))
                AppLog.operationEvent(operationId, "danmu.plugin", "failure", "plugin=${plugin.id} error=${error.javaClass.simpleName}")
            }
        }
        AppLog.operationEvent(operationId, "danmu.plugin", "empty")
        return null
    }

    private fun selectCandidate(items: List<JSONObject>, title: String, episode: String): JSONObject? {
        if (items.isEmpty()) return null
        if (items.size == 1) return items.first()
        val normalizedTitle = normalize(title)
        val normalizedEpisode = normalize(episode)
        val scored = items.map { item ->
            val candidateTitle = normalize(firstNonEmpty(item, "title", "name", "vod_name", "animeTitle"))
            val candidateEpisode = normalize(firstNonEmpty(item, "episode", "episodeTitle", "episodeName", "label"))
            var score = 0
            if (candidateTitle.isNotEmpty() && (candidateTitle == normalizedTitle ||
                    candidateTitle.contains(normalizedTitle) || normalizedTitle.contains(candidateTitle))) score += 2
            if (normalizedEpisode.isNotEmpty() && candidateEpisode.isNotEmpty() &&
                (candidateEpisode == normalizedEpisode || candidateEpisode.contains(normalizedEpisode) ||
                    normalizedEpisode.contains(candidateEpisode))) score += 4
            item to score
        }.sortedByDescending { it.second }
        if (scored.first().second == 0) return null
        if (scored.size > 1 && scored[0].second == scored[1].second) return null
        return scored.first().first
    }

    private fun firstNonEmpty(item: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = item.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[\\s\\p{Punct}·•，。！？：；、“”‘’（）【】《》_-]+"), "")
}
