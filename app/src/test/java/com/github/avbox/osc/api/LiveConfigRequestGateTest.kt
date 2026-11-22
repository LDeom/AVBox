package com.github.avbox.osc.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveConfigRequestGateTest {

    @Test
    fun acceptsOnlyCurrentRequestForActiveUrl() {
        assertTrue(
            isCurrentLiveConfigRequest(
                requestId = 8L,
                activeRequestId = 8L,
                requestUrl = "https://live.example/new.json",
                activeUrl = "https://live.example/new.json",
            )
        )
    }

    @Test
    fun rejectsOlderRequestEvenWhenItsUrlWasPreviouslyActive() {
        assertFalse(
            isCurrentLiveConfigRequest(
                requestId = 7L,
                activeRequestId = 8L,
                requestUrl = "https://live.example/old.json",
                activeUrl = "https://live.example/new.json",
            )
        )
    }

    @Test
    fun rejectsRequestWhenActiveUrlChangesWithoutNewCallback() {
        assertFalse(
            isCurrentLiveConfigRequest(
                requestId = 8L,
                activeRequestId = 8L,
                requestUrl = "https://live.example/old.json",
                activeUrl = "https://live.example/new.json",
            )
        )
    }
}
