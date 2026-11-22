package com.github.avbox.osc.player.danmu

import com.whl.quickjs.wrapper.QuickJSContext
import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/** Dedicated QuickJS context. No crawler Global/local objects or credential APIs are exposed. */
class JsDanmuPluginRuntime(
    private val source: String,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "avbox-danmu-js").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    @Volatile private var context: QuickJSContext? = null
    @Volatile private var initialized = false

    fun initialize(): JSONObject = submit {
        check(!closed.get()) { "runtime closed" }
        val js = QuickJSContext.create()
        context = js
        val bootstrap = """
            (function () {
              const module = { exports: {} };
              const exports = module.exports;
              const source = __SOURCE_LITERAL__;
              (new Function('module', 'exports', source))(module, exports);
              globalThis.__avboxDanmuPlugin = module.exports;
              const p = globalThis.__avboxDanmuPlugin;
              if (!p || typeof p !== 'object') throw new Error('CommonJS module.exports must be an object');
              const meta = p.manifest || p.meta || {};
              return JSON.stringify({
                name: String(meta.name || p.name || 'Unnamed plugin'),
                version: String(meta.version || p.version || ''),
                apiVersion: String(meta.apiVersion || p.apiVersion || ''),
                hasSearch: typeof p.search === 'function',
                hasLoad: typeof p.load === 'function'
              });
            })()
        """.trimIndent().replace("__SOURCE_LITERAL__", JSONObject.quote(source))
        val result = js.evaluate(bootstrap)?.toString().orEmpty()
        val meta = JSONObject(result)
        require(meta.optBoolean("hasSearch") || meta.optBoolean("hasLoad")) {
            "插件至少需要导出 search 或 load 函数"
        }
        initialized = true
        meta
    }

    fun search(query: String): JSONArray = invokeArray("search", JSONObject().put("query", query))
    fun load(pluginResult: JSONObject): JSONArray = invokeArray("load", pluginResult)

    private fun invokeArray(method: String, argument: JSONObject): JSONArray = submit {
        check(initialized && !closed.get()) { "runtime not initialized" }
        val script = """
            (function () {
              const p = globalThis.__avboxDanmuPlugin;
              if (!p || typeof p['__METHOD__'] !== 'function') return '[]';
              const arg = JSON.parse(__ARG_LITERAL__);
              const value = p['__METHOD__'](arg);
              if (value && typeof value.then === 'function') throw new Error('Promise return is not supported yet');
              return JSON.stringify(value == null ? [] : value);
            })()
        """.trimIndent()
            .replace("__METHOD__", method)
            .replace("__ARG_LITERAL__", JSONObject.quote(argument.toString()))
        val raw = context!!.evaluate(script)?.toString().orEmpty()
        val array = JSONArray(raw)
        require(array.length() <= MAX_RESULT_ITEMS) { "插件返回条目超过限制" }
        array
    }

    private fun <T> submit(block: () -> T): T {
        check(!closed.get()) { "runtime closed" }
        val future = executor.submit(Callable { block() })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw IllegalStateException("JS 插件执行超时", e)
        } catch (e: Exception) {
            throw IllegalStateException("JS 插件执行失败: ${e.cause?.message ?: e.message}", e)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            executor.submit {
                runCatching { context?.destroy() }
                context = null
                initialized = false
            }.get(500, TimeUnit.MILLISECONDS)
        }
        executor.shutdownNow()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val MAX_RESULT_ITEMS = 5_000
    }
}