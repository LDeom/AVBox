package com.github.avbox.osc.sourcedata

import android.text.TextUtils
import androidx.lifecycle.ViewModel
import com.github.catvod.crawler.SpiderReaper
import com.github.avbox.osc.api.ApiConfig
import com.github.avbox.osc.bean.AbsSortXml
import com.github.avbox.osc.bean.AbsXml
import com.github.avbox.osc.bean.MovieSort
import com.github.avbox.osc.util.AppLog
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

internal fun isCurrentSourceListRequest(requestId: Long, activeRequestId: Long): Boolean =
    requestId == activeRequestId

internal fun isCurrentSourceSortRequest(requestId: Long, activeRequestId: Long): Boolean =
    requestId == activeRequestId

class SourceViewModel : ViewModel() {

    @JvmField
    val sortResult = SourceChannel<AbsSortXml?>()

    @JvmField
    val listResult = SourceChannel<AbsXml?>()

    @JvmField
    val searchResult = SourceChannel<AbsXml?>()

    @JvmField
    val detailResult = SourceChannel<AbsXml?>()

    @JvmField
    val actionResult = SourceChannel<JSONObject?>()

    @JvmField
    val playResult = SourceChannel<JSONObject?>()

    @JvmField
    val preloadResult = SourceChannel<JSONObject?>()

    private val gson = Gson()
    private val pushDetailResolver = PushDetailResolver(gson, detailResult)
    private val resultParser = SourceResultParser(gson, searchResult, detailResult, pushDetailResolver)
    private val detailLoader = DetailLoader(gson, SourceRuntimeState.extendCache, detailResult, resultParser)
    private val searchLoader = SearchLoader(gson, SourceRuntimeState.extendCache, searchResult, resultParser)
    private val playLoader = PlayLoader(gson, SourceRuntimeState.extendCache, playResult, preloadResult)

    private val rootJob = SupervisorJob()
    private val listRequestGeneration = AtomicLong(0)
    private var listChain = SupervisorJob(rootJob)
    private val sortRequestGeneration = AtomicLong(0)
    private var sortChain = SupervisorJob(rootJob)

    private val requestScope: CoroutineScope by lazy { CoroutineScope(rootJob + Dispatchers.Main.immediate) }

    @Volatile
    private var detailChain = SupervisorJob(rootJob)

    @Volatile
    private var searchChain = SupervisorJob(rootJob)

    fun getSort(sourceKey: String?) {
        getSort(sourceKey, true)
    }

    @Synchronized
    fun getSort(sourceKey: String?, withRec: Boolean) {
        val operationId = AppLog.newOperationId()
        AppLog.operationEvent(operationId, "source.sort", "start", "source=${sourceKey.orEmpty()} withRecommendations=$withRec")
        val requestId = sortRequestGeneration.incrementAndGet()
        sortChain.cancel()
        val chain = SupervisorJob(rootJob)
        sortChain = chain
        requestScope.launch(chain) {
            val requestResult = SourceChannel<AbsSortXml?>()
            val homeRecListLoader = ListLoader(
                gson,
                SourceRuntimeState.extendCache,
                SourceChannel<AbsXml?>(),
                resultParser,
            )
            val loader = SortLoader(
                gson,
                SourceRuntimeState.extendCache,
                SourceRuntimeState.sortCache,
                requestResult,
                homeRecListLoader,
                resultParser,
            )
            val forwardJob = launch {
                requestResult.flow.collect { result ->
                    if (isCurrentSourceSortRequest(requestId, sortRequestGeneration.get())) {
                        AppLog.operationEvent(operationId, "source.sort", if (result == null) "empty" else "success", "resultPresent=${result != null}")
                        sortResult.postValue(result)
                    }
                }
            }
            try {
                loader.getSort(sourceKey, withRec)
            } finally {
                forwardJob.cancel()
            }
        }
    }

    @Synchronized
    fun cancelSort() {
        AppLog.operationEvent(AppLog.newOperationId(), "source.sort", "cancel")
        sortRequestGeneration.incrementAndGet()
        sortChain.cancel()
        sortChain = SupervisorJob(rootJob)
    }

    @Synchronized
    fun getList(sourceKey: String?, sortData: MovieSort.SortData?, page: Int) {
        val operationId = AppLog.newOperationId()
        AppLog.operationEvent(operationId, "source.list", "start", "source=${sourceKey.orEmpty()} page=$page category=${sortData?.id.orEmpty()}")
        val requestId = listRequestGeneration.incrementAndGet()
        listChain.cancel()
        val chain = SupervisorJob(rootJob)
        listChain = chain
        requestScope.launch(chain) {
            val requestResult = SourceChannel<AbsXml?>()
            val loader = ListLoader(gson, SourceRuntimeState.extendCache, requestResult, resultParser)
            val forwardJob = launch {
                requestResult.flow.collect { result ->
                    if (isCurrentSourceListRequest(requestId, listRequestGeneration.get())) {
                        AppLog.operationEvent(operationId, "source.list", if (result == null) "empty" else "success", "resultPresent=${result != null}")
                        listResult.postValue(result)
                    }
                }
            }
            try {
                loader.getList(sourceKey, sortData, page)
            } finally {
                forwardJob.cancel()
            }
        }
    }

    @Synchronized
    fun cancelList() {
        AppLog.operationEvent(AppLog.newOperationId(), "source.list", "cancel")
        listRequestGeneration.incrementAndGet()
        listChain.cancel()
        listChain = SupervisorJob(rootJob)
    }

    fun getDetail(sourceKey: String?, urlid: String) {
        getDetail(sourceKey, urlid, false)
    }

    fun getDetail(sourceKey: String?, urlid: String, fallback: Boolean) {
        getDetail(sourceKey, urlid, fallback, null)
    }

    fun getDetail(sourceKey: String?, urlid: String, fallback: Boolean, requestToken: Int?) {
        val operationId = AppLog.newOperationId()
        AppLog.operationEvent(operationId, "source.detail", "start", "source=${sourceKey.orEmpty()} fallback=$fallback hasItemId=${urlid.isNotBlank()} requestToken=${requestToken ?: -1}")
        requestScope.launch(detailChain) { detailLoader.getDetail(sourceKey, urlid, fallback, requestToken) }
    }

    fun cancelDetail() {
        AppLog.operationEvent(AppLog.newOperationId(), "source.detail", "cancel")
        detailChain.cancel()
        detailChain = SupervisorJob(rootJob)
    }

    fun action(sourceKey: String?, action: String?) {
        val operationId = AppLog.newOperationId()
        AppLog.operationEvent(operationId, "source.action", "start", "source=${sourceKey.orEmpty()} actionPresent=${!action.isNullOrBlank()}")
        val sourceBean = ApiConfig.get().getSource(sourceKey)
        if (sourceBean == null || action == null) {
            actionResult.postValue(null)
            return
        }
        if (sourceBean.type == 3) {
            SourceHelper.SPIDER_POOL.execute {
                try {
                    val sp = ApiConfig.get().getCSP(sourceBean)
                    val json = SpiderReaper.track(sp) { sp.action(action) }
                    actionResult.postValue(if (TextUtils.isEmpty(json)) null else JSONObject(json))
                    AppLog.operationEvent(operationId, "source.action", if (TextUtils.isEmpty(json)) "empty" else "success")
                } catch (th: Throwable) {
                    AppLog.e("SourceViewModel", th)
                    AppLog.operationEvent(operationId, "source.action", "failure", "error=${th.javaClass.simpleName}")
                    actionResult.postValue(null)
                }
            }
        } else {
            actionResult.postValue(null)
        }
    }

    fun getSearch(sourceKey: String?, wd: String?) {
        getSearch(sourceKey, wd, "")
    }

    fun getSearch(sourceKey: String?, wd: String?, searchToken: String?) {
        val operationId = AppLog.newOperationId()
        AppLog.operationEvent(operationId, "source.search", "start", "source=${sourceKey.orEmpty()} queryLength=${wd?.length ?: 0}")
        requestScope.launch(searchChain) { searchLoader.getSearch(sourceKey, wd, searchToken) }
    }

    fun cancelSearch() {
        AppLog.operationEvent(AppLog.newOperationId(), "source.search", "cancel")
        searchChain.cancel()
        searchChain = SupervisorJob(rootJob)
    }

    fun getPlay(sourceKey: String?, playFlag: String?, progressKey: String?, url: String?, subtitleKey: String?) {
        val operationId = AppLog.newOperationId()
        AppLog.operationEvent(operationId, "source.play.resolve", "start", "source=${sourceKey.orEmpty()} flagPresent=${!playFlag.isNullOrBlank()} hasUrl=${!url.isNullOrBlank()} subtitlePresent=${!subtitleKey.isNullOrBlank()}")
        playLoader.getPlay(sourceKey, playFlag, progressKey, url, subtitleKey)
    }

    fun getPlayForPreload(sourceKey: String?, playFlag: String?, progressKey: String?, url: String?, subtitleKey: String?) {
        AppLog.operationEvent(AppLog.newOperationId(), "source.play.preload", "start", "source=${sourceKey.orEmpty()} hasUrl=${!url.isNullOrBlank()}")
        playLoader.getPlayForPreload(sourceKey, playFlag, progressKey, url, subtitleKey)
    }

    fun cancelPlayRequest() {
        AppLog.operationEvent(AppLog.newOperationId(), "source.play.resolve", "cancel")
        playLoader.cancelPlayRequest()
    }

    fun checkThunder(data: AbsXml, index: Int) {
        pushDetailResolver.checkThunder(data, index)
    }

    override fun onCleared() {
        AppLog.operationEvent(AppLog.newOperationId(), "source.viewmodel", "cleared")
        rootJob.cancel()
        super.onCleared()
    }
}
