package com.github.avbox.osc.net

import okhttp3.Interceptor
import okhttp3.Response

object CloudCookieNetworkInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = LinkedHashMap<String, String>()
        for (name in request.headers.names()) {
            val values = request.headers.values(name)
            headers[name] = if (name.equals("Cookie", ignoreCase = true)) {
                values.joinToString("; ")
            } else {
                values.firstOrNull().orEmpty()
            }
        }
        val cookie = CloudCookieInjector.applyForUrl(request.url.toString(), headers)
            .entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
        val outgoing = if (cookie.isNullOrBlank() || cookie == request.header("Cookie")) {
            request
        } else {
            request.newBuilder().header("Cookie", cookie).build()
        }
        return chain.proceed(outgoing)
    }
}
