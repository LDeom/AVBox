package com.github.catvod.crawler

import android.content.Context
import android.os.Environment
import android.text.TextUtils
import android.util.Log

import com.github.catvod.Proxy
import com.github.catvod.net.OkHttp
import com.github.avbox.osc.io.FileUtils
import com.github.avbox.osc.server.ControlManager
import com.github.avbox.osc.server.RemoteServer
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.boot.BootGuard
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.MD5
import com.github.avbox.osc.util.RegexUtils
import com.github.avbox.osc.util.SpiderInitWatch
import com.github.avbox.osc.util.net.Http

import org.json.JSONObject

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import dalvik.system.DexClassLoader

class JarLoader {

    private val loaders = ConcurrentHashMap<String, DexClassLoader>()
    private val proxyMethods = ConcurrentHashMap<String, Method>()
    private val danmuClickMethods = ConcurrentHashMap<String, Method>()
    private val danmuLongClickMethods = ConcurrentHashMap<String, Method>()
    private val spiders = ConcurrentHashMap<String, Spider>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val siteJarKeys = ConcurrentHashMap<String, String>()
    private val aliases = ConcurrentHashMap<String, String>()
    private val liveJars = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var recent: String = MAIN_KEY

    private val clearGeneration: AtomicLong = AtomicLong(0)
    private val lifecycleLock: Any = Any()

    fun load(cache: String): Boolean {
        val generation = clearGeneration.get()
        val success = load(MAIN_KEY, File(cache), generation)
        if (success) {
            synchronized(lifecycleLock) {
                if (generation == clearGeneration.get() && loaders.containsKey(MAIN_KEY)) {
                    recent = MAIN_KEY
                }
            }
        }
        return success
    }

    fun setRecentJarKey(key: String?) {
        if (key.isNullOrEmpty()) return
        val loader = synchronized(lifecycleLock) {
            recent = realKey(key)
            loaders[recent]
        }
        injectProxyPort(loader)
    }

    fun loadLiveJar(jar: String?) {
        if (jar.isNullOrEmpty()) return
        val key = jarKey(jar)
        val generation = clearGeneration.get()
        synchronized(lifecycleLock) {
            if (generation != clearGeneration.get()) return
            liveJars.add(key)
        }
        parseJar(key, jar, generation)
        val loader = synchronized(lifecycleLock) {
            if (generation != clearGeneration.get()) return
            recent = realKey(key)
            loaders[recent]
        }
        injectProxyPort(loader)
    }

    fun clear() {
        val stale = synchronized(lifecycleLock) {
            clearGeneration.incrementAndGet()
            val current = ArrayList(spiders.values)
            loaders.clear()
            proxyMethods.clear()
            danmuClickMethods.clear()
            danmuLongClickMethods.clear()
            spiders.clear()
            siteJarKeys.clear()
            aliases.clear()
            liveJars.clear()
            recent = MAIN_KEY
            current
        }
        for (spider in stale) {
            SpiderReaper.retire(spider)
        }
    }

    private fun load(key: String, file: File, generation: Long = clearGeneration.get()): Boolean {
        if (Thread.interrupted()) return false
        if (!exists(file) || generation != clearGeneration.get()) return false
        if (loaders.containsKey(key)) return generation == clearGeneration.get()
        if (BootGuard.isJarDisabled(file.absolutePath)) {
            AppLog.i("echo-boot-guard: blocked jar key=" + key + " file=" + file.absolutePath)
            return false
        }
        if (key != MAIN_KEY) {
            val scope = if (liveJars.contains(key)) BootGuard.SCOPE_LIVE else BootGuard.SCOPE_VOD
            BootGuard.onJarLoadStart(file.absolutePath, scope)
        }
        try {
            file.setReadOnly()
            val cachePath = jarDir().absolutePath
            val loader = DexClassLoader(file.absolutePath, cachePath, cachePath, requireNotNull(AppContextHolder.context()) { "app context not ready" }.classLoader)
            invokeInit(loader)
            val proxyMethod = findProxyMethod(loader)
            val danmakuMethods = findDanmakuMethods(loader)
            injectProxyPort(loader)
            synchronized(lifecycleLock) {
                if (generation != clearGeneration.get()) return false
                if (proxyMethod != null) proxyMethods[key] = proxyMethod
                if (danmakuMethods.first != null) danmuClickMethods[key] = danmakuMethods.first
                if (danmakuMethods.second != null) danmuLongClickMethods[key] = danmakuMethods.second
                loaders[key] = loader
            }
            BootGuard.scheduleStableRunReset()
            AppLog.i("echo--jar-load success key=" + key + ", file=" + file.absolutePath)
            return true
        } catch (e: Throwable) {
            AppLog.i("echo--jar-load error key=" + key + ", msg=" + e.javaClass.simpleName + ":" + e.message)
            AppLog.e("JarLoader", e)
            return false
        }
    }

    private fun invokeInit(loader: DexClassLoader) {
        try {
            val clz = loader.loadClass("com.github.catvod.spider.Init")
            val method = clz.getMethod("init", Context::class.java)
            method.invoke(null, AppContextHolder.context())
        } catch (e: Throwable) {
            AppLog.e("JarLoader", e)
        }
    }

    private fun findProxyMethod(loader: DexClassLoader): Method? {
        return try {
            val clz = loader.loadClass("com.github.catvod.spider.Proxy")
            clz.getMethod("proxy", Map::class.java)
        } catch (e: Throwable) {
            AppLog.e("echo-proxy-jar: register fail | " + e)
            null
        }
    }

    private fun findDanmakuMethods(loader: DexClassLoader): Pair<Method?, Method?> {
        return try {
            val clz = loader.loadClass("com.github.catvod.spider.Danmaku")
            val click = try {
                clz.getMethod("onClick", String::class.java, String::class.java)
            } catch (ignored: Throwable) {
                AppLog.d("JarLoader", "danmaku onClick method not found")
                null
            }
            val longClick = try {
                clz.getMethod("onLongClick", String::class.java, String::class.java)
            } catch (ignored: Throwable) {
                AppLog.d("JarLoader", "danmaku onLongClick method not found")
                null
            }
            Pair(click, longClick)
        } catch (ignored: Throwable) {
            AppLog.d("JarLoader", "danmaku class not found in jar")
            Pair(null, null)
        }
    }

    fun parseJar(key: String?, jar: String?) {
        parseJar(key, jar, clearGeneration.get())
    }

    private fun parseJar(key: String?, jar: String?, generation: Long) {
        if (key.isNullOrEmpty() || jar.isNullOrEmpty()) return
        if (generation != clearGeneration.get() || loaders.containsKey(key)) return
        val texts = RegexUtils.getPattern(";md5;").split(jar)
        val source = texts[0]
        var md5 = if (texts.size > 1) texts[1].trim { it <= ' ' } else ""
        val lock = lock("jar-cache:" + jarKey(source))
        synchronized(lock) {
            if (generation != clearGeneration.get() || loaders.containsKey(key)) return
            synchronized(lifecycleLock) {
                if (generation != clearGeneration.get()) return
                aliases[jarKey(source)] = key
            }
            if (md5.startsWith("http")) {
                val value = OkHttp.string(md5, null)
                md5 = value?.trim { it <= ' ' } ?: ""
            }
            val file = fileForJar(source)
            if (!TextUtils.isEmpty(md5) && exists(file) && MD5.fileMd5(file).equals(md5, ignoreCase = true)) {
                load(key, file, generation)
            } else if (TextUtils.isEmpty(md5) && exists(file) && !FileUtils.isWeekAgo(file)) {
                load(key, file, generation)
            } else if (source.startsWith("http")) {
                download(source, file, generation)?.let { load(key, it, generation) }
            } else if (source.startsWith("assets")) {
                copyAsset(source, file, generation)?.let { load(key, it, generation) }
            } else if (source.startsWith("file")) {
                load(key, local(source), generation)
            } else if (source.startsWith("clan://")) {
                download(clanToAddress(source), file, generation)?.let { load(key, it, generation) }
            }
        }
    }

    fun dex(jar: String?): DexClassLoader? {
        try {
            val key = jarKey(jar)
            parseJar(key, jar)
            return loaders[key]
        } catch (e: Throwable) {
            AppLog.e("JarLoader", e)
            return null
        }
    }

    fun getSpider(key: String?, api: String?, ext: String?, jar: String?): Spider {
        val spiderKey = key ?: ""
        val spiderApi = api ?: ""
        val spiderExt = ext ?: ""
        val spiderJar = jar ?: ""
        if (TextUtils.isEmpty(spiderApi)) return SpiderNull()

        val jaKey = if (TextUtils.isEmpty(spiderJar)) MAIN_KEY else jarKey(spiderJar)
        val spKey = jaKey + spiderKey
        val snapshot = synchronized(lifecycleLock) {
            recent = jaKey
            siteJarKeys[spiderKey] = jaKey
            clearGeneration.get() to spiders[spKey]
        }
        injectProxyPort(loaders[jaKey])

        val cached = snapshot.second
        if (cached != null) {
            Log.i(TAG, "getSpider cached key=" + spKey)
            return cached
        }

        val lock = locks.computeIfAbsent(spKey) { Any() }
        val generation = snapshot.first
        synchronized(lock) {
            val cachedAgain = synchronized(lifecycleLock) {
                if (generation == clearGeneration.get()) spiders[spKey] else null
            }
            if (cachedAgain != null) return cachedAgain
            if (generation != clearGeneration.get()) return SpiderNull()
            try {
                if (MAIN_KEY != jaKey) parseJar(jaKey, spiderJar, generation)
                val loader = loaders[jaKey]
                if (loader == null) return SpiderNull()
                val spider = loader.loadClass("com.github.catvod.spider." + className(spiderApi)).newInstance() as Spider
                spider.siteKey = spiderKey
                spider.initApi(SpiderApi())
                SpiderInitWatch.begin()
                try {
                    spider.init(AppContextHolder.context(), spiderExt)
                } finally {
                    SpiderInitWatch.end()
                }
                val registered = synchronized(lifecycleLock) {
                    if (generation != clearGeneration.get()) {
                        false
                    } else {
                        spiders[spKey] = spider
                        true
                    }
                }
                if (!registered) {
                    SpiderReaper.retire(spider)
                    return SpiderNull()
                }
                Log.i(TAG, "getSpider success key=" + spKey)
                return spider
            } catch (e: Throwable) {
                Log.i(TAG, "getSpider error key=" + spKey + ", msg=" + e.message)
                AppLog.e("JarLoader", e)
                return SpiderNull()
            }
        }
    }

    fun searchDanmuUi(name: String, episode: String, longClick: Boolean) {
        try {
            val methods = if (longClick) danmuLongClickMethods else danmuClickMethods
            val method = methods[recent] ?: methods["main"]
            if (method == null) return
            method.invoke(null, name, episode)
        } catch (e: Throwable) {
            AppLog.e("JarLoader", e)
        }
    }

    fun hasDanmuSearchUi(): Boolean {
        return danmuClickMethods.containsKey(recent) || danmuLongClickMethods.containsKey(recent)
    }

    fun jsonExt(key: String, jxs: LinkedHashMap<String, String>, url: String): JSONObject? {
        try {
            val clz = loadParserClass("com.github.catvod.parser.Json" + key)
            val method = clz.getMethod("parse", LinkedHashMap::class.java, String::class.java)
            return method.invoke(null, jxs, url) as JSONObject?
        } catch (e: Throwable) {
            AppLog.e("JarLoader", e)
            return null
        }
    }

    fun jsonExtMix(flag: String, key: String, name: String, jxs: LinkedHashMap<String, HashMap<String, String>>, url: String): JSONObject? {
        try {
            val clz = loadParserClass("com.github.catvod.parser.Mix" + key)
            val method = clz.getMethod("parse", LinkedHashMap::class.java, String::class.java, String::class.java, String::class.java)
            return method.invoke(null, jxs, name, flag, url) as JSONObject?
        } catch (e: Throwable) {
            AppLog.e("JarLoader", e)
            return null
        }
    }

    fun proxyInvoke(params: Map<String, String>?): Array<Any?>? {
        val siteKey = params?.get("siteKey")
        if (!siteKey.isNullOrEmpty()) {
            val key = siteKey
            val result = proxyInvoke(proxyMethods[requireNotNull(siteJarKeys[key]) { "jar key not found for siteKey" }], params)
            if (result != null) return result
        }
        var result = proxyInvoke(proxyMethods[recent], params)
        if (result != null) return result
        for (entry in proxyMethods.entries) {
            if (entry.key == recent) continue
            result = proxyInvoke(entry.value, params)
            if (result != null) return result
        }
        AppLog.e("echo-proxy-jar: no result, siteKey=" + siteKey + " recent=" + recent + " jars=" + proxyMethods.keys)
        return null
    }

    private fun proxyInvoke(method: Method?, params: Map<String, String>?): Array<Any?>? {
        if (method == null) return null
        try {
            val args = if (params == null) null else HashMap(params)
            args?.remove("siteKey")
            return method.invoke(null, args) as Array<Any?>?
        } catch (e: Throwable) {
            val cause = e.cause ?: e
            val bad = StringBuilder()
            if (params != null) {
                for (entry in params.entries) {
                    val v = entry.value
                    if (v == null) continue
                    for (i in v.indices) {
                        if (v[i].code > 0x7f) {
                            if (bad.isNotEmpty()) bad.append(',')
                            bad.append(entry.key)
                            break
                        }
                    }
                }
            }
            AppLog.e("echo-proxy-jar: invoke error | " + cause + " | nonAsciiKeys=" + bad)
            return null
        }
    }

    private fun requireRecentLoader(): DexClassLoader {
        var loader = loaders[recent]
        if (loader == null) loader = loaders[MAIN_KEY]
        if (loader == null) throw IllegalStateException("No jar loaded for recent key: " + recent)
        return loader
    }

    @Throws(ClassNotFoundException::class)
    private fun loadParserClass(name: String): Class<*> {
        var loader = loaders[recent]
        if (loader != null) {
            try {
                return loader.loadClass(name)
            } catch (ignored: ClassNotFoundException) {
                AppLog.d("JarLoader", "class not in cached loader: " + name)
            }
        }
        loader = loaders[MAIN_KEY]
        if (loader != null) return loader.loadClass(name)
        throw ClassNotFoundException(name)
    }

    private fun download(url: String, file: File, generation: Long): File? {
        val temp = File(file.absolutePath + ".tmp")
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("Cannot create Jar cache directory")
            }
            if (generation != clearGeneration.get()) throw InterruptedException("Jar download generation expired")
            if (temp.exists()) temp.delete()
            Http.getSync(url).use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Jar HTTP " + response.code)
                val body = response.body ?: throw IllegalStateException("Jar response body is empty")
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(16384)
                        while (true) {
                            if (Thread.currentThread().isInterrupted || generation != clearGeneration.get()) {
                                throw InterruptedException("Jar download interrupted or expired")
                            }
                            val length = input.read(buffer)
                            if (length < 0) break
                            output.write(buffer, 0, length)
                        }
                        output.flush()
                    }
                }
            }
            if (!temp.exists() || temp.length() <= 0L) {
                throw IllegalStateException("Downloaded Jar is empty")
            }
            if (!replaceCacheFile(temp, file, generation)) {
                throw IllegalStateException("Cannot replace Jar cache")
            }
            return file
        } catch (e: Throwable) {
            AppLog.e("JarLoader", e)
            if (temp.exists()) temp.delete()
        }
        return null
    }

    private fun copyAsset(url: String, file: File, generation: Long): File? {
        val temp = File(file.absolutePath + ".tmp")
        try {
            if (generation != clearGeneration.get()) throw InterruptedException("Asset Jar generation expired")
            val path = assetJarPath(url)
                    ?: throw IllegalArgumentException("Invalid asset Jar path")
            val input = requireNotNull(AppContextHolder.context()) { "app context not ready" }.assets.open(path)
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        if (Thread.currentThread().isInterrupted || generation != clearGeneration.get()) {
                            throw InterruptedException("Asset Jar copy interrupted or expired")
                        }
                        val length = source.read(buffer)
                        if (length < 0) break
                        output.write(buffer, 0, length)
                    }
                    output.flush()
                }
            }
            if (!temp.exists() || temp.length() <= 0L) {
                throw IllegalStateException("Asset Jar is empty")
            }
            if (!replaceCacheFile(temp, file, generation)) {
                throw IllegalStateException("Cannot replace asset Jar cache")
            }
            return file
        } catch (e: Throwable) {
            AppLog.e("JarLoader", e)
            if (temp.exists()) temp.delete()
        }
        return null
    }

    private fun replaceCacheFile(temp: File, target: File, generation: Long): Boolean {
        return synchronized(lifecycleLock) {
            if (generation != clearGeneration.get()) return@synchronized false
            val backup = File(target.absolutePath + ".bak")
            if (backup.exists() && !backup.delete()) return@synchronized false
            if (target.exists() && !target.renameTo(backup)) return@synchronized false
            if (!temp.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                return@synchronized false
            }
            if (backup.exists()) backup.delete()
            true
        }
    }

    private fun local(path: String): File {
        val normalized = path.replace("file:/", "")
        val file = File(Environment.getExternalStorageDirectory(), normalized)
        return if (file.exists()) file else File(normalized)
    }

    private fun fileForJar(jar: String): File {
        return File(jarDir(), jarKey(jar) + ".jar")
    }

    private fun jarDir(): File {
        val dir = File(requireNotNull(AppContextHolder.context()) { "app context not ready" }.cacheDir, "jar")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun exists(file: File?): Boolean {
        return file != null && file.exists() && file.length() > 0
    }

    private fun lock(key: String): Any {
        val lock = locks[key]
        if (lock != null) return lock
        val created = Any()
        val old = locks.putIfAbsent(key, created)
        return old ?: created
    }

    private fun jarKey(jar: String?): String {
        val key = MD5.string2MD5(jar ?: "")
        return if (key.isNullOrEmpty()) MAIN_KEY else key
    }

    private fun realKey(key: String): String {
        val alias = aliases[key]
        return if (alias.isNullOrEmpty()) key else alias
    }

    private fun className(api: String): String {
        return if (api.contains("csp_")) RegexUtils.getPattern("csp_").split(api)[1] else api
    }

    private fun clanToAddress(url: String): String {
        if (url.startsWith("clan://localhost/")) {
            return url.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/")
        }
        if (url.startsWith("clan://")) {
            val text = url.substring(7)
            val index = text.indexOf('/')
            if (index > 0) return "http://" + text.substring(0, index) + "/file/" + text.substring(index + 1)
        }
        return url
    }

    internal fun assetJarPath(url: String): String? {
        val path = when {
            url.startsWith("assets://") -> url.removePrefix("assets://")
            url.startsWith("assets/") -> url.removePrefix("assets/")
            else -> return null
        }
        if (path.isEmpty() || path.split('/').any { it == ".." }) return null
        return path
    }

    private fun injectProxyPort(loader: DexClassLoader?) {
        Proxy.set(getServerPort())
        if (loader == null) return
        try {
            val proxy = loader.loadClass("com.github.catvod.Proxy")
            val set = proxy.getMethod("set", Int::class.javaPrimitiveType)
            set.invoke(null, getServerPort())
        } catch (ignored: Throwable) {
            AppLog.d("JarLoader", "inject proxy port into jar failed")
        }
    }

    private fun getServerPort(): Int {
        try {
            val address = ControlManager.get().getAddress(true)
            if (address.startsWith("http://127.0.0.1:")) {
                val baseUrl = if (address.endsWith("/")) address.substring(0, address.length - 1) else address
                return baseUrl.substring(baseUrl.lastIndexOf(":") + 1).toInt()
            }
        } catch (ignored: Throwable) {
            AppLog.d("JarLoader", "parse server port failed, use RemoteServer.serverPort")
        }
        return RemoteServer.serverPort
    }

    private fun close(closeable: Closeable?) {
        try {
            if (closeable != null) closeable.close()
        } catch (ignored: Throwable) {
            AppLog.d("JarLoader", "close failed")
        }
    }

    companion object {

        private const val TAG: String = "JarLoader"
        private const val MAIN_KEY: String = "main"
    }
}
