package com.github.avbox.osc.api

import com.github.avbox.osc.bean.ParseBean
import com.github.avbox.osc.net.HttpClients
import com.github.avbox.osc.util.AdBlocker
import com.github.avbox.osc.util.DefaultConfig
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.player.usecase.M3u8
import com.github.avbox.osc.util.VideoParseRuler
import com.google.gson.JsonObject

import java.util.ArrayList

object ConfigApplier {

    @JvmStatic
    fun applyHostRules(infoJson: JsonObject) {
        val rulesValue = infoJson.get("rules")
        if (rulesValue != null && rulesValue.isJsonArray) {
            VideoParseRuler.clearRule()
            for (oneHostRule in rulesValue.asJsonArray) {
                if (!oneHostRule.isJsonObject) continue
                val obj = oneHostRule.asJsonObject
                try {
                if (obj.has("host")) {
                    val host = obj.get("host").asString
                    if (obj.has("rule")) {
                        val ruleJsonArr = obj.getAsJsonArray("rule")
                        val rule = ArrayList<String>()
                        for (one in ruleJsonArr) {
                            val oneRule = one.asString
                            rule.add(oneRule)
                        }
                        if (rule.size > 0) {
                            VideoParseRuler.addHostRule(host, rule)
                        }
                    }
                    if (obj.has("filter")) {
                        val filterJsonArr = obj.getAsJsonArray("filter")
                        val filter = ArrayList<String>()
                        for (one in filterJsonArr) {
                            val oneFilter = one.asString
                            filter.add(oneFilter)
                        }
                        if (filter.size > 0) {
                            VideoParseRuler.addHostFilter(host, filter)
                        }
                    }
                }
                if (obj.has("hosts") && obj.has("regex")) {
                    val rule = ArrayList<String>()
                    val ads = ArrayList<String>()
                    val regexArray = obj.getAsJsonArray("regex")
                    for (one in regexArray) {
                        val regex = one.asString
                        if (M3u8.isAd(regex)) ads.add(regex) else rule.add(regex)
                    }
                    val array = obj.getAsJsonArray("hosts")
                    for (one in array) {
                        val host = one.asString
                        VideoParseRuler.addHostRule(host, rule)
                        VideoParseRuler.addHostRegex(host, ads)
                    }
                }
                if (obj.has("hosts") && obj.has("script")) {
                    val scripts = ArrayList<String>()
                    val scriptArray = obj.getAsJsonArray("script")
                    for (one in scriptArray) {
                        val script = one.asString
                        scripts.add(script)
                    }
                    val array = obj.getAsJsonArray("hosts")
                    for (one in array) {
                        val host = one.asString
                        VideoParseRuler.addHostScript(host, scripts)
                    }
                }
                if (obj.has("hosts") && obj.has("exclude")
                        && obj.get("hosts").isJsonArray && obj.get("exclude").isJsonArray) {
                    val excludes = ArrayList<String>()
                    for (one in obj.getAsJsonArray("exclude")) {
                        excludes.add(one.asString)
                    }
                    if (!excludes.isEmpty()) {
                        for (one in obj.getAsJsonArray("hosts")) {
                            VideoParseRuler.addHostExclude(one.asString, excludes)
                        }
                    }
                }
                } catch (th: Throwable) {
                    AppLog.e("ConfigApplier", th)
                }
            }
        }
    }

    @JvmStatic
    fun applyDoh(infoJson: JsonObject) {
        var dohJson = ""
        if (infoJson.has("doh")) {
            try {
                dohJson = infoJson.getAsJsonArray("doh").toString()
            } catch (e: Exception) {
                AppLog.e("ApiConfig", e)
            }
        }
        HttpClients.applyDohConfig(dohJson)
    }

    @JvmStatic
    fun applyAds(infoJson: JsonObject) {
        val ads = infoJson.get("ads")
        if (ads == null || !ads.isJsonArray) return
        for (host in ads.asJsonArray) {
            if (!host.isJsonPrimitive || !host.asJsonPrimitive.isString) continue
            val value = host.asString.trim { it <= ' ' }
            if (value.isNotEmpty() && !AdBlocker.hasHost(value)) {
                AdBlocker.addAdHost(value)
            }
        }
    }

    @JvmStatic
    fun parseParseBeans(infoJson: JsonObject): List<ParseBean> {
        val parseBeans: MutableList<ParseBean> = ArrayList()
        val parses = infoJson.get("parses")
        if (parses == null || !parses.isJsonArray) return parseBeans
        for (opt in parses.asJsonArray) {
            if (!opt.isJsonObject) continue
            try {
                val obj = opt.asJsonObject
                val nameValue = obj.get("name")
                val urlValue = obj.get("url")
                if (nameValue == null || !nameValue.isJsonPrimitive || !nameValue.asJsonPrimitive.isString
                    || urlValue == null || !urlValue.isJsonPrimitive || !urlValue.asJsonPrimitive.isString) continue
                val name = nameValue.asString.trim { it <= ' ' }
                val url = urlValue.asString.trim { it <= ' ' }
                if (name.isEmpty() || url.isEmpty()) continue
                val pb = ParseBean()
                pb.name = name
                pb.url = url
                val extValue = obj.get("ext")
                pb.ext = if (extValue != null && extValue.isJsonObject) extValue.asJsonObject.toString() else ""
                pb.type = DefaultConfig.safeJsonInt(obj, "type", 0)
                parseBeans.add(pb)
            } catch (th: Throwable) {
                AppLog.e("ConfigApplier", th)
            }
        }
        return parseBeans
    }

}
