package com.github.avbox.osc.sourcedata

import com.github.avbox.osc.bean.AbsSortXml
import com.github.avbox.osc.bean.AbsXml
import com.github.avbox.osc.ui.DetailResponseGuard
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceResultParserRoutingTest {

    private val gson = Gson()

    private class RecordingChannel : SourceChannel<AbsXml?>() {
        val posted = ArrayList<AbsXml?>()
        override fun postValue(value: AbsXml?) {
            posted.add(value)
        }
    }

    private val payload =
        """{"list":[{"vod_id":"1","vod_name":"测试片","vod_play_from":"线路甲","vod_play_url":"第1集${'$'}http://a.example/1.m3u8"}]}"""

    private fun newParser(search: SourceChannel<AbsXml?>, detail: SourceChannel<AbsXml?>) =
        SourceResultParser(gson, search, detail, PushDetailResolver(gson, detail))

    @Test
    fun sortJsonAcceptsTvBoxClassAndIdNameAliases() {
        val parser = newParser(RecordingChannel(), RecordingChannel())
        val parsed = parser.sortJson(null, """{"class":[{"type_id":"11","type_name":"电影"},{"id":"22","name":"电视剧"},{"type_id":"","type_name":"无效"}],"list":[]}""")
        val categories = parsed?.classes?.sortList.orEmpty()
        assertEquals(2, categories.size)
        assertEquals("11", categories[0].id)
        assertEquals("电影", categories[0].name)
        assertEquals("22", categories[1].id)
        assertEquals("电视剧", categories[1].name)
    }

    @Test
    fun emptySortJsonReturnsEmptyCategoriesRatherThanNull() {
        val parser = newParser(RecordingChannel(), RecordingChannel())
        val parsed = parser.sortJson(null, "{}")
        assertTrue(parsed != null)
        assertEquals(0, parsed?.classes?.sortList?.size ?: -1)
    }

    @Test
    fun searchChannelReceivesSearchResult() {
        val search = RecordingChannel()
        val detail = RecordingChannel()
        runBlocking { newParser(search, detail).json(search, payload, "src", "token") }
        assertEquals("搜索结果应统一经 searchResult 通道投递", 1, search.posted.size)
        assertTrue("载荷应为 AbsXml", search.posted[0] is AbsXml)
    }

    @Test
    fun detailChannelReceivesParsedDetail() {
        val search = RecordingChannel()
        val detail = RecordingChannel()
        runBlocking { newParser(search, detail).json(detail, payload, "src") }
        assertEquals(1, detail.posted.size)
        val data = detail.posted[0]!!
        val videos = data.movie!!.videoList!!
        assertEquals(1, videos.size)
        assertEquals("测试片", videos[0].name)
        assertEquals("src", data.sourceKey)
    }

    @Test
    fun plainChannelReceivesValue() {
        val search = RecordingChannel()
        val detail = RecordingChannel()
        val list = RecordingChannel()
        runBlocking { newParser(search, detail).json(list, payload, "src") }
        assertEquals(1, list.posted.size)
        assertEquals("src", list.posted[0]!!.sourceKey)
    }

    @Test
    fun detailTokenSurvivesFlowDelivery() = runBlocking {
        val search = SourceChannel<AbsXml?>()
        val detail = SourceChannel<AbsXml?>()
        newParser(search, detail).json(detail, payload, "src", "", 42)
        val data = withTimeout(1000) { detail.flow.first() }
        assertEquals(42, data?.detailToken)
        assertTrue("当前代次应被守卫采信", DetailResponseGuard.isCurrent(42, data?.detailToken))
        assertTrue("上一代必须被守卫丢弃", !DetailResponseGuard.isCurrent(41, data?.detailToken))
    }
}
