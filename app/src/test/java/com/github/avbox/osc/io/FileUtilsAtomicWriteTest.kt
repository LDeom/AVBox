package com.github.avbox.osc.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileUtilsAtomicWriteTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writesAndReplacesFileContent() {
        val target = File(folder.root, "cache.bin")
        target.writeText("old")

        assertTrue(FileUtils.writeSimple("new content".toByteArray(), target))
        assertEquals("new content", target.readText())
        assertTrue(File(target.absolutePath + ".tmp").let { !it.exists() })
        assertTrue(File(target.absolutePath + ".bak").let { !it.exists() })
    }

    @Test
    fun writesFileWhenDestinationDoesNotExist() {
        val target = File(folder.root, "nested/cache.bin")

        assertTrue(FileUtils.writeSimple(byteArrayOf(1, 2, 3), target))
        assertEquals(3L, target.length())
    }

    @Test
    fun saveCacheReplacesExistingFileWithoutLeavingTemporaryFiles() {
        val target = File(folder.root, "cache.json")
        target.writeText("old cache")

        FileUtils.saveCache(target, """{"data":"new cache"}""")

        assertEquals("""{"data":"new cache"}""", target.readText())
        assertTrue(!File(target.absolutePath + ".tmp").exists())
        assertTrue(!File(target.absolutePath + ".bak").exists())
    }

    @Test
    fun cacheExpiryUsesInclusiveBoundary() {
        assertTrue(FileUtils.isCacheExpired(100L, 100L))
        assertTrue(FileUtils.isCacheExpired(99L, 100L))
        assertTrue(!FileUtils.isCacheExpired(101L, 100L))
    }

    @Test
    fun readSimpleReturnsCompleteFileContent() {
        val target = File(folder.root, "module.js")
        val expected = ByteArray(128 * 1024) { (it % 251).toByte() }
        target.writeBytes(expected)

        val actual = FileUtils.readSimple(target)

        assertTrue(expected.contentEquals(actual))
    }
}
