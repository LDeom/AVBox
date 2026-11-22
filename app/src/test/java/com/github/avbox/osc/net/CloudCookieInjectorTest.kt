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
    @Test
    fun onlyInjectsForMatchingProviderAndServiceDomain() {
        assertEquals(
            true,
            CloudCookieInjector.matchesProvider("夸克网盘", "csp_PanQuark", "https://drive.quark.cn/api"),
        )
        assertEquals(
            false,
            CloudCookieInjector.matchesProvider("夸克网盘", "csp_PanQuark", "https://pan.baidu.com/api"),
        )
        assertEquals(
            false,
            CloudCookieInjector.matchesProvider("普通点播源", "csp_PanQuark", "https://drive.quark.cn/api"),
        )
    }

    @Test
    fun hostLookupRequiresHttpsAndKnownProviderDomain() {
        assertEquals(true, CloudCookieInjector.matchesAnyProviderHost("https://drive.quark.cn/api"))
        assertEquals(false, CloudCookieInjector.matchesAnyProviderHost("http://drive.quark.cn/api"))
        assertEquals(false, CloudCookieInjector.matchesAnyProviderHost("https://example.com/api"))
        assertEquals(false, CloudCookieInjector.matchesAnyProviderHost("https://mail.quark.cn/api"))
    }

}
