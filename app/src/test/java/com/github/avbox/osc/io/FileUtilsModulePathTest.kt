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
    @Test
    fun normalizesFileModuleUris() {
        assertEquals("modules/helper.js", FileUtils.fileModulePath("file:///modules/helper.js"))
        assertEquals("modules/helper.js", FileUtils.fileModulePath("file://modules/helper.js"))
    }

    @Test
    fun rejectsEmptyOrUnrelatedFileUris() {
        assertNull(FileUtils.fileModulePath("file://"))
        assertNull(FileUtils.fileModulePath("https://example.com/helper.js"))
    }

    @Test
    fun resolvesAssetsModulePathsWithoutChangingNestedOrUnicodeNames() {
        assertEquals("js/lib/helper.js", FileUtils.assetsModulePath("assets://js/lib/helper.js"))
        assertEquals("模块目录/工具.js", FileUtils.assetsModulePath("assets://模块目录/工具.js"))
    }

    @Test
    fun rejectsEmptyOrUnrelatedAssetsUris() {
        assertNull(FileUtils.assetsModulePath("assets://"))
        assertNull(FileUtils.assetsModulePath("file:///modules/helper.js"))
    }

    @Test
    fun preservesAlreadyPrefixedJsLibraryPaths() {
        assertEquals("js/lib/helper.js", FileUtils.jsLibAssetPath("js/lib/helper.js"))
        assertEquals("js/lib/helper.js", FileUtils.jsLibAssetPath("helper.js"))
        assertNull(FileUtils.jsLibAssetPath(""))
        assertNull(FileUtils.jsLibAssetPath("js/lib/"))
    }

}
