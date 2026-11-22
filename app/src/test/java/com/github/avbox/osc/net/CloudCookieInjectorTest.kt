package com.github.avbox.osc.net

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudCookieInjectorTest {

    @Test
    fun storedCookieOverridesSameNameAndKeepsOtherRequestCookies() {
        val result = CloudCookieInjector.merge(
            mapOf("Accept" to "application/json", "Cookie" to "sid=old; theme=dark"),
            "sid=new; token=abc",
        )
        assertEquals("application/json", result["Accept"])
        assertEquals("sid=new; theme=dark; token=abc", result["Cookie"])
    }

    @Test
    fun ignoresSetCookieAttributesWhenPastingBrowserCookieText() {
        val result = CloudCookieInjector.merge(
            emptyMap(),
            "sid=abc; Path=/; Domain=.quark.cn; Secure; HttpOnly; SameSite=Lax",
        )
        assertEquals("sid=abc", result["Cookie"])
    }

    @Test
    fun invalidCookieDoesNotCreateHeader() {
        val result = CloudCookieInjector.merge(emptyMap(), "Path=/; Secure")
        assertEquals(emptyMap<String, String>(), result)
    }
}
