package com.github.avbox.osc.api

import com.github.avbox.osc.bean.SourceBean
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSourcesStateTest {

    private fun source(key: String, searchable: Boolean = true): SourceBean =
        SourceBean().apply {
            this.key = key
            this.searchable = if (searchable) 1 else 0
        }

    @Test
    fun normalizeSearchableSources_dropsBlankKeysAndNonSearchableSources() {
        val result = normalizeSearchableSources(
            listOf(source("valid"), source(""), source("   "), source("disabled", false)),
        )

        assertEquals(listOf("valid"), result.map { it.key })
    }

    @Test
    fun normalizeSearchableSources_keepsOnlyFirstOccurrenceOfDuplicateKey() {
        val first = source("same")
        val duplicate = source("same").apply { name = "duplicate" }

        val result = normalizeSearchableSources(listOf(first, duplicate))

        assertEquals(1, result.size)
        assertEquals(first, result.single())
    }
}
