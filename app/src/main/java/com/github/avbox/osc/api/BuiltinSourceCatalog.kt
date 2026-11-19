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

    private const val VERSION = "1"
    private const val SPLIT = "\t"

    private val vodSources = listOf(
        source("TVBox 影视配置", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/cat/tjs/tv_config.json"),
        source("TVBox 18+ 成人配置", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/cat/tjs/18_tv_config.json"),
        source("TVBox 0821 综合", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0821.json"),
        source("TVBox 0825 综合", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0825.json"),
        source("TVBox 0826 综合", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0826.json"),
        source("TVBox 0827 综合", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/0827.json"),
        source("TVBox 367 综合", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/367.json"),
        source("TVBox 9918 成人 18+", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/9918.json"),
        source("TVBox 99188 成人 18+", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/99188.json"),
        source("TVBox 电视配置", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/dianshi.json"),
        source("TVBox 饭太硬配置", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/fty.json"),
        source("TVBox JS 综合", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/js.json"),
        source("TVBox JSM 综合", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/json/jsm.json"),
        source("TVBox 工具版电视", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/tools/dianshi.json"),
        source("TVBox 工具版 JSM", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/tools/jsm.json"),
        source("TVBox 小洒配置", "https://gh-proxy.com/https://raw.githubusercontent.com/LDeom/tvbox/master/xiaosa/api.json"),
    )

    private val liveSources = listOf(
        source("Clun IPTV 直播", "https://clun.top/lib/iptv.m3u"),
    )

    fun installIfNeeded() {
        if (KV.get(KVKeys.BUILTIN_SOURCE_CATALOG_VERSION, "") == VERSION) return

        val currentVod = KV.get(KVKeys.SUBSCRIBE_LIST, ArrayList<String>())
        val currentLive = KV.get(KVKeys.LIVE_SUBSCRIBE_LIST, ArrayList<String>())
        KV.put(KVKeys.SUBSCRIBE_LIST, merge(currentVod, vodSources))
        KV.put(KVKeys.LIVE_SUBSCRIBE_LIST, merge(currentLive, liveSources))
        KV.put(KVKeys.BUILTIN_SOURCE_CATALOG_VERSION, VERSION)
    }

    private fun merge(current: ArrayList<String>, defaults: List<String>): ArrayList<String> {
        val merged = ArrayList(current)
        val knownUrls = merged.mapTo(HashSet()) { subscriptionUrl(it) }
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
