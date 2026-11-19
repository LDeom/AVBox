package com.github.avbox.osc.api

import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.KVKeys

/**
 * First-run subscriptions supplied by the repository owner.
 *
 * Only URLs are bundled: remote TVBox configuration and playlist contents remain
 * remotely maintained and can be refreshed without an APK update.
 */
internal object BuiltinSourceCatalog {

    private const val VERSION = "3"
    private const val SPLIT = "\t"

    private val replacedVodSources = listOf(
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/cat/tjs/tv_config.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/cat/tjs/18_tv_config.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0821.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0825.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0826.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0827.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/367.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/9918.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/99188.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/dianshi.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/fty.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/js.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/jsm.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/tools/dianshi.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/tools/jsm.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/xiaosa/api.json",
    )

    private val previousCatalogVodSources = listOf(
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/xiaosa/api.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/jsm.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/js.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/fty.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/dianshi.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/0827.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/367.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/0826.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/0825.json?utm_source=chatgpt.com",
        "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/audit/repair-sources-2026-11/0821.json?utm_source=chatgpt.com",
    )

    private val vodSources = listOf(
        source("自用仓库（多仓点播）", "https://gh-proxy.com/https://raw.githubusercontent.com/niubihu1/tvbox/main/%E8%87%AA%E7%94%A8%E4%BB%93%E5%BA%93.txt"),
    )

    private val liveSources = listOf(
        source("Clun IPTV 直播", "https://clun.top/lib/iptv.m3u"),
    )

    fun installIfNeeded() {
        val installedVersion = KV.get(KVKeys.BUILTIN_SOURCE_CATALOG_VERSION, "")
        if (installedVersion == VERSION) return

        val currentVod = KV.get(KVKeys.SUBSCRIBE_LIST, ArrayList<String>())
        val currentLive = KV.get(KVKeys.LIVE_SUBSCRIBE_LIST, ArrayList<String>())
        val migratedVod = if (installedVersion.isNotEmpty() && installedVersion != VERSION) {
            removeSources(currentVod, replacedVodSources + previousCatalogVodSources)
        } else {
            currentVod
        }
        KV.put(KVKeys.SUBSCRIBE_LIST, merge(migratedVod, vodSources))
        KV.put(KVKeys.LIVE_SUBSCRIBE_LIST, merge(currentLive, liveSources))
        KV.put(KVKeys.BUILTIN_SOURCE_CATALOG_VERSION, VERSION)
    }

    private fun removeSources(current: ArrayList<String>, urls: List<String>): ArrayList<String> {
        val replaced = urls.toHashSet()
        return ArrayList(current.filterNot { subscriptionUrl(it) in replaced })
    }

    private fun merge(current: ArrayList<String>, defaults: List<String>): ArrayList<String> {
        val merged = ArrayList(current)
        val knownUrls = HashSet<String>()
        merged.forEach { knownUrls.add(subscriptionUrl(it)) }
        defaults.forEach { item ->
            val url = subscriptionUrl(item)
            if (url.isNotEmpty() && knownUrls.add(url)) merged.add(item)
        }
        return merged
    }

    private fun subscriptionUrl(value: String): String {
        val split = value.indexOf(SPLIT)
        return (if (split >= 0) value.substring(split + SPLIT.length) else value)
            .filterNot { it.isISOControl() }
            .trim()
    }

    private fun source(name: String, url: String): String = name + SPLIT + url
}
