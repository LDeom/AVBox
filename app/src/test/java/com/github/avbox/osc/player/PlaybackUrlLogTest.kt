package com.github.avbox.osc.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUrlLogTest {

    @Test
    fun safeOmitsPathQueryAndFragment() {
        assertEquals(
            "https://cdn.example/[REDACTED]",
            PlaybackUrlLog.safe("https://cdn.example/video/movie.m3u8?token=secret&sign=private#fragment"),
        )
    }

    @Test
    fun safeDoesNotExposeUserInfo() {
        assertEquals(
            "https://cdn.example/[REDACTED]",
            PlaybackUrlLog.safe("https://user:password@cdn.example/video.mp4"),
        )
    }

    @Test
    fun invalidUrlUsesGenericMarker() {
        assertEquals("[URL]", PlaybackUrlLog.safe("not a URL"))
    }
}
