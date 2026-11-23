@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.avbox.osc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.LiveChannelGroup
import com.github.avbox.osc.player.core.PlayState
import com.github.avbox.osc.ui.components.AVBoxAlertDialog
import com.github.avbox.osc.ui.components.AVBoxBottomSheet
import com.github.avbox.osc.ui.components.LoadStateBox
import com.github.avbox.osc.ui.components.LoadState
import com.github.avbox.osc.ui.components.LocalSheetDismiss
import com.github.avbox.osc.ui.components.LocalSheetDismissThen
import com.github.avbox.osc.ui.components.SettingsCard
import com.github.avbox.osc.ui.components.SettingsCardPosition
import com.github.avbox.osc.ui.components.SettingsGroup
import com.github.avbox.osc.ui.components.SettingsOptionRow
import com.github.avbox.osc.ui.components.SettingsSwitchRow
import com.github.avbox.osc.ui.theme.cardContainer
import java.util.ArrayList
import java.util.Date
import kotlin.math.max

@Composable
internal fun LiveScreen(state: LivePlayUiState, actions: LivePlayActions) {
    val background = if (state.frame.isFullBox) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        when (state.frame.pageState) {
            PageState.LOADING -> LoadStateBox(
                state = LoadState.Loading,
                emptyText = "",
                errorText = "",
                retryText = "",
                modifier = Modifier.fillMaxSize(),
                loadingContent = { ContainedLoadingIndicator(Modifier.size(64.dp)) },
            )

            PageState.EMPTY -> LoadStateBox(
                state = LoadState.Error(stringResource(R.string.live_empty_channels)),
                emptyText = "",
                errorText = stringResource(R.string.live_empty_channels),
                retryText = stringResource(R.string.common_retry),
                modifier = Modifier.fillMaxSize(),
                onRetry = actions.page.onRetryLoad,
            )

            PageState.READY -> LiveReadyContent(state, actions)
        }
        if (state.epg.sheetVisible) {
            EpgSheet(
                epg = state.epg,
                onDismiss = actions.epg.onDismiss,
                onRowClicked = actions.epg.onRowClicked,
            )
        }
        if (state.settings.sheetVisible) {
            SettingsSheet(
                settings = state.settings,
                onItemClick = actions.settings.onItemClick,
                onItemLongClick = actions.settings.onItemLongClick,
                onDismiss = actions.settings.onDismiss,
            )
        }
        state.passwordDialogTarget?.let {
            LivePasswordDialog(
                onConfirm = actions.page.onPasswordConfirm,
                onDismiss = actions.page.onPasswordDismiss,
            )
        }
    }
}

@Composable
private fun LivePasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AVBoxAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_password_title)) },
        text = {
            val dismissThen = LocalSheetDismissThen.current
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text(stringResource(R.string.live_password_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (password.isNotBlank()) dismissThen { onConfirm(password.trim()) }
                    },
                ),
            )
        },
        confirmButton = {
            val dismissThen = LocalSheetDismissThen.current
            TextButton(
                enabled = password.isNotBlank(),
                onClick = { dismissThen { onConfirm(password.trim()) } },
            ) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            val dismissAnimated = LocalSheetDismiss.current
            TextButton(onClick = { dismissAnimated() }) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private const val PHONE_DRAWER_HEIGHT = 0.86f
private val PHONE_DRAWER_MAX_WIDTH = 320.dp
private const val TABLET_DRAWER_HEIGHT = 0.88f
private val TABLET_DRAWER_MIN_WIDTH = 240.dp
private val TABLET_DRAWER_MAX_WIDTH = 340.dp

@Composable
private fun LiveReadyContent(state: LivePlayUiState, actions: LivePlayActions) {
    val configuration = LocalConfiguration.current
    val tabletLandscape = configuration.smallestScreenWidthDp >= 600 &&
        configuration.screenWidthDp > configuration.screenHeightDp
    val longestChannelLabelWidth = state.channelList.rows.maxOfOrNull { row ->
        val label = (row.channel?.channelName ?: row.group?.groupName).orEmpty()
        label.sumOf { if (it.code > 0x2ff) 15 else 8 }
    } ?: 0
    // A narrow window or transient configuration during rotation can make the
    // screen-derived maximum smaller than the design minimum. Dp.coerceIn()
    // throws when min > max, which previously crashed LiveReadyContent.
    val tabletDrawerMaxWidth = minOf(
        TABLET_DRAWER_MAX_WIDTH,
        configuration.screenWidthDp.dp * 0.48f,
    ).coerceAtLeast(1.dp)
    val tabletDrawerMinWidth = minOf(TABLET_DRAWER_MIN_WIDTH, tabletDrawerMaxWidth)
    val tabletDrawerWidth = (longestChannelLabelWidth.dp + 84.dp).coerceIn(
        tabletDrawerMinWidth,
        tabletDrawerMaxWidth,
    )
    val phoneDrawerMaxWidth = minOf(
        PHONE_DRAWER_MAX_WIDTH,
        configuration.screenWidthDp.dp * 0.66f,
    ).coerceAtLeast(1.dp)
    val phoneDrawerMinWidth = minOf(200.dp, phoneDrawerMaxWidth)
    val phoneDrawerWidth = (longestChannelLabelWidth.dp + 72.dp).coerceIn(
        phoneDrawerMinWidth,
        phoneDrawerMaxWidth,
    )
    if (tabletLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(state.channelList.channelDrawerVisible) {
                    val edge = 36.dp.toPx()
                    val trigger = 84.dp.toPx()
                    var startX = -1f
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { startX = it.x; drag = 0f },
                        onDragEnd = {
                            if (!state.channelList.channelDrawerVisible && startX >= 0f && startX <= edge && drag > trigger) actions.list.onToggleDrawer()
                            startX = -1f
                        },
                        onDragCancel = { startX = -1f },
                        onHorizontalDrag = { change, amount ->
                            drag += amount
                            if (!state.channelList.channelDrawerVisible && startX >= 0f && startX <= edge && drag > trigger) {
                                actions.list.onToggleDrawer()
                                startX = -1f
                            }
                        },
                    )
                },
        ) {
            PlayerArea(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxSize(),
                showCornerButtons = false,
                drawFullscreenControls = false,
                blurVideo = state.channelList.channelDrawerVisible,
            )
            if (state.overlay.visible) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = 12.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.52f),
                    shadowElevation = 8.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
                        PlayerCornerButton(
                            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.live_exit_fullscreen), tint = Color.White) },
                            onClick = actions.player.onExitFullscreen,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(modifier = Modifier.clickable(onClick = actions.list.onToggleDrawer), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Menu, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = stringResource(R.string.live_channel_list), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                PlayerCornerButtons(
                    actions.player,
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 18.dp, top = 12.dp),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 20.dp)
                        .fillMaxWidth(0.70f)
                        .widthIn(max = 760.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.64f),
                    shadowElevation = 12.dp,
                ) {
                    LandscapeChannelInfoSection(
                        info = state.channelInfo,
                        timeshift = state.timeshift,
                        overlay = state.overlay,
                    )
                }
            }
            if (state.channelList.channelDrawerVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.24f))
                        .clickable(onClick = actions.list.onDismissDrawer),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight(TABLET_DRAWER_HEIGHT)
                        .padding(start = 10.dp)
                        .width(tabletDrawerWidth),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.live_channel_list),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = state.channelInfo.name,
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = state.channelList.rows.count { it.channel != null }.toString(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            PlayerCornerButton(
                                icon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.rotate(180f).size(18.dp),
                                    )
                                },
                                onClick = actions.list.onDismissDrawer,
                            )
                        }
                        LandscapeChannelListSection(
                            channelList = state.channelList,
                            onSelectGroup = actions.list.onSelectGroup,
                            onSelectChannel = actions.list.onSelectChannel,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
        return
    }
    val shortEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    val longEdge = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    val previewHeight = (shortEdge * 9f / 16f)
        .coerceAtLeast(150.dp)
        .coerceAtMost(maxOf(150.dp, longEdge / 2))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.frame.isFullBox) Color.Black else MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(state.channelList.channelDrawerVisible, state.frame.isFullBox) {
                val edge = 28.dp.toPx()
                val trigger = 72.dp.toPx()
                var startX = -1f
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { startX = it.x; drag = 0f },
                    onDragEnd = {
                        if (!state.channelList.channelDrawerVisible && startX >= 0f && startX <= edge && drag > trigger) actions.list.onToggleDrawer()
                        startX = -1f
                    },
                    onDragCancel = { startX = -1f },
                    onHorizontalDrag = { _, amount ->
                        drag += amount
                        if (!state.channelList.channelDrawerVisible && startX >= 0f && startX <= edge && drag > trigger) {
                            actions.list.onToggleDrawer()
                            startX = -1f
                        }
                    },
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlayerArea(
                state = state,
                actions = actions,
                drawFullscreenControls = false,
                modifier = if (state.frame.isFullBox) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .height(previewHeight)
                },
            )
            if (!state.frame.isFullBox) {
                ChannelInfoSection(info = state.channelInfo, timeshift = state.timeshift, overlay = state.overlay)
                ChannelListSection(
                    channelList = state.channelList,
                    onToggleGroup = actions.list.onToggleGroup,
                    onSelectChannel = actions.list.onSelectChannel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.frame.isFullBox && state.overlay.visible) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 8.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.52f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    PlayerCornerButton(
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.live_exit_fullscreen), tint = Color.White) },
                        onClick = actions.player.onExitFullscreen,
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(modifier = Modifier.clickable(onClick = actions.list.onToggleDrawer).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Menu, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.live_channel_list), color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            PlayerCornerButtons(actions.player, Modifier.align(Alignment.TopEnd).statusBarsPadding())
        }
        if (state.frame.isFullBox && state.channelList.channelDrawerVisible) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)).clickable(onClick = actions.list.onDismissDrawer))
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(PHONE_DRAWER_HEIGHT)
                    .width(phoneDrawerWidth).padding(start = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.28f),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.live_channel_list), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(state.channelInfo.name, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(state.channelList.rows.count { it.channel != null }.toString(), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                        PlayerCornerButton(
                            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.rotate(180f)) },
                            onClick = actions.list.onDismissDrawer,
                        )
                    }
                    ChannelListSection(
                        channelList = state.channelList,
                        onToggleGroup = actions.list.onToggleGroup,
                        onSelectChannel = actions.list.onSelectChannel,
                        modifier = Modifier.weight(1f).fillMaxWidth().blur(0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerArea(
    state: LivePlayUiState,
    actions: LivePlayActions,
    modifier: Modifier,
    showCornerButtons: Boolean = true,
    drawFullscreenControls: Boolean = true,
    blurVideo: Boolean = false,
) {
    val videoView = actions.player.videoView()
    Box(
        modifier = modifier
            .background(Color.Black)
            .then(if (blurVideo) Modifier.blur(30.dp) else Modifier),
    ) {
        if (videoView != null) {
            AndroidView(
                factory = { videoView },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.player.snapshotVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                state.player.snapshotBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            }
        }
        if (!state.player.snapshotVisible &&
            (state.player.playState == PlayState.PREPARING || state.player.playState == PlayState.BUFFERING)
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
        }
        if (state.player.resolutionVisible && state.player.resolutionText.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Text(
                    text = state.player.resolutionText,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        state.player.gestureHintText?.let { hint ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Text(
                    text = hint,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        if (state.timeshift.isBackState && state.overlay.visible) {
            TimeshiftBar(
                timeshift = state.timeshift,
                paused = state.player.playState == PlayState.PAUSED,
                onSeek = actions.player.onTimeshiftSeek,
                onTogglePlay = actions.player.onTimeshiftTogglePlay,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (!state.frame.isFullBox) {
            if (showCornerButtons) {
                PlayerCornerButtons(actions.player, Modifier.align(Alignment.TopEnd))
            }
        } else if (state.overlay.visible && drawFullscreenControls) {
            IconButton(
                onClick = actions.player.onExitFullscreen,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.live_exit_fullscreen),
                    tint = Color.White,
                )
            }
            PlayerCornerButtons(actions.player, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun PlayerCornerButtons(actions: LivePlayerActions, modifier: Modifier) {
    Row(modifier = modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerCornerButton(
            icon = {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = stringResource(R.string.live_epg),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = actions.onEpgSheetOpen,
        )
        PlayerCornerButton(
            icon = {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.live_settings),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = actions.onSettingsSheetOpen,
        )
    }
}

@Composable
private fun PlayerCornerButton(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun TimeshiftBar(
    timeshift: LiveTimeshiftUi,
    paused: Boolean,
    onSeek: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(R.string.player_play_pause),
                tint = Color.White,
            )
        }
        Slider(
            value = timeshift.position.toFloat().coerceIn(0f, max(timeshift.duration, 1).toFloat()),
            onValueChange = onSeek,
            valueRange = 0f..max(timeshift.duration, 1).toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = LiveEpgParser.durationToString(timeshift.position) + " / " + LiveEpgParser.durationToString(timeshift.duration),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LandscapeChannelInfoSection(
    info: ChannelInfoUi,
    timeshift: LiveTimeshiftUi,
    overlay: LiveOverlayUi,
) {
    if (info.name.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(7.dp), color = Color.White.copy(alpha = 0.16f)) {
                Text(
                    text = info.num.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = info.name,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFDC4545).copy(alpha = 0.20f),
            ) {
                Text(
                    text = if (timeshift.isBackState) stringResource(R.string.live_replaying) else stringResource(R.string.live_on_air),
                    color = Color(0xFFFF7777),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            if (info.sourceText.isNotEmpty()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = info.sourceText, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = info.currentEpgTime,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = info.currentEpgTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (overlay.showTimeOn && overlay.timeText.isNotEmpty()) {
                Text(text = overlay.timeText, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
            }
        }
        if (info.nextEpgTitle.isNotEmpty() || (overlay.showNetSpeedOn && overlay.netSpeedText.isNotEmpty())) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = info.nextEpgTime,
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info.nextEpgTitle,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (overlay.showNetSpeedOn && overlay.netSpeedText.isNotEmpty()) {
                    Text(text = overlay.netSpeedText, color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ChannelInfoSection(
    info: ChannelInfoUi,
    timeshift: LiveTimeshiftUi,
    overlay: LiveOverlayUi,
) {
    if (info.name.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = info.num.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = info.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (timeshift.isBackState) {
                Text(text = stringResource(R.string.live_replaying), fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
            } else {
                Text(text = stringResource(R.string.live_on_air), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (info.sourceText.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = info.sourceText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = info.currentEpgTime + "  " + info.currentEpgTitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = info.nextEpgTime + "  " + info.nextEpgTitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (overlay.showTimeOn || overlay.showNetSpeedOn) {
            Spacer(modifier = Modifier.height(2.dp))
            val parts = ArrayList<String>()
            if (overlay.showTimeOn && overlay.timeText.isNotEmpty()) parts.add(overlay.timeText)
            if (overlay.showNetSpeedOn && overlay.netSpeedText.isNotEmpty()) parts.add(overlay.netSpeedText)
            Text(
                text = parts.joinToString("  "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelListSection(
    channelList: LiveChannelListUi,
    onToggleGroup: (Int) -> Unit,
    onSelectChannel: (Int, Int) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(channelList.scrollRequestId) {
        var target = -1
        for (i in channelList.rows.indices) {
            val row = channelList.rows[i]
            if (row.channel != null &&
                row.group?.groupIndex == channelList.playingGroupIndex &&
                row.channel.channelIndex == channelList.playingChannelIndex
            ) {
                target = i
                break
            }
        }
        if (target > 0) listState.animateScrollToItem(max(0, target - 2))
    }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = navBarInset + 24.dp),
    ) {
        itemsIndexed(channelList.rows, key = { _, row -> row.key }) { _, row ->
            val channel = row.channel
            if (channel == null) {
                val group = row.group ?: return@itemsIndexed
                GroupHeaderRow(
                    group = group,
                    expanded = channelList.expandedGroups.contains(group.groupIndex),
                    locked = channelList.lockedGroups.contains(group.groupIndex),
                    onToggle = { onToggleGroup(group.groupIndex) },
                )
            } else {
                val group = row.group
                ChannelRow(
                    row = row,
                    selected = group != null &&
                        group.groupIndex == channelList.playingGroupIndex &&
                        channel.channelIndex == channelList.playingChannelIndex,
                    onSelect = { onSelectChannel(group?.groupIndex ?: 0, row.channelPos) },
                )
            }
        }
    }
}

@Composable
private fun LandscapeChannelListSection(
    channelList: LiveChannelListUi,
    onSelectGroup: (Int) -> Unit,
    onSelectChannel: (Int, Int) -> Unit,
    modifier: Modifier,
) {
    val groups = channelList.rows.mapNotNull { row ->
        if (row.channel == null) row.group else null
    }
    val selectedGroup = groups.firstOrNull { it.groupIndex == channelList.tappedGroupIndex }
        ?: groups.firstOrNull()
    val selectedGroupIndex = selectedGroup?.groupIndex ?: -1
    val channels = channelList.rows.filter {
        it.channel != null && it.group?.groupIndex == selectedGroupIndex
    }
    val listState = rememberLazyListState()
    LaunchedEffect(channelList.scrollRequestId, selectedGroupIndex, channelList.playingChannelIndex) {
        val target = channels.indexOfFirst {
            it.group?.groupIndex == channelList.playingGroupIndex &&
                it.channel?.channelIndex == channelList.playingChannelIndex
        }
        if (target >= 0) listState.animateScrollToItem(target)
    }
    Row(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.weight(0.40f).fillMaxHeight().padding(end = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.live_main_categories),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = groups.size.toString(),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                itemsIndexed(groups, key = { _, group -> "main_" + group.groupIndex }) { _, group ->
                    val selected = group.groupIndex == selectedGroupIndex
                    val locked = channelList.lockedGroups.contains(group.groupIndex)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.045f),
                            )
                            .clickable { onSelectGroup(group.groupIndex) }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedListText(
                                text = group.groupName.orEmpty(),
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = (group.liveChannels?.size ?: 0).toString(),
                                color = Color.White.copy(alpha = 0.58f),
                                fontSize = 10.sp,
                            )
                        }
                        if (locked) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.live_need_password),
                                tint = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = if (selected) 0.95f else 0.42f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Column(
            modifier = Modifier.weight(0.60f).fillMaxHeight().padding(start = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedGroup?.groupName.orEmpty(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = channels.size.toString(),
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            when {
                selectedGroup == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.live_empty_channels),
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 13.sp,
                    )
                }
                channelList.lockedGroups.contains(selectedGroupIndex) -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.live_need_password),
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 13.sp,
                        )
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(channels, key = { _, row -> row.key }) { _, row ->
                        val channel = row.channel ?: return@itemsIndexed
                        val selected = selectedGroupIndex == channelList.playingGroupIndex &&
                            channel.channelIndex == channelList.playingChannelIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else Color.White.copy(alpha = 0.035f),
                                )
                                .clickable { onSelectChannel(selectedGroupIndex, row.channelPos) }
                                .padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(30.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                        else Color.White.copy(alpha = 0.08f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp),
                                    )
                                } else {
                                    Text(
                                        text = channel.channelNum.toString(),
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedListText(
                                text = channel.channelName.orEmpty(),
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Text(
                                    text = stringResource(R.string.live_now_playing),
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlinedListText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Text(
            text = text,
            style = style.copy(
                color = Color.Black,
                drawStyle = Stroke(width = 4f, join = StrokeJoin.Round),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text,
            style = style.copy(color = Color.White, drawStyle = Fill),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GroupHeaderRow(
    group: LiveChannelGroup,
    expanded: Boolean,
    locked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedListText(
            text = group.groupName ?: "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (locked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(R.string.live_need_password),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 90f else 0f),
        )
    }
}

@Composable
private fun ChannelRow(
    row: LiveListRow,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val channel = row.channel ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.cardContainer else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedListText(
            text = channel.channelNum.toString(),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.width(36.dp),
        )
        OutlinedListText(
            text = channel.channelName ?: "",
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EpgSheet(
    epg: LiveEpgUi,
    onDismiss: () -> Unit,
    onRowClicked: (Int) -> Boolean,
) {
    val channelNameStr = epg.channelName
    AVBoxBottomSheet(
        onDismissRequest = onDismiss,
        title = if (channelNameStr.isEmpty()) {
            stringResource(R.string.live_epg)
        } else {
            stringResource(R.string.live_epg_of, channelNameStr)
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        isScrollable = false,
    ) {
        val dismissAnimated = LocalSheetDismiss.current
        val epgList = epg.epgList
        if (epgList.isEmpty()) {
            Text(
                text = stringResource(R.string.live_epg_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
            return@AVBoxBottomSheet
        }
        val canCatchup = epg.canCatchup
        val now = Date()
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(epgList) { index, item ->
                val isNow = item.startdateTime != null && item.enddateTime != null &&
                        !now.before(item.startdateTime) && !now.after(item.enddateTime)
                val clickable = item.startdateTime != null && !now.before(item.startdateTime) &&
                        (canCatchup || (item.enddateTime != null && !now.after(item.enddateTime)))
                val selected = index == epg.lookBackIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = clickable) {
                            if (onRowClicked(index)) dismissAnimated()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.start + "-" + item.end,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.title.orEmpty(),
                        fontSize = 14.sp,
                        fontWeight = if (selected || isNow) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            isNow -> MaterialTheme.colorScheme.onSurface
                            clickable -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        selected -> Text(
                            text = stringResource(R.string.live_replaying),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        isNow -> Text(
                            text = stringResource(R.string.live_now_playing),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: LiveSettingsUi,
    onItemClick: (Int, Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AVBoxBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.live_settings),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        isScrollable = false,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            settings.groups.forEach { group ->
                item(key = "sg" + group.groupIndex) {
                    SettingsGroup(
                        title = if (group.longPressDelete) {
                            stringResource(R.string.live_group_long_press_delete, group.title)
                        } else {
                            group.title
                        },
                    ) {
                        group.items.forEachIndexed { index, item ->
                            val position = when {
                                group.items.size == 1 -> SettingsCardPosition.SINGLE
                                index == 0 -> SettingsCardPosition.FIRST
                                index == group.items.size - 1 -> SettingsCardPosition.LAST
                                else -> SettingsCardPosition.MIDDLE
                            }
                            SettingsCard(
                                position = position,
                                color = MaterialTheme.colorScheme.surfaceBright,
                            ) {
                                if (group.switchRow) {
                                    SettingsSwitchRow(
                                        title = item.title,
                                        checked = item.checked,
                                        onCheckedChange = { onItemClick(group.groupIndex, item.itemIndex) },
                                    )
                                } else {
                                    SettingsOptionRow(
                                        title = item.title,
                                        selected = item.selected,
                                        onClick = { onItemClick(group.groupIndex, item.itemIndex) },
                                        onLongClick = if (group.groupIndex == LiveSettingsSnapshot.CONFIG_GROUP_INDEX && item.itemIndex > 0) {
                                            { onItemLongClick(item.itemIndex - 1) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
