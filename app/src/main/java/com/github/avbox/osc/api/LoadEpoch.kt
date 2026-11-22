package com.github.avbox.osc.api

import java.util.concurrent.atomic.AtomicLong

internal class LoadEpoch {

    private val latest = AtomicLong(0)
    private val lock = Any()

    fun next(): Long = synchronized(lock) { latest.incrementAndGet() }

    fun current(): Long = latest.get()

    fun isStale(snapshot: Long): Boolean = snapshot != latest.get()

    fun runIfCurrent(snapshot: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (snapshot != latest.get()) return@synchronized false
        action()
        true
    }
}
