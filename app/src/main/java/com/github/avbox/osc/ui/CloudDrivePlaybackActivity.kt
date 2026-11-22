package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.VodInfo
import com.github.avbox.osc.player.PageHost
import com.github.avbox.osc.player.PlaybackSession
import com.github.avbox.osc.player.PlaybackService
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.components.SheetHostScaffold
import com.github.avbox.osc.ui.player.PlayContainer
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.PermissionHelper

class CloudDrivePlaybackActivity : BaseActivity(), PageHost {

    private var player: PlayContainer? = null
    private var session: PlaybackSession? = null

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) player?.onLocalSubtitlePicked(uri)
    }

    companion object {
        private const val EXTRA_FILE_ID = "cloud_file_id"
        private const val EXTRA_FILE_NAME = "cloud_file_name"
        private const val CLOUD_SOURCE_KEY = "cloud:guangyapan"
        private const val CLOUD_PLAY_FLAG = "云盘"

        fun start(context: Context, fileId: String, fileName: String) {
            context.startActivity(
                Intent(context, CloudDrivePlaybackActivity::class.java)
                    .putExtra(EXTRA_FILE_ID, fileId)
                    .putExtra(EXTRA_FILE_NAME, fileName),
            )
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
        super.hideSysBar()
    }

    override fun init() {
        val fileId = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        if (fileId.isBlank() || fileName.isBlank()) {
            finish()
            return
        }
        session = createSession(fileId, fileName)
        enableTransparentEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (player?.onBackPressed() != true) finish()
            }
        })
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                SheetHostScaffold {
                    CloudDrivePlayerContent()
                }
            }
        }
    }

    @Composable
    private fun CloudDrivePlayerContent() {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayContainer(this@CloudDrivePlaybackActivity).also { container ->
                    container.pageHostRef = this@CloudDrivePlaybackActivity
                    player = container
                    container.post {
                        if (isPageAlive()) {
                            container.hostResume()
                            session?.let(container::setData)
                        }
                    }
                }
            },
            update = {},
        )
    }

    private fun createSession(fileId: String, fileName: String): PlaybackSession {
        val series = arrayListOf(VodInfo.VodSeries(fileName, "guangya://$fileId"))
        val seriesMap = linkedMapOf<String?, MutableList<VodInfo.VodSeries>>(CLOUD_PLAY_FLAG to series)
        val flags = arrayListOf(VodInfo.VodSeriesFlag(CLOUD_PLAY_FLAG))
        val vod = VodInfo(
            id = "guangya:$fileId",
            name = fileName,
            type = "网盘视频",
            seriesFlags = flags,
            seriesMap = seriesMap,
            playFlag = CLOUD_PLAY_FLAG,
            playIndex = 0,
            sourceKey = CLOUD_SOURCE_KEY,
            sourceName = "光鸭云盘",
        )
        return PlaybackSession(vod, CLOUD_SOURCE_KEY, false)
    }

    override fun onResume() {
        super.onResume()
        player?.hostResume()
    }

    override fun onPause() {
        player?.hostPause()
        super.onPause()
    }

    override fun onDestroy() {
        player?.hostDestroy()
        player = null
        super.onDestroy()
    }

    override fun context(): Context = this

    override fun isPageAlive(): Boolean = !isFinishing && !isDestroyed

    override fun runOnUi(action: Runnable) {
        if (isPageAlive()) runOnUiThread(action)
    }

    override fun toast(text: CharSequence) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun launchLocalSubtitlePicker() {
        subtitlePicker.launch(arrayOf("*/*"))
    }

    override fun requestNotificationPermission() {
        PermissionHelper.requestNotificationIfNeeded(this)
    }

    override fun showEpisodeSheet() {
    }

    override fun onPlaybackLinesExhausted(): Boolean = false
}
