package com.github.catvod.crawler.js

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsyncResultTest {

    @Test
    fun firstSuccessfulCompletionWins() {
        val result = Async.Result()
        result.complete("first")
        result.complete("second")
        result.completeExceptionally(IllegalStateException("late failure"))

        assertEquals("first", result.get())
    }

    @Test
    fun firstExceptionalCompletionWins() {
        val result = Async.Result()
        result.completeExceptionally(IllegalArgumentException("first failure"))
        result.complete("late value")
        result.completeExceptionally(IllegalStateException("late failure"))

        try {
            result.get()
            throw AssertionError("Expected the first failure")
        } catch (error: IllegalArgumentException) {
            assertEquals("first failure", error.message)
        }
    }

    @Test
    fun nullIsAValidCompletedValue() {
        val result = Async.Result()
        result.complete(null)
        result.complete("late value")

        assertNull(result.get())
    }
}
