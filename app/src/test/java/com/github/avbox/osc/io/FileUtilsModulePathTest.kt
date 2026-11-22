package com.github.avbox.osc.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.whl.quickjs.wrapper.UriUtil
import com.github.catvod.crawler.JarLoader
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

    @Test
    fun resolvesNestedRelativeModulePaths() {
        assertEquals(
            "https://example.com/js/lib/helper.js",
            UriUtil.resolve("https://example.com/js/api/main.js", "../lib/helper.js")
        )
        assertEquals(
            "https://example.com/js/api/sub/child.js",
            UriUtil.resolve("https://example.com/js/api/main.js", "./sub/child.js")
        )
    }

    @Test
    fun resolvesParentSegmentsAcrossMultipleLevels() {
        assertEquals(
            "https://example.com/shared/util.js",
            UriUtil.resolve("https://example.com/js/api/deep/main.js", "../../../shared/util.js")
        )
    }

    @Test
    fun resolvesRootRelativeAndQueryOrFragmentReferences() {
        assertEquals(
            "https://example.com/shared.js",
            UriUtil.resolve("https://example.com/js/api/main.js", "/shared.js")
        )
        assertEquals(
            "https://example.com/js/api/main.js?v=2",
            UriUtil.resolve("https://example.com/js/api/main.js?old=1", "?v=2")
        )
        assertEquals(
            "https://example.com/js/api/main.js#section",
            UriUtil.resolve("https://example.com/js/api/main.js?old=1#old", "#section")
        )
    }

    @Test
    fun emptyReferenceKeepsQueryAndDropsFragment() {
        assertEquals(
            "https://example.com/js/api/main.js?token=1",
            UriUtil.resolve("https://example.com/js/api/main.js?token=1#section", "")
        )
        assertEquals(
            "https://example.com/js/api/main.js?token=1",
            UriUtil.resolve("https://example.com/js/api/main.js?token=1#section", null)
        )
    }

    @Test
    fun normalizesAssetJarPathsWithoutReplacingNestedSegments() {
        val loader = JarLoader()
        assertEquals("lib/source.jar", loader.assetJarPath("assets://lib/source.jar"))
        assertEquals("lib/source.jar", loader.assetJarPath("assets/lib/source.jar"))
        assertEquals("nested/assets/source.jar", loader.assetJarPath("assets://nested/assets/source.jar"))
        assertNull(loader.assetJarPath("assets://"))
        assertNull(loader.assetJarPath("assets://../outside.jar"))
        assertNull(loader.assetJarPath("assets-other/source.jar"))
    }

}
