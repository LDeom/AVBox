package com.github.avbox.osc.net

import com.github.avbox.osc.util.security.CloudCredentialVault
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

object CloudCookieInjector {

    private val providers = CloudDriveProviderCatalog.providers

    fun apply(
        sourceKey: String?,
        api: String?,
        url: String?,
        headers: Map<String, String>,
    ): Map<String, String> {
        val provider = providerFor(sourceKey, api) ?: return headers
        val target = url?.toHttpUrlOrNull() ?: return headers
        if (!target.isHttps || target.host !in provider.cookieHosts) return headers
        return applyProviderCookie(provider.id, target, headers)
    }

    fun applyForUrl(url: String, headers: Map<String, String>): Map<String, String> {
        val target = url.toHttpUrlOrNull() ?: return headers
        if (!target.isHttps) return headers
        val provider = providers.firstOrNull { target.host in it.cookieHosts } ?: return headers
        return applyProviderCookie(provider.id, target, headers)
    }

    internal fun matchesProvider(sourceKey: String?, api: String?, url: String?): Boolean {
        val provider = providerFor(sourceKey, api) ?: return false
        val target = url?.toHttpUrlOrNull() ?: return false
        return target.isHttps && target.host in provider.cookieHosts
    }

    internal fun matchesAnyProviderHost(url: String): Boolean {
        val target = url.toHttpUrlOrNull() ?: return false
        return target.isHttps && providers.any { target.host in it.cookieHosts }
    }

    private fun applyProviderCookie(providerId: String, target: HttpUrl, headers: Map<String, String>): Map<String, String> {
        val stored = CloudCredentialVault.read(providerId) ?: return headers
        val cookie = cookieForHost(stored, target.host) ?: return headers
        return merge(headers, cookie)
    }

    private fun cookieForHost(stored: String, host: String): String? {
        if (!stored.trimStart().startsWith("{")) return stored
        return runCatching { JSONObject(stored).optString(host).takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun providerFor(sourceKey: String?, api: String?): CloudDriveProvider? =
        providers.firstOrNull { item ->
            val aliases = when (item.id) {
                "quark" -> setOf("csp_PanQuark", "夸克网盘", "夸克")
                "uc" -> setOf("csp_PanUC", "UC网盘", "UC")
                "baidu" -> setOf("csp_PanBaidu", "百度网盘", "百度")
                "xunlei" -> setOf("csp_PanXunlei", "迅雷云盘", "迅雷")
                "aliyun" -> setOf("csp_PanAli", "阿里云盘", "阿里")
                "guangya" -> setOf("csp_PanGuangya", "光鸭网盘", "光鸭")
                "tianyi" -> setOf("csp_PanTianyi", "天翼云盘", "天翼")
                else -> setOf("csp_Pan123", "123云盘", "123")
            }
            aliases.any { it.equals(sourceKey, ignoreCase = true) || it.equals(api, ignoreCase = true) }
        }

    internal fun merge(headers: Map<String, String>, storedCookie: String): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        headers.entries.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value?.let(::parseCookie)?.forEach { (name, value) -> merged[name] = value }
        parseCookie(storedCookie).forEach { (name, value) -> merged[name] = value }
        if (merged.isEmpty()) return headers
        val result = LinkedHashMap<String, String>()
        headers.filterKeys { !it.equals("Cookie", ignoreCase = true) }.forEach { (key, value) -> result[key] = value }
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
