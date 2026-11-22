package com.github.avbox.osc.player

import android.net.Uri

object PlaybackUrlLog {

    @JvmStatic
    fun safe(url: String?): String {
        if (url.isNullOrBlank()) return "[empty]"
        return runCatching {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: return@runCatching "[URL]"
            val host = uri.host ?: return@runCatching "[URL]"
            "$scheme://$host/[REDACTED]"
        }.getOrDefault("[URL]")
    }
}
