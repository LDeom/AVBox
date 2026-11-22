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
}
