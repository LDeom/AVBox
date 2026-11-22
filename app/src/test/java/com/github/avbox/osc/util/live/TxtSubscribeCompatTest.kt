package com.github.avbox.osc.util.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtSubscribeCompatTest {
    @Test
    fun parseJsonGroups_skipsMalformedGroupsAndChannels() {
        val parsed = TxtSubscribe.parseToJsonArray(
            """[null,7,{"group":"A","channels":[null,7,{"name":"C","urls":["http://a","not-a-url",7,"http://a"]}]},{"group":"empty","channels":[null]}]""",
        )
        assertEquals(1, parsed.size())
        val group = parsed.get(0).asJsonObject
        assertEquals("A", group.get("group").asString)
        assertEquals(1, group.getAsJsonArray("channels").size())
        val channel = group.getAsJsonArray("channels").get(0).asJsonObject
        assertEquals("C", channel.get("name").asString)
        assertEquals(1, channel.getAsJsonArray("urls").size())
        assertEquals("http://a", channel.getAsJsonArray("urls").get(0).asString)
    }

    @Test
    fun parseJsonChannelAlias_acceptsLegacyChannelField() {
        val parsed = TxtSubscribe.parseToJsonArray(
            """[{"name":"Legacy","channel":[{"name":"C","urls":["http://a"]}]}]""",
        )
        assertEquals(1, parsed.size())
        assertEquals("Legacy", parsed.get(0).asJsonObject.get("group").asString)
        assertTrue(parsed.get(0).asJsonObject.getAsJsonArray("channels").size() > 0)
    }
    @Test
    fun parseMap_mergesUrlsForDuplicateChannelNames() {
        val parsed = linkedMapOf<String, java.util.LinkedHashMap<String, java.util.ArrayList<String>>>()
        TxtSubscribe.parse(
            parsed,
            """[{"group":"A","channels":[{"name":"C","urls":["http://a"]},{"name":"C","urls":["http://b","http://a"]}]}]""",
        )
        val urls = parsed["A"]?.get("C")
        assertEquals(listOf("http://a", "http://b"), urls)
    }

}
