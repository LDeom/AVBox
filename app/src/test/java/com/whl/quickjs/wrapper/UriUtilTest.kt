package com.whl.quickjs.wrapper

import org.junit.Assert.assertEquals
import org.junit.Test

class UriUtilTest {

    @Test
    fun emptyReferenceReturnsBaseWithoutFragment() {
        assertEquals(
            "https://example.com/modules/main.js?version=1",
            UriUtil.resolve(
                "https://example.com/modules/main.js?version=1#section",
                ""
            )
        )
    }

    @Test
    fun nullReferenceReturnsBaseWithoutFragment() {
        assertEquals(
            "https://example.com/modules/main.js",
            UriUtil.resolve("https://example.com/modules/main.js#section", null)
        )
    }

    @Test
    fun relativeModulePathStillResolvesAgainstBaseDirectory() {
        assertEquals(
            "https://example.com/modules/lib/helper.js",
            UriUtil.resolve(
                "https://example.com/modules/main.js",
                "./lib/helper.js"
            )
        )
    }
}
