package com.github.avbox.osc.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadEpochTest {

    @Test
    fun runIfCurrentExecutesForCurrentSnapshot() {
        val epoch = LoadEpoch()
        var executed = false
        assertTrue(epoch.runIfCurrent(epoch.current()) { executed = true })
        assertTrue(executed)
    }

    @Test
    fun runIfCurrentRejectsStaleSnapshot() {
        val epoch = LoadEpoch()
        val snapshot = epoch.current()
        epoch.next()
        var executed = false
        assertFalse(epoch.runIfCurrent(snapshot) { executed = true })
        assertFalse(executed)
    }

    @Test
    fun snapshotOfCurrentEpochIsNotStale() {
        val epoch = LoadEpoch()
        val snapshot = epoch.current()
        assertFalse(epoch.isStale(snapshot))
    }

    @Test
    fun bumpingInvalidatesEarlierSnapshot() {
        val epoch = LoadEpoch()
        val snapshot = epoch.current()
        val bumped = epoch.next()
        assertTrue(epoch.isStale(snapshot))
        assertFalse(epoch.isStale(bumped))
    }

    @Test
    fun everyBumpProducesMonotonicEpoch() {
        val epoch = LoadEpoch()
        val first = epoch.next()
        val second = epoch.next()
        assertTrue(second > first)
        assertTrue(epoch.isStale(first))
        assertFalse(epoch.isStale(second))
    }

    @Test
    fun repeatedBumpsKeepOnlyLastSnapshotValid() {
        val epoch = LoadEpoch()
        val stale = epoch.current()
        repeat(3) { epoch.next() }
        assertTrue(epoch.isStale(stale))
        assertEquals(false, epoch.isStale(epoch.current()))
    }
}
