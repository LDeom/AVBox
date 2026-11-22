package com.github.avbox.osc.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogRotationTest {

    @Test
    fun keepsWritingBelowThreshold() {
        assertFalse(AppLog.shouldRotateLog(0L))
        assertFalse(AppLog.shouldRotateLog(7L * 1024 * 1024))
        assertFalse(AppLog.shouldRotateLog(AppLog.FILE_LOG_MAX_BYTES - 1))
    }

    @Test
    fun rotatesAtThreshold() {
        assertTrue(AppLog.shouldRotateLog(AppLog.FILE_LOG_MAX_BYTES))
        assertTrue(AppLog.shouldRotateLog(AppLog.FILE_LOG_MAX_BYTES + 1024))
    }

    @Test
    fun sanitizesSensitiveLogFieldsBeforeExport() {
        val line = "request authorization=Bearer bearer-secret token=abc123 api_key:secret password=hunter2 keep=value"
        val sanitized = AppLog.sanitizeLogLine(line)
        assertTrue(sanitized.contains("authorization=[REDACTED]"))
        assertTrue(sanitized.contains("token=[REDACTED]"))
        assertTrue(sanitized.contains("api_key:[REDACTED]"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
        assertTrue(sanitized.contains("keep=value"))
        assertFalse(sanitized.contains("bearer-secret"))
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("hunter2"))
    }
}
