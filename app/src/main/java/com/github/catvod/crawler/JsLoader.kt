package com.github.catvod.crawler

import android.util.Log

import com.github.catvod.crawler.js.JsSpider
import com.github.avbox.osc.io.FileUtils
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.MD5
import com.github.avbox.osc.util.RegexUtils
import com.github.avbox.osc.util.net.Http

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

import dalvik.system.DexClassLoader
import com.whl.quickjs.wrapper.QuickJSContext

class JsLoader {

    @Volatile
    private var recentKey: String = ""

    private val clearGeneration: AtomicLong = AtomicLong(0)
    private val creationLock: Any = Any()
    private val lifecycleLock: Any = Any()
    private val destroyExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "js-loader-destroy") }

    fun clear() {
        val stale = synchronized(lifecycleLock) {
            clearGeneration.incrementAndGet()
            val current = ArrayList(spiders.values)
            spiders.clear()
            classes.clear()
            recentKey = ""
            current
        }
        for (spider in stale) {
            try {
                spider.cancelByTag()
            } catch (ignored: Throwable) {
                AppLog.d("JsLoader", "cancel spider failed")
            }
            SpiderReaper.retire(spider)
        }
    }

    private fun loadClassLoader(jar: String, key: String, generation: Long): Boolean {
        if (generation != clearGeneration.get()) return false
        var success = false
        var classInit: Class<*>? = null
        try {
            val cacheDir = File(requireNotNull(AppContextHolder.context()) { "app context not ready" }.cacheDir.absolutePath + "/catvod_jsapi")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val classLoader = DexClassLoader(jar, cacheDir.absolutePath, null, requireNotNull(AppContextHolder.context()) { "app context not ready" }.classLoader)
            var count = 0
            do {
                try {
                    try {
                        val clz = classLoader.loadClass("com.github.catvod.js.Function")
                        classInit = clz
                        clz.getDeclaredConstructor(QuickJSContext::class.java)
                        Log.i("JSLoader", "echo-load_com.github.catvod.js.Function")
                    } catch (ignored: Throwable) {
                        val clz = classLoader.loadClass("com.github.catvod.js.Method")
                        classInit = clz
                        clz.getDeclaredConstructor(QuickJSContext::class.java)
                        Log.i("JSLoader", "echo-load_com.github.catvod.js.Method")
                    }
                    if (classInit != null) {
                        Log.i("JSLoader", "echo-自定义jsapi代码加载成功!")
                        success = true
                        break
                    }
                    Thread.sleep(200)
                } catch (th: Throwable) {
                    AppLog.e("JsLoader", th)
                    if (th is InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                count++
            } while (count < 5)

            if (success) {
                synchronized(lifecycleLock) {
                    if (generation != clearGeneration.get()) return false
                    classes[key] = requireNotNull(classInit) { "class init is null" }
                }
            }
        } catch (th: Throwable) {
            AppLog.e("JsLoader", th)
        }
        return success
    }

    private fun loadJarInternal(jar: String, md5: String, key: String, generation: Long): Class<*>? {
        if (generation != clearGeneration.get()) return null
        if (classes.containsKey(key)) {
            Log.i("JSLoader", "echo-loadJarInternal cached")
            return classes[key]
        }
        val cache = File(requireNotNull(AppContextHolder.context()) { "app context not ready" }.filesDir.absolutePath + "/csp/" + key + ".jar")
        try {
            val parent = cache.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
        } catch (ignored: Throwable) {
            AppLog.d("JsLoader", "create csp dir failed")
        }
        if (!md5.isEmpty()) {
            if (cache.exists() && MD5.fileMd5(cache).equals(md5, ignoreCase = true)) {
                loadClassLoader(cache.absolutePath, key, generation)
                return classes[key]
            }
        } else {
            if (cache.exists() && !FileUtils.isWeekAgo(cache)) {
                if (loadClassLoader(cache.absolutePath, key, generation)) {
                    return classes[key]
                }
            }
        }
        val temp = File(cache.absolutePath + ".tmp")
        try {
            if (temp.exists()) temp.delete()
            Http.getSync(jar).use { response ->
                if (!response.isSuccessful) throw IllegalStateException("JS API Jar HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("JS API Jar body is empty")
                body.byteStream().use { inputStream ->
                    FileOutputStream(temp).use { outputStream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) {
                                throw InterruptedException("JS API Jar download interrupted")
                            }
                            val length = inputStream.read(buffer)
                            if (length < 0) break
                            outputStream.write(buffer, 0, length)
                        }
                        outputStream.flush()
                    }
                }
            }
            if (temp.length() <= 0L) throw IllegalStateException("JS API Jar download is empty")
            if (!md5.isEmpty() && !MD5.fileMd5(temp).equals(md5, ignoreCase = true)) {
                throw IllegalStateException("JS API Jar MD5 mismatch")
            }
            val downloaded = FileUtils.readSimple(temp)
                    ?: throw IllegalStateException("Cannot read downloaded JS API Jar")
            if (!FileUtils.writeSimple(downloaded, cache)) {
                throw IllegalStateException("Cannot safely replace JS API Jar cache")
            }
            temp.delete()
            loadClassLoader(cache.absolutePath, key, generation)
            return classes[key]
        } catch (e: Throwable) {
            AppLog.e("JsLoader", e)
            if (e is InterruptedException) Thread.currentThread().interrupt()
            if (temp.exists()) temp.delete()
        }
        return null
    }

    fun getSpider(key: String?, api: String?, ext: String?, jar: String?): Spider {
        recentKey = requireNotNull(key) { "site key is null" }
        spiders[key]?.let {
            Log.i("JSLoader", "echo-getSpider cached " + key)
            return it
        }
        val generation = clearGeneration.get()
        synchronized(creationLock) {
            val cached = spiders[key]
            if (cached != null) {
                Log.i("JSLoader", "echo-getSpider cached " + key)
                return cached
            }
            var classLoader: Class<*>? = null
            if (!jar.isNullOrEmpty()) {
                val urls = RegexUtils.getPattern(";md5;").split(jar)
                val jarUrl = urls[0]
                val jarKey = requireNotNull(MD5.string2MD5(jarUrl)) { "jar md5 is null" }
                val jarMd5 = if (urls.size > 1) urls[1].trim { it <= ' ' } else ""
                classLoader = loadJarInternal(jarUrl, jarMd5, jarKey, generation)
            }
            var sp: Spider? = null
            try {
                Log.i("JSLoader", "echo-getSpider load")
                val created = JsSpider(key, requireNotNull(api) { "js api is null" }, classLoader)
                sp = created
                created.siteKey = key
                created.init(AppContextHolder.context(), ext)
                val registered = synchronized(lifecycleLock) {
                    if (generation != clearGeneration.get()) {
                        false
                    } else {
                        spiders[key] = created
                        true
                    }
                }
                if (registered) return created
                destroyLater(created)
                return SpiderNull()
            } catch (th: Throwable) {
                AppLog.i("echo-getSpider-error " + th.message)
                sp?.let { destroyLater(it) }
            }
            return SpiderNull()
        }
    }

    private fun destroyLater(spider: Spider) {
        destroyExecutor.execute {
            try {
                spider.cancelByTag()
                spider.destroy()
            } catch (ignored: Throwable) {
                AppLog.d("JsLoader", "destroy spider failed")
            }
        }
    }

    fun proxyInvoke(params: Map<String, String>?): Array<Any?>? {
        try {
            val proxyFun = spiders[recentKey]
            if (proxyFun != null) {
                return SpiderReaper.track(proxyFun) { proxyFun.proxyLocal(params) }
            }
        } catch (th: Throwable) {
            AppLog.e("JsLoader", "proxy invoke failed", th)
        }
        return null
    }

    companion object {

        private val spiders = ConcurrentHashMap<String, Spider>()
        private val classes = ConcurrentHashMap<String, Class<*>>()

        @JvmStatic
        fun destroy() {
            for (spider in spiders.values) {
                spider.cancelByTag()
                spider.destroy()
            }
        }

        @JvmStatic
        fun stopAll() {
            for (spider in spiders.values) {
                spider.cancelByTag()
            }
        }
    }
}
