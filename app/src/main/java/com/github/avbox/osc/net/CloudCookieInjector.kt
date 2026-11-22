package com.github.avbox.osc.net

import com.github.avbox.osc.util.security.CloudCredentialVault
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CloudCookieInjector {

    private data class Provider(
        val id: String,
        val aliases: Set<String>,
        val domains: Set<String>,
    )

    private val providers = listOf(
        Provider("quark", setOf("csp_PanQuark", "夸克网盘", "夸克"), setOf("pan.quark.cn", "drive.quark.cn", "api.quark.cn")),
        Provider("uc", setOf("csp_PanUC", "UC网盘", "UC"), setOf("drive.uc.cn", "pc-api.uc.cn", "api.open.uc.cn")),
        Provider("baidu", setOf("csp_PanBaidu", "百度网盘", "百度"), setOf("pan.baidu.com", "yun.baidu.com", "d.pcs.baidu.com")),
        Provider("xunlei", setOf("csp_PanXunlei", "迅雷云盘", "迅雷"), setOf("pan.xunlei.com", "api-pan.xunlei.com", "xluser-ssl.xunlei.com")),
        Provider("aliyun", setOf("csp_PanAli", "阿里云盘", "阿里"), setOf("www.alipan.com", "openapi.alipan.com", "api.aliyundrive.com", "auth.alipan.com")),
        Provider("guangya", setOf("csp_PanGuangya", "光鸭网盘", "光鸭"), setOf("api.guangyapan.com", "account.guangyapan.com")),
        Provider("tianyi", setOf("csp_PanTianyi", "天翼云盘", "天翼"), setOf("cloud.189.cn", "api.cloud.189.cn", "web.cloud.189.cn")),
        Provider("pan123", setOf("csp_Pan123", "123云盘", "123"), setOf("www.123pan.com", "api.123pan.com", "open-api.123pan.com", "yun.123pan.cn")),
    )

    fun apply(
        sourceKey: String?,
        api: String?,
        url: String?,
        headers: Map<String, String>,
    ): Map<String, String> {
        val provider = providerFor(sourceKey, api) ?: return headers
        if (!matchesProvider(sourceKey, api, url)) return headers
        val cookie = CloudCredentialVault.read(provider.id) ?: return headers
        return merge(headers, cookie)
    }

    fun applyForUrl(url: String, headers: Map<String, String>): Map<String, String> {
        val provider = providerForUrl(url) ?: return headers
        val cookie = CloudCredentialVault.read(provider.id) ?: return headers
        return merge(headers, cookie)
    }

    internal fun matchesProvider(sourceKey: String?, api: String?, url: String?): Boolean {
        val provider = providerFor(sourceKey, api) ?: return false
        val target = url?.toHttpUrlOrNull() ?: return false
        return target.isHttps && provider.domains.any { target.host == it || target.host.endsWith(".$it") }
    }

    internal fun matchesAnyProviderHost(url: String): Boolean = providerForUrl(url) != null

    private fun providerForUrl(url: String): Provider? {
        val target = url.toHttpUrlOrNull() ?: return null
        if (!target.isHttps) return null
        return providers.firstOrNull { provider ->
            provider.domains.any { target.host == it || target.host.endsWith(".$it") }
        }
    }

    private fun providerFor(sourceKey: String?, api: String?): Provider? =
        providers.firstOrNull { item ->
            item.aliases.any { alias ->
                alias.equals(sourceKey, ignoreCase = true) || alias.equals(api, ignoreCase = true)
            }
        }

    internal fun merge(headers: Map<String, String>, storedCookie: String): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        headers.entries.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            ?.let(::parseCookie)
            ?.forEach { (name, value) -> merged[name] = value }
        parseCookie(storedCookie).forEach { (name, value) -> merged[name] = value }
        if (merged.isEmpty()) return headers
        val result = LinkedHashMap<String, String>()
        headers.filterKeys { !it.equals("Cookie", ignoreCase = true) }.forEach { (key, value) ->
            result[key] = value
        }
        result["Cookie"] = merged.entries.joinToString("; ") { it.key + "=" + it.value }
        return result
    }

    private fun parseCookie(raw: String): Map<String, String> {
        val value = raw.trim().removePrefix("Cookie:").trim()
        val result = LinkedHashMap<String, String>()
        for (part in value.split(';')) {
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val name = part.substring(0, separator).trim()
            val cookieValue = part.substring(separator + 1).trim()
            if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) continue
            if (name.lowercase() in COOKIE_ATTRIBUTES || cookieValue.any { it == '\r' || it == '\n' }) continue
            result[name] = cookieValue
        }
        return result
    }

    private val COOKIE_ATTRIBUTES = setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite")
}
