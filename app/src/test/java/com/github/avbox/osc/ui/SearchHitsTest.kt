package com.github.avbox.osc.ui

import com.github.avbox.osc.bean.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHitsTest {

    private fun video(id: String): Movie.Video {
        val item = Movie.Video()
        item.id = id
        item.name = id
        return item
    }

    private fun result(key: String, arrivedAt: Int, hits: Int): SearchViewModel.SourceResult =
        SearchViewModel.SourceResult(
            sourceKey = key,
            sourceName = key,
            state = SearchViewModel.ResultState.Done,
            videos = (1..hits).map { video("$key-$it") },
            arrivedAt = arrivedAt,
        )

    @Test
    fun sources_keepsOnlySourcesWithHits() {
        val sources = SearchHits.sources(listOf(result("a", 1, 2), result("b", 2, 0), result("c", 3, 1)))
        assertEquals(listOf("a", "c"), sources.map { it.sourceKey })
    }

    @Test
    fun sources_ordersByArrival() {
        val sources = SearchHits.sources(listOf(result("a", 3, 1), result("b", 1, 1), result("c", 2, 1)))
        assertEquals(listOf("b", "c", "a"), sources.map { it.sourceKey })
    }

    @Test
    fun sources_dropsPendingSourcesWithoutHits() {
        val pending = SearchViewModel.SourceResult(
            sourceKey = "d",
            sourceName = "d",
            state = SearchViewModel.ResultState.Pending,
            videos = emptyList(),
        )
        assertTrue(SearchHits.sources(listOf(result("a", 1, 0), pending)).isEmpty())
    }

    @Test
    fun sources_handlesEmptyInput() {
        assertTrue(SearchHits.sources(emptyList()).isEmpty())
    }

    @Test
    fun sources_keepsSameTitleFromDifferentSources() {
        val first = result("source-a", 1, 1)
        val second = result("source-b", 2, 1)
        first.videos.first().name = "同一部影片"
        second.videos.first().name = "同一部影片"

        val hits = SearchHits.sources(listOf(first, second))

        assertEquals(2, hits.size)
        assertEquals(listOf("source-a", "source-b"), hits.map { it.sourceKey })
    }
}
