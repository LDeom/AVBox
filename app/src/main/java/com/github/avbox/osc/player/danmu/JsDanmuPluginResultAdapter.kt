package com.github.avbox.osc.player.danmu

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Converts the documented plugin record shape to the XML subset consumed by Danmu.fromXml.
 * Invalid records are skipped; limits are enforced before building the XML string.
 */
object JsDanmuPluginResultAdapter {
    private const val MAX_TEXT_LENGTH = 500
    private const val MAX_XML_CHARS = 2 * 1024 * 1024

    fun toDanmakuXml(items: JSONArray): String {
        require(items.length() <= JsDanmuPluginRuntime.MAX_RESULT_ITEMS) { "弹幕数量超过限制" }
        val xml = StringBuilder("<i>")
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val time = item.optDouble("time", Double.NaN)
            if (!time.isFinite() || time < 0.0 || time > 86_400.0) continue
            val text = item.optString("text").trim()
            if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) continue
            val mode = when (item.optString("mode", "scroll").lowercase(Locale.ROOT)) {
                "scroll", "right-to-left", "rtl" -> 1
                "bottom", "fixed-bottom" -> 4
                "top", "fixed-top" -> 5
                else -> continue
            }
            val color = item.optLong("color", 0xFFFFFFL)
            if (color !in 0L..0xFFFFFFL) continue
            val size = item.optDouble("size", 25.0)
            if (!size.isFinite() || size !in 8.0..64.0) continue
            val timestamp = System.currentTimeMillis() / 1000L
            val param = String.format(
                Locale.US, "%.3f,%d,%.1f,%d,%d,0,0,0",
                time, mode, size, color, timestamp
            )
            xml.append("<d p=\"").append(param).append("\">")
                .append(escapeXml(text)).append("</d>")
            if (xml.length > MAX_XML_CHARS) throw IllegalArgumentException("弹幕转换结果超过 2 MB")
        }
        return xml.append("</i>").toString()
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch == '\u0000' || (ch.code in 0x01..0x08) || ch.code in 0x0B..0x0C ||
                    ch.code in 0x0E..0x1F) {
                    // Drop XML 1.0 forbidden control characters.
                } else append(ch)
            }
        }
    }
}