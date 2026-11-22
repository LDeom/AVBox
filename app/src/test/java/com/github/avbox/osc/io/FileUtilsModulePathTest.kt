package com.github.avbox.osc.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileUtilsModulePathTest {

    @Test
    fun resolvesClanHostAndModulePath() {
        assertEquals(
            "http://192.168.1.8:9978/file/js/lib/helper.js",
            FileUtils.clanModuleUrl("clan://192.168.1.8:9978/js/lib/helper.js")
        )
    }

    @Test
    fun rejectsClanUrlWithoutHostOrPathSeparator() {
        assertNull(FileUtils.clanModuleUrl("clan://"))
        assertNull(FileUtils.clanModuleUrl("clan://192.168.1.8:9978"))
    }

    @Test
    fun allowsEmptyClanModulePath() {
        assertEquals(
            "http://192.168.1.8:9978/file/",
            FileUtils.clanModuleUrl("clan://192.168.1.8:9978/")
        )
    }
}
