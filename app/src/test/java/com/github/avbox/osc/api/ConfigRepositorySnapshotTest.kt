package com.github.avbox.osc.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigRepositorySnapshotTest {

    @Test
    fun vodSwitchSnapshotCopiesBothLineLists() {
        val vodLines = mutableListOf("vod-a", "vod-b")
        val liveLines = mutableListOf("live-a")
        val snapshot = createVodSwitchSnapshot(
            vodUrl = "https://old.example/vod.json",
            liveUrl = "",
            vodLines = vodLines,
            vodLineSource = "https://old.example/repo.txt",
            liveLines = liveLines,
            liveLineSource = "https://old.example/live.m3u",
        )

        vodLines.clear()
        liveLines.add("live-b")

        assertEquals(listOf("vod-a", "vod-b"), snapshot.vodLines)
        assertEquals(listOf("live-a"), snapshot.liveLines)
        assertEquals("https://old.example/vod.json", snapshot.vodUrl)
        assertEquals("", snapshot.liveUrl)
        assertEquals("https://old.example/repo.txt", snapshot.vodLineSource)
        assertEquals("https://old.example/live.m3u", snapshot.liveLineSource)
    }
}
