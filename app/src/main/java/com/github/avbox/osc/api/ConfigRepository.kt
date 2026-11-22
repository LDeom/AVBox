package com.github.avbox.osc.api

import com.github.avbox.osc.util.KVKeys
import com.github.avbox.osc.util.HistoryHelper
import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.ApiLineSignal

internal fun createVodSwitchSnapshot(
    vodUrl: String,
    liveUrl: String,
    vodLines: List<String>,
    vodLineSource: String,
    liveLines: List<String>,
    liveLineSource: String,
): ConfigRepository.VodSwitchSnapshot = ConfigRepository.VodSwitchSnapshot(
    vodUrl = vodUrl,
    liveUrl = liveUrl,
    vodLines = vodLines.toList(),
    vodLineSource = vodLineSource,
    liveLines = liveLines.toList(),
    liveLineSource = liveLineSource,
)

object ConfigRepository {

    internal val liveSourceLock = Any()

    data class VodSwitchSnapshot(
        val vodUrl: String,
        val liveUrl: String,
        val vodLines: List<String>,
        val vodLineSource: String,
        val liveLines: List<String>,
        val liveLineSource: String,
    )

    fun snapshotVodSwitch(): VodSwitchSnapshot = synchronized(liveSourceLock) {
        createVodSwitchSnapshot(
            vodUrl = vodUrl(),
            liveUrl = liveUrl(),
            vodLines = KV.get(KVKeys.API_LINE_LIST, ArrayList<String>()),
            vodLineSource = KV.get(KVKeys.API_LINE_SOURCE, ""),
            liveLines = KV.get(KVKeys.LIVE_API_LINE_LIST, ArrayList<String>()),
            liveLineSource = KV.get(KVKeys.LIVE_API_LINE_SOURCE, ""),
        )
    }

    fun restoreVodSwitch(snapshot: VodSwitchSnapshot) = synchronized(liveSourceLock) {
        KV.put(KVKeys.API_URL, snapshot.vodUrl)
        KV.put(KVKeys.LIVE_API_URL, snapshot.liveUrl)
        KV.put(KVKeys.API_LINE_LIST, ArrayList(snapshot.vodLines))
        KV.put(KVKeys.API_LINE_SOURCE, snapshot.vodLineSource)
        KV.put(KVKeys.LIVE_API_LINE_LIST, ArrayList(snapshot.liveLines))
        KV.put(KVKeys.LIVE_API_LINE_SOURCE, snapshot.liveLineSource)
        ApiLineSignal.notifyChanged()
    }

    fun vodUrl(): String = KV.get(KVKeys.API_URL, "")

    fun liveUrl(): String = KV.get(KVKeys.LIVE_API_URL, "")

    fun switchVodSource(url: String): Boolean = synchronized(liveSourceLock) {
        val followLive = ApiConfig.isLiveFollowVod()
        val oldApi = vodUrl()
        val oldFollowTarget = liveUrl().ifEmpty { oldApi }
        HistoryHelper.setApiHistory(url)
        KV.put(KVKeys.API_URL, url)
        if (followLive) {
            KV.put(KVKeys.LIVE_API_URL, "")
            if (url != oldFollowTarget) HistoryHelper.clearLiveApiLineList()
        }
        if (!HistoryHelper.isApiLineHistory(url)) HistoryHelper.clearApiLineList()
        oldApi == url
    }

    fun switchLiveSource(url: String) = synchronized(liveSourceLock) {
        KV.put(KVKeys.LIVE_API_URL, url)
        if (url.isEmpty()) {
            HistoryHelper.clearLiveApiLineList()
        } else {
            HistoryHelper.setLiveApiHistory(url)
            if (!HistoryHelper.isLiveApiLineHistory(url)) HistoryHelper.clearLiveApiLineList()
        }
        ApiConfig.get().clearLiveHosts()
        ApiConfig.get().invalidateLiveConfig()
    }
