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
    fun operationIdsIncreaseMonotonically() {
        val first = AppLog.newOperationId()
        val second = AppLog.newOperationId()
        assertTrue(second > first)
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
    @Test
    fun sanitizesWholeCookieHeaderAndAllCookiePairs() {
        val sanitized = AppLog.sanitizeLogLine(
            """request Cookie: sid=first-secret; refresh=second-secret, host=pan.example""",
        )
        assertTrue(sanitized.contains("Cookie: [REDACTED]"))
        assertFalse(sanitized.contains("first-secret"))
        assertFalse(sanitized.contains("second-secret"))
    }

    @Test
    fun removesSignedAndNestedCredentialUrls() {
        val sanitized = AppLog.sanitizeLogLine(
            """request=https://127.0.0.1:5266?url=https%3A%2F%2Fcdn.example%2Fv.mp4%3Fauth_key%3Dsecret&header=%7B%22Cookie%22%3A%22sid%3Dsecret%22%7D""",
        )
        assertTrue(sanitized.contains("https://127.0.0.1:5266?[REDACTED]"))
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("%22Cookie%22"))
    }

}
