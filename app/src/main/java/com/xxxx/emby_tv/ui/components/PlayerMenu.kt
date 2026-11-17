package com.xxxx.emby_tv.ui.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.xxxx.emby_tv.R
import java.util.Locale
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.Utils.formatFileSize
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.model.RemoteSubtitleInfoDto
import com.xxxx.emby_tv.ui.player.SubtitleLanguageResolver
import com.xxxx.emby_tv.util.ErrorHandler
import com.xxxx.emby_tv.util.ExternalPlayerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val SUBTITLE_SYNC_STEP_MS = 250L
private const val SUBTITLE_SYNC_MAX_STEPS = 120

@Composable
fun PlayerMenu(
    onDismiss: () -> Unit,
    media: MediaDto,
    mediaInfo: BaseItemDto,
    subtitleTracks: List<MediaStreamDto>,
    selectedSubtitleIndex: Int,
    onSubtitleSelect: (Int) -> Unit,
    audioTracks: List<MediaStreamDto>,
    selectedAudioIndex: Int,
    onAudioSelect: (Int) -> Unit,
    playbackCorrection: Int,
    onPlaybackCorrectionChange: (Int) -> Unit,
    playMode: Int,
    onPlayModeChange: (Int) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    serverUrl: String,
    repository: EmbyRepository,
    position:Long,
    onRePlayer: (BaseItemDto,Long) -> Unit,
    onNavigateToPlayer: (BaseItemDto) -> Unit,
    autoSkipIntro: Boolean = false,
    onAutoSkipIntroChange: (Boolean) -> Unit = {},
    minBufferMs: Int = 40_000,
    onMinBufferMsChange: (Int) -> Unit = {},
    maxBufferMs: Int = 120_000,
    onMaxBufferMsChange: (Int) -> Unit = {},
    playbackBufferMs: Int = 3_000,
    onPlaybackBufferMsChange: (Int) -> Unit = {},
    rebufferMs: Int = 5_000,
    onRebufferMsChange: (Int) -> Unit = {},
    bufferSizeBytes: Int = 134_217_728,
    onBufferSizeBytesChange: (Int) -> Unit = {},
    onResetBufferDefaults: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    subtitleBottomPadding: Float = 0.08f,
    onSubtitleBottomPaddingChange: (Float) -> Unit = {},
    subtitleTimeOffsetMs: Long = 0L,
    onSubtitleTimeOffsetChange: (Long) -> Unit = {},
    currentMediaSourceId: String? = null,
    allMediaSources: List<MediaSourceInfoDto> = emptyList(),
    onSourceSelect: (MediaSourceInfoDto) -> Unit = {},
    onLaunchExternalPlayer: (String?) -> Unit = {},
    onSubtitleDownloaded: (Int, RemoteSubtitleInfoDto) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // 字幕 Tab 内打开了在线字幕搜索页时，禁止 Tab 行因焦点被系统恢复而自动切走
    var onlineSubtitleSearchActive by remember { mutableStateOf(false) }

    // Determine if Episodes tab should be shown
    val isSeries = mediaInfo.type == "Episode" || mediaInfo.seriesId != null

    // 完整片源列表（多版本）。切换片源后服务器只返回单个源，须使用上层缓存的完整列表
    val mediaSources = allMediaSources.ifEmpty { media.mediaSources ?: emptyList() }

    // Build tabs dynamically
    val tabs = remember(isSeries, mediaSources.size) {
        val list = mutableListOf<String>()
        if (isSeries) list.add("Episodes") // 0 (if present)
        list.add("Info") // 0 or 1
        if (mediaSources.size > 1) list.add("Version")
        list.add("Speed") // 倍速
        list.add("Subtitles") // 2 or 1
        list.add("Audio") // 3 or 2
        list.add("Mode")
        list.add("Correction") // ...
        if (isSeries) list.add("IntroSkip") // 跳过片头仅对电视剧有意义，电影不显示
        list.add("Buffer") // Buffer settings
        list.add("External") // 外部播放器
        list
    }

    // Helper to map UI index to content type
    fun getTabContent(index: Int): String {
        return tabs.getOrNull(index) ?: ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,

            ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))),
                colors = SurfaceDefaults.colors(
                    containerColor = Color(0xFF161616).copy(alpha = 0.5f),
                    contentColor = Color.White
                )

            ) {

                Column {
                    // Tabs
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        itemsIndexed(tabs) { index, title ->
                            val displayTitle = when (title) {
                                "Info" -> stringResource(R.string.info)
                                "Episodes" -> stringResource(R.string.episodes)
                                "Version" -> stringResource(R.string.media_source)
                                "External" -> stringResource(R.string.external_player)
                                "Speed" -> stringResource(R.string.playback_speed)
                                "Subtitles" -> stringResource(R.string.subtitles)
                                "Audio" -> stringResource(R.string.audio_label)
                                "Mode" -> stringResource(R.string.play_mode)
                                "Correction" -> stringResource(R.string.playback_correction)
                                "IntroSkip" -> stringResource(R.string.skip_intro)
                                "Buffer" -> stringResource(R.string.buffer_settings)
                                else -> title
                            }

                            val isSelected = selectedTab == index
                            Surface(
                                onClick = { selectedTab = index },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .onFocusChanged {
                                        if (it.isFocused && !onlineSubtitleSearchActive) selectedTab = index
                                    },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    ) else Color.Transparent,
                                    contentColor = if (isSelected) TvMaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text(
                                    text = displayTitle,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    ),
                                    style = TvMaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content
                    Box(modifier = Modifier.weight(1f)) {
                        val currentTabName = getTabContent(selectedTab)
                        // Info 页展示当前选中的片源信息（多版本切换后随之更新）
                        val currentSource = mediaSources.firstOrNull { it.id == currentMediaSourceId }
                            ?: mediaSources.firstOrNull()
                        when (currentTabName) {
                            "Info" -> InfoTab(mediaInfo, media, isFavorite, onToggleFavorite, currentSource)
                            "Episodes" -> EpisodesTab(
                                mediaInfo,
                                serverUrl,
                                repository,
                                onDismiss,
                                onNavigateToPlayer
                            )

                            "Version" -> VersionTab(
                                mediaSources,
                                currentMediaSourceId,
                                onSourceSelect
                            )

                            "External" -> ExternalPlayerTab(
                                media,
                                mediaInfo,
                                currentMediaSourceId,
                                serverUrl,
                                repository,
                                onLaunchExternalPlayer
                            )

                            "Subtitles" -> SubtitlesTab(
                                subtitleTracks,
                                selectedSubtitleIndex,
                                onSubtitleSelect,
                                subtitleBottomPadding,
                                onSubtitleBottomPaddingChange,
                                subtitleTimeOffsetMs,
                                onSubtitleTimeOffsetChange,
                                mediaId = mediaInfo.id ?: "",
                                mediaSourceId = currentMediaSourceId,
                                repository = repository,
                                onDismiss = onDismiss,
                                onSubtitleDownloaded = onSubtitleDownloaded,
                                searchActive = onlineSubtitleSearchActive,
                                onSearchActiveChange = { onlineSubtitleSearchActive = it }
                            )

                            "Audio" -> AudioTab(audioTracks, selectedAudioIndex, onAudioSelect)
                            "Speed" -> SpeedTab(playbackSpeed, onPlaybackSpeedChange)
                            "Mode" -> PlayModeTab(playMode, onPlayModeChange, isSeries)
                            "Correction" -> PlaybackCorrectionTab(
                                playbackCorrection,
                                onPlaybackCorrectionChange
                            )
                            "IntroSkip" -> IntroSkipTab(
                                autoSkipIntro,
                                onAutoSkipIntroChange,
                                mediaInfo,
                                repository
                            )
                            "Buffer" -> BufferSettingsTab(
                                minBufferMs = minBufferMs,
                                onMinBufferMsChange = onMinBufferMsChange,
                                maxBufferMs = maxBufferMs,
                                onMaxBufferMsChange = onMaxBufferMsChange,
                                playbackBufferMs = playbackBufferMs,
                                onPlaybackBufferMsChange = onPlaybackBufferMsChange,
                                rebufferMs = rebufferMs,
                                onRebufferMsChange = onRebufferMsChange,
                                bufferSizeBytes = bufferSizeBytes,
                                onBufferSizeBytesChange = onBufferSizeBytesChange,
                                onResetDefault = onResetBufferDefaults,
                                onReplay = {
                                    onDismiss()
                                    onRePlayer(mediaInfo,position)
                                }
                            )

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoTab(
    mediaInfo: BaseItemDto,
    media: MediaDto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    currentSource: MediaSourceInfoDto? = null
) {

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(horizontal = 50.dp)
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        // 1. 标题栏
        Text(
            text = mediaInfo.seriesName ?: mediaInfo.name ?: "",
            style = TvMaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        // 2. 季集详细信息 (如果是电视剧)
        val parentIndexNumber = mediaInfo.parentIndexNumber
        val indexNumber = mediaInfo.indexNumber
        if (parentIndexNumber != null && indexNumber != null) {
            Text(
                text = "S${parentIndexNumber} E${indexNumber} · ${mediaInfo.name ?: ""}",
                style = TvMaterialTheme.typography.titleMedium,
                color = TvMaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. 媒体元数据标签组
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val year = mediaInfo.productionYear?.toString() ?: mediaInfo.premiereDate?.take(4) ?: ""
            if (year.isNotEmpty()) MetaBadge(text = year)

            // 优先取当前选中的片源（多版本切换后保持一致），否则取第一个
            val source = currentSource ?: media.mediaSources?.firstOrNull()
            val videoStream = source?.mediaStreams?.find { it.type == "Video" }

            val resolution = when {
                (videoStream?.width ?: 0) >= 3840 -> "4K"
                (videoStream?.width ?: 0) >= 1920 -> "1080P"
                (videoStream?.width ?: 0) >= 1280 -> "720P"
                else -> videoStream?.displayTitle?.split(" ")?.firstOrNull() ?: ""
            }
            if (resolution.isNotEmpty()) MetaBadge(text = resolution, containerColor = Color(0xFFE50914))

            val videoRange = videoStream?.videoRange ?: ""
            if (videoRange.isNotEmpty()) MetaBadge(text = videoRange)
            val codec = videoStream?.codec?.uppercase() ?: ""
            if (codec.isNotEmpty()) MetaBadge(text = codec)

            // 多版本影片的大小按当前片源显示，无片源信息时回退 Item 全局大小
            val fileSize = formatFileSize(currentSource?.size ?: mediaInfo.size)
            MetaBadge(text = fileSize)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. 操作按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onToggleFavorite,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                scale = ButtonDefaults.scale(focusedScale = 1.1f),
                colors = ButtonDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.onSecondary.copy(alpha = 0.1f),
                    contentColor = TvMaterialTheme.colorScheme.onSecondary,
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                )
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isFavorite) stringResource(R.string.remove_from_favorite)
                    else stringResource(R.string.add_to_favorite)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            onClick = { },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Gray.copy(alpha = 0.4f),
            ),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.03f
            ),
        ) {
            Text(
                text = mediaInfo.overview ?: "",
                style = TvMaterialTheme.typography.bodyMedium,
                lineHeight = 28.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(50.dp))
    }
}

/**
 * 媒体信息小标签组件
 */
@Composable
fun MetaBadge(
    text: String,
    containerColor: Color = Color.White.copy(alpha = 0.2f)
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = text,
            style = TvMaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}


@Composable
fun EpisodesTab(
    mediaInfo: BaseItemDto,
    serverUrl: String,
    repository: EmbyRepository,
    onDismiss: () -> Unit,
    onNavigateToPlayer: (BaseItemDto) -> Unit
) {
    val scope = rememberCoroutineScope()
    val seriesId = mediaInfo.seriesId
    if (seriesId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.not_series_content), color = Color.White)
        }
        return
    }

    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(seriesId) {
        scope.launch(Dispatchers.IO) {
            try {
                val list = repository.getSeriesList(seriesId)
                episodes = list
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerMenu", "操作失败", e)
            } finally {
                isLoading = false
            }
        }
    }

    if (!isLoading && episodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_episodes_found), color = Color.White)
        }
        return
    }

    // 1. 根据比例计算动态的宽高
    val maxLength = 220.dp
    val maxAspectRatio = episodes.mapNotNull {
        val ratio = it.primaryImageAspectRatio?.toFloat()
        if (ratio == null || ratio == 1f) null else ratio
    }.maxOrNull() ?: 0.666f
    val imgWidth = if (maxAspectRatio >= 1) {
        maxLength
    } else {
        (maxLength.value * maxAspectRatio).dp
    }

    // Auto scroll to current item
    val listState = rememberLazyListState()
    val currentItemId = mediaInfo.id

    LaunchedEffect(episodes) {
        val index = episodes.indexOfFirst { it.id == currentItemId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(episodes) { episode ->
            val isCurrent = episode.id == currentItemId

            BuildItem(
                item = episode,
                imgWidth = imgWidth,
                aspectRatio = maxAspectRatio,
                modifier = Modifier,
                isMyLibrary = false,
                isShowOverview = false,
                isPlaying = isCurrent,
                serverUrl = serverUrl,
                onItemClick = {
                    if (!isCurrent) {
                        onDismiss()
                        onNavigateToPlayer(episode)
                    }
                }
            )
        }
    }
}

@Composable
fun SubtitleTimeOffsetRow(timeOffsetMs: Long, onTimeOffsetChange: (Long) -> Unit) {
    val steps = (timeOffsetMs / SUBTITLE_SYNC_STEP_MS).toInt().coerceIn(-SUBTITLE_SYNC_MAX_STEPS, SUBTITLE_SYNC_MAX_STEPS)
    val isDefault = steps == 0
    val seconds = steps * SUBTITLE_SYNC_STEP_MS / 1000.0
    val valueText = when {
        isDefault -> String.format(Locale.US, "0.00s (%s)", stringResource(R.string.subtitle_position_default))
        seconds > 0 -> String.format(Locale.US, "+%.2fs", seconds)
        else -> String.format(Locale.US, "%.2fs", seconds)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.subtitle_sync),
                style = TvMaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OffsetButton(
                    text = stringResource(R.string.subtitle_earlier),
                    enabled = steps > -SUBTITLE_SYNC_MAX_STEPS
                ) {
                    onTimeOffsetChange((steps - 1) * SUBTITLE_SYNC_STEP_MS)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = valueText,
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(130.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                OffsetButton(
                    text = stringResource(R.string.subtitle_later),
                    enabled = steps < SUBTITLE_SYNC_MAX_STEPS
                ) {
                    onTimeOffsetChange((steps + 1) * SUBTITLE_SYNC_STEP_MS)
                }

                Spacer(modifier = Modifier.width(12.dp))

                OffsetButton(
                    text = stringResource(R.string.reset_default),
                    enabled = !isDefault
                ) {
                    onTimeOffsetChange(0L)
                }
            }
        }
    }
}

@Composable
fun SubtitleOffsetRow(bottomPadding: Float, onBottomPaddingChange: (Float) -> Unit) {
    val steps = Math.round(bottomPadding / 0.02f).coerceIn(0, 50)
    val isDefault = steps == 4
    val percentText = "${steps * 2}%"
    val valueText = if (isDefault) {
        "$percentText (${stringResource(R.string.subtitle_position_default)})"
    } else {
        percentText
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.subtitle_position),
                style = TvMaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OffsetButton(
                    text = stringResource(R.string.subtitle_move_down),
                    enabled = steps > 0
                ) {
                    onBottomPaddingChange((steps - 1) * 0.02f)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = valueText,
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(110.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                OffsetButton(
                    text = stringResource(R.string.subtitle_move_up),
                    enabled = steps < 50
                ) {
                    onBottomPaddingChange((steps + 1) * 0.02f)
                }

                Spacer(modifier = Modifier.width(12.dp))

                OffsetButton(
                    text = stringResource(R.string.reset_default),
                    enabled = !isDefault
                ) {
                    onBottomPaddingChange(0.08f)
                }
            }
        }

        Text(
            text = stringResource(R.string.subtitle_position_hint),
            style = TvMaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun OffsetButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = Color.White,
            focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
            focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = Color.White.copy(alpha = 0.05f),
            disabledContentColor = Color.White.copy(alpha = 0.3f)
        ),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = text,
            style = TvMaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun SubtitlesTab(
    tracks: List<MediaStreamDto>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit,
    timeOffsetMs: Long,
    onTimeOffsetChange: (Long) -> Unit,
    mediaId: String,
    mediaSourceId: String?,
    repository: EmbyRepository,
    onDismiss: () -> Unit,
    onSubtitleDownloaded: (Int, RemoteSubtitleInfoDto) -> Unit,
    searchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {}
) {
    var showOnlineSearch by remember { mutableStateOf(false) }
    var hasOpenedSearch by remember { mutableStateOf(false) }
    val entryFocusRequester = remember { FocusRequester() }

    // 离开字幕 Tab（切到其他 Tab/关闭菜单）时同步重置搜索页标记
    DisposableEffect(Unit) {
        onDispose { onSearchActiveChange(false) }
    }

    // 从在线搜索页返回时，把焦点放回入口，避免 TV 遥控器焦点丢失
    LaunchedEffect(showOnlineSearch) {
        if (!showOnlineSearch && hasOpenedSearch) {
            runCatching { entryFocusRequester.requestFocus() }
        }
    }

    if (showOnlineSearch) {
        OnlineSubtitleSearchPanel(
            mediaId = mediaId,
            mediaSourceId = mediaSourceId,
            repository = repository,
            existingTracks = tracks,
            onBack = { showOnlineSearch = false },
            onDismiss = onDismiss,
            onSubtitleDownloaded = onSubtitleDownloaded
        )
    } else {
        SubtitleTrackList(
            tracks = tracks,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            bottomPadding = bottomPadding,
            onBottomPaddingChange = onBottomPaddingChange,
            timeOffsetMs = timeOffsetMs,
            onTimeOffsetChange = onTimeOffsetChange,
            entryFocusRequester = entryFocusRequester,
            onSearchOnline = {
                hasOpenedSearch = true
                // 必须同步置位：点击后入口行被移出组合，焦点系统会把焦点
                // 恢复到菜单 Tab 行并触发 onFocusChanged，稍晚设置会来不及
                onSearchActiveChange(true)
                showOnlineSearch = true
            },
            onEntryFocused = {
                // 入口行重新拿到焦点后，恢复 Tab 行的焦点跟随切换
                if (!showOnlineSearch) onSearchActiveChange(false)
            }
        )
    }
}

@Composable
private fun SubtitleTrackList(
    tracks: List<MediaStreamDto>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit,
    timeOffsetMs: Long,
    onTimeOffsetChange: (Long) -> Unit,
    entryFocusRequester: FocusRequester,
    onSearchOnline: () -> Unit,
    onEntryFocused: () -> Unit = {}
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        item {
            OnlineSubtitleEntryRow(
                focusRequester = entryFocusRequester,
                onFocusGained = onEntryFocused,
                onClick = onSearchOnline
            )
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            SubtitleOffsetRow(bottomPadding, onBottomPaddingChange)
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            SubtitleTimeOffsetRow(timeOffsetMs, onTimeOffsetChange)
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            val isSelected = selectedIndex == -1
            Surface(
                onClick = { onSelect(-1) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.disable_subtitles))
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
        items(tracks) { track ->
            val index = (track.index) ?: -1
            val title =
                (track.displayTitle) ?: (track.language) ?: "Unknown"

            val isSelected = selectedIndex == index

            Surface(
                onClick = { onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

/**
 * “搜索在线字幕”入口行
 */
@Composable
private fun OnlineSubtitleEntryRow(
    focusRequester: FocusRequester,
    onFocusGained: () -> Unit = {},
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            contentColor = Color.White,
            focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
            focusedContentColor = TvMaterialTheme.colorScheme.onSecondary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusGained() }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_online_subtitles),
                    style = TvMaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.search_online_subtitles_hint),
                    style = TvMaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 在线字幕搜索页：语言选择、搜索、结果下载。
 *
 * 权限错误、网络错误和空结果都显示在结果区域，不使用弹窗/Toast；
 * 只有下载成功并关闭菜单时由播放页给出成功提示。
 */
@Composable
private fun OnlineSubtitleSearchPanel(
    mediaId: String,
    mediaSourceId: String?,
    repository: EmbyRepository,
    existingTracks: List<MediaStreamDto>,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSubtitleDownloaded: (Int, RemoteSubtitleInfoDto) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var searchState by remember {
        mutableStateOf<SubtitleSearchUiState>(SubtitleSearchUiState.ResolvingLanguage)
    }
    var downloadErrorMessage by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    // 挂在搜索页第一个可见元素（返回按钮）上，进入页面时立即抢回焦点
    val initialFocusRequester = remember { FocusRequester() }
    // 挂在第一条搜索结果上：下载失败后焦点若被系统夺走（跳到 Tab 行），请求回到结果区
    val firstResultFocusRequester = remember { FocusRequester() }

    // 下载结束但仍停留在搜索页（即下载失败）时，确保焦点回到结果列表
    LaunchedEffect(downloadingId, downloadErrorMessage) {
        if (downloadingId == null && downloadErrorMessage != null) {
            runCatching { firstResultFocusRequester.requestFocus() }
        }
    }

    suspend fun runSearch(language: String) {
        searchState = SubtitleSearchUiState.Searching
        downloadErrorMessage = null
        try {
            val items = repository.searchRemoteSubtitles(mediaId, language, mediaSourceId)
            searchState = if (items.isEmpty()) {
                SubtitleSearchUiState.Empty(language)
            } else {
                SubtitleSearchUiState.Success(items)
            }
        } catch (e: Exception) {
            ErrorHandler.logError("PlayerMenu", "搜索在线字幕失败", e)
            searchState = subtitleSearchErrorState(e, context)
        }
    }

    fun download(info: RemoteSubtitleInfoDto) {
        val subtitleId = info.id ?: return
        if (downloadingId != null) return
        if (!isSupportedRemoteSubtitleFormat(info.format)) {
            downloadErrorMessage = context.getString(R.string.subtitle_format_unsupported)
            return
        }
        scope.launch {
            downloadingId = subtitleId
            downloadErrorMessage = null
            try {
                var newIndex = repository.downloadRemoteSubtitle(
                    itemId = mediaId,
                    subtitleId = subtitleId,
                    mediaSourceId = mediaSourceId
                )
                if (newIndex == null) {
                    // 部分 Emby 版本下载成功但返回空 body（无 NewIndex），
                    // 重新拉取媒体流并与下载前对比，找出新增的外挂字幕
                    newIndex = resolveNewSubtitleIndex(
                        repository = repository,
                        itemId = mediaId,
                        mediaSourceId = mediaSourceId,
                        existingTracks = existingTracks,
                        info = info
                    )
                }
                if (newIndex != null) {
                    onSubtitleDownloaded(newIndex, info)
                    onDismiss()
                } else {
                    downloadErrorMessage = context.getString(R.string.subtitle_download_missing_index)
                }
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerMenu", "下载在线字幕失败", e)
                downloadErrorMessage = subtitleDownloadErrorMessage(e, context)
            } finally {
                downloadingId = null
            }
        }
    }

    LaunchedEffect(Unit) {
        // 等一帧让焦点系统的恢复流程先结束（避免焦点被 Tab 行抢走导致页面被切走），
        // 再把焦点移动到搜索页内的返回按钮上
        withFrameNanos { }
        runCatching { initialFocusRequester.requestFocus() }
        val language = SubtitleLanguageResolver.resolveDefaultLanguage(
            context = context,
            repository = repository,
            itemId = mediaId
        )
        selectedLanguage = language
        runSearch(language)
    }

    val languageLabels = mapOf(
        "zh-CN" to stringResource(R.string.subtitle_language_zh_cn),
        "zh-TW" to stringResource(R.string.subtitle_language_zh_tw),
        "eng" to stringResource(R.string.subtitle_language_english),
        "jpn" to stringResource(R.string.subtitle_language_japanese),
        "kor" to stringResource(R.string.subtitle_language_korean),
        "fra" to stringResource(R.string.subtitle_language_french),
        "deu" to stringResource(R.string.subtitle_language_german),
        "spa" to stringResource(R.string.subtitle_language_spanish),
        "rus" to stringResource(R.string.subtitle_language_russian),
        "por" to stringResource(R.string.subtitle_language_portuguese)
    )
    val languageCodes = SubtitleLanguageResolver.commonLanguages.toMutableList().apply {
        val current = selectedLanguage
        if (current != null && current !in this) add(current)
    }
    val isBusy = searchState is SubtitleSearchUiState.ResolvingLanguage ||
            searchState is SubtitleSearchUiState.Searching

    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        item {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.focusRequester(initialFocusRequester)
            ) {
                Text(
                    text = "‹ " + stringResource(R.string.back),
                    style = TvMaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Text(
                text = stringResource(R.string.search_online_subtitles),
                style = TvMaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                text = stringResource(R.string.search_online_subtitles_hint),
                style = TvMaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Text(
                text = stringResource(R.string.subtitle_search_language),
                style = TvMaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languageCodes) { code ->
                    val isSelected = selectedLanguage == code
                    Surface(
                        onClick = {
                            selectedLanguage = code
                            downloadErrorMessage = null
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) {
                                TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            } else Color.Transparent,
                            contentColor = Color.White,
                            focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                            focusedContentColor = TvMaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text(
                            text = languageLabels[code] ?: code,
                            style = TvMaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Surface(
                onClick = {
                    selectedLanguage?.let { language ->
                        scope.launch { runSearch(language) }
                    }
                },
                enabled = selectedLanguage != null && !isBusy,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.search), style = TvMaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        downloadErrorMessage?.let { message ->
            item {
                InlineSubtitleErrorCard(
                    title = stringResource(R.string.subtitle_download_failed),
                    message = message
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        when (val state = searchState) {
            SubtitleSearchUiState.ResolvingLanguage -> item {
                SubtitleSearchStatusRow(stringResource(R.string.subtitle_search_resolving_language))
            }

            SubtitleSearchUiState.Searching -> item {
                SubtitleSearchStatusRow(stringResource(R.string.searching) + "…")
            }

            is SubtitleSearchUiState.Error -> item {
                val title = when (state.kind) {
                    SubtitleSearchErrorKind.Permission -> stringResource(R.string.subtitle_search_permission_title)
                    SubtitleSearchErrorKind.LoginExpired -> stringResource(R.string.subtitle_search_login_expired_title)
                    SubtitleSearchErrorKind.NotFound -> stringResource(R.string.subtitle_search_not_found_title)
                    SubtitleSearchErrorKind.Network,
                    SubtitleSearchErrorKind.Unknown -> stringResource(R.string.subtitle_search_failed)
                }
                InlineSubtitleErrorCard(
                    title = title,
                    message = state.message,
                    onRetry = selectedLanguage?.let { language ->
                        { scope.launch { runSearch(language) } }
                    }
                )
            }

            is SubtitleSearchUiState.Empty -> item {
                InlineSubtitleEmptyCard(stringResource(R.string.subtitle_search_empty))
            }

            is SubtitleSearchUiState.Success -> itemsIndexed(state.items) { itemIndex, info ->
                OnlineSubtitleResultRow(
                    info = info,
                    isDownloading = downloadingId == info.id,
                    // 下载中保持可聚焦（禁用会导致焦点被系统夺走跳到 Tab 行），
                    // 重复点击由 download() 内部守卫拦截
                    enabled = info.id != null &&
                            isSupportedRemoteSubtitleFormat(info.format),
                    onClick = { download(info) },
                    modifier = if (itemIndex == 0) {
                        Modifier.focusRequester(firstResultFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }

            SubtitleSearchUiState.Idle -> Unit
        }
    }
}

@Composable
private fun SubtitleSearchStatusRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = TvMaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = TvMaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun InlineSubtitleErrorCard(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        colors = SurfaceDefaults.colors(
            containerColor = Color(0xFF3A1E1E).copy(alpha = 0.85f),
            contentColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = TvMaterialTheme.typography.titleMedium,
                color = Color(0xFFFFB4AB),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = TvMaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 22.sp
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onRetry,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White,
                        focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                        focusedContentColor = TvMaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.retry),
                        style = TvMaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineSubtitleEmptyCard(message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        colors = SurfaceDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            contentColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.subtitle_search_empty_title),
                style = TvMaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = TvMaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun OnlineSubtitleResultRow(
    info: RemoteSubtitleInfoDto,
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val supported = isSupportedRemoteSubtitleFormat(info.format)
    val meta = buildList {
        info.providerName?.takeIf { it.isNotBlank() }?.let { add(it) }
        info.format?.uppercase(Locale.US)?.takeIf { it.isNotBlank() }?.let { add(it) }
        info.author?.takeIf { it.isNotBlank() }?.let { add(it) }
        info.communityRating?.takeIf { it > 0f }?.let {
            add(String.format(Locale.US, "★ %.1f", it))
        }
        info.downloadCount?.takeIf { it > 0 }?.let {
            add(String.format(Locale.US, "↓ %d", it))
        }
    }.joinToString(" · ")

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TvMaterialTheme.colorScheme.onSurface,
            focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
            focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = Color.White.copy(alpha = 0.04f),
            disabledContentColor = Color.White.copy(alpha = 0.55f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name ?: info.providerName ?: stringResource(R.string.unknown),
                    style = TvMaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = TvMaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!supported) {
                    Text(
                        text = stringResource(R.string.subtitle_format_unsupported),
                        style = TvMaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB4AB)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 下载接口未返回 NewIndex 时，通过重新拉取媒体流对比出新增的外挂字幕索引。
 *
 * 匹配策略：
 * 1. 优先按文件路径（外挂字幕必有 Path，且是新文件）找出新增流；
 * 2. 多条新增时按语言与搜索结果名称打分择优；
 * 3. 找不到则返回 null，由调用方提示。
 */
private suspend fun resolveNewSubtitleIndex(
    repository: EmbyRepository,
    itemId: String,
    mediaSourceId: String?,
    existingTracks: List<MediaStreamDto>,
    info: RemoteSubtitleInfoDto
): Int? {
    return try {
        val streams = repository.getItemSubtitleStreams(itemId, mediaSourceId)
        val knownPaths = existingTracks.mapNotNull { it.path }.toSet()
        val knownIndices = existingTracks.mapNotNull { it.index }.toSet()

        // 新增的外挂字幕：索引此前不存在，且优先校验是新文件
        val candidates = streams.filter { stream ->
            stream.index != null && stream.index!! !in knownIndices &&
                    (stream.path == null || stream.path !in knownPaths) &&
                    (stream.isExternal == true || stream.path != null)
        }
        when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates.first().index
            else -> {
                val targetLang = SubtitleLanguageResolver.normalizeLanguage(
                    info.language ?: info.threeLetterISOLanguageName
                )
                val nameKey = info.name?.lowercase(Locale.US)
                candidates.maxByOrNull { stream ->
                    var score = 0
                    val streamLang = SubtitleLanguageResolver.normalizeLanguage(stream.language)
                    if (streamLang == targetLang) score += 2
                    val hay = (stream.displayTitle ?: "") + " " + (stream.path ?: "")
                    if (nameKey != null && hay.lowercase(Locale.US).contains(nameKey)) score += 3
                    if (stream.codec?.contains("srt", true) == true) score += 1
                    score
                }?.index
            }
        }
    } catch (e: Exception) {
        ErrorHandler.logError("PlayerMenu", "定位新增字幕轨失败", e)
        null
    }
}

private enum class SubtitleSearchErrorKind {
    Permission,
    LoginExpired,
    NotFound,
    Network,
    Unknown
}

private sealed interface SubtitleSearchUiState {
    object Idle : SubtitleSearchUiState
    object ResolvingLanguage : SubtitleSearchUiState
    object Searching : SubtitleSearchUiState
    data class Success(val items: List<RemoteSubtitleInfoDto>) : SubtitleSearchUiState
    data class Empty(val language: String) : SubtitleSearchUiState
    data class Error(
        val kind: SubtitleSearchErrorKind,
        val message: String
    ) : SubtitleSearchUiState
}

private val supportedRemoteSubtitleFormats = setOf(
    "srt", "ass", "ssa", "vtt", "subrip", "webvtt", "text"
)

private fun isSupportedRemoteSubtitleFormat(format: String?): Boolean {
    val value = format?.trim()?.lowercase(Locale.US).orEmpty()
    return value.isEmpty() || value in supportedRemoteSubtitleFormats
}

private fun subtitleSearchErrorState(
    e: Exception,
    context: android.content.Context
): SubtitleSearchUiState.Error {
    val message = e.message.orEmpty()
    return when {
        message.contains("HTTP Error: 401") -> SubtitleSearchUiState.Error(
            SubtitleSearchErrorKind.LoginExpired,
            context.getString(R.string.subtitle_search_login_expired)
        )
        message.contains("HTTP Error: 403") -> SubtitleSearchUiState.Error(
            SubtitleSearchErrorKind.Permission,
            context.getString(R.string.subtitle_search_no_permission)
        )
        message.contains("HTTP Error: 404") -> SubtitleSearchUiState.Error(
            SubtitleSearchErrorKind.NotFound,
            context.getString(R.string.subtitle_search_not_found)
        )
        else -> SubtitleSearchUiState.Error(
            SubtitleSearchErrorKind.Network,
            message.ifBlank { context.getString(R.string.subtitle_search_failed) }
        )
    }
}

private fun subtitleDownloadErrorMessage(
    e: Exception,
    context: android.content.Context
): String {
    val message = e.message.orEmpty()
    return when {
        message.contains("HTTP Error: 401") ->
            context.getString(R.string.subtitle_search_login_expired)
        message.contains("HTTP Error: 403") ->
            context.getString(R.string.subtitle_download_no_permission)
        message.contains("HTTP Error: 404") ->
            context.getString(R.string.subtitle_search_not_found)
        message.contains("HTTP Error: 500") ->
            context.getString(R.string.subtitle_download_server_error)
        else -> message.ifBlank { context.getString(R.string.subtitle_download_failed) }
    }
}

@Composable
fun AudioTab(tracks: List<MediaStreamDto>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {

        items(tracks) { track ->
            val index = (track.index) ?: -1
            val title =
                (track.displayTitle) ?: (track.language) ?: "Unknown"
            val isSelected = selectedIndex == index

            Surface(
                onClick = { onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedTab(currentSpeed: Float, onChange: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
    val normalLabel = stringResource(R.string.speed_normal)
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        items(speeds) { speed ->
            val isSelected = currentSpeed == speed
            val label = if (speed == 1.0f) "1.0x ($normalLabel)" else "${speed}x"
            Surface(
                onClick = { onChange(speed) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackCorrectionTab(current: Int, onChange: (Int) -> Unit) {
    val options = listOf(
        0 to stringResource(R.string.off_default),
        1 to stringResource(R.string.playback_correction_server)
    )
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        items(options) { (value, label) ->
            val isSelected = current == value
            Surface(
                onClick = { onChange(value) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = TvMaterialTheme.typography.bodyLarge)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
fun PlayModeTab(current: Int, onChange: (Int) -> Unit, isSeries: Boolean = true) {
    // 剧集：0 列表循环 / 1 单集循环 / 2 不循环
    // 非剧集：仅 1 循环播放 / 2 不循环
    val modes = if (isSeries) listOf(
        stringResource(R.string.loop_list),
        stringResource(R.string.loop_single),
        stringResource(R.string.loop_off)
    ) else listOf(
        stringResource(R.string.loop_playback),
        stringResource(R.string.loop_off)
    )
    val values = if (isSeries) listOf(0, 1, 2) else listOf(1, 2)
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        itemsIndexed(modes) { index, title ->
            val value = values[index]
            val isSelected = current == value
            Surface(
                onClick = { onChange(value) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = TvMaterialTheme.typography.bodyLarge)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
fun IntroSkipTab(
    autoSkipIntro: Boolean,
    onAutoSkipIntroChange: (Boolean) -> Unit,
    mediaInfo: BaseItemDto,
    repository: EmbyRepository
) {
    val isSeries = mediaInfo.type == "Episode" || mediaInfo.seriesId != null

    // 当前剧集所属媒体库的标记片头状态（服务端设置，需管理员）
    var markerLibraries by remember { mutableStateOf<List<MarkerLibraryState>>(emptyList()) }
    var markerStatus by remember { mutableStateOf<MarkerLoadStatus>(MarkerLoadStatus.Loading) }

    LaunchedEffect(Unit) {
        if (!isSeries) return@LaunchedEffect
        try {
            val libs = toMarkerLibraries(repository.getVirtualFolders())
            if (libs.isEmpty()) {
                markerStatus = MarkerLoadStatus.NoLibraries
                return@LaunchedEffect
            }
            // 优先定位当前剧集所属的媒体库（通过祖先链匹配），失败则显示全部电视剧库
            val libsToShow = try {
                val ancestorIds = repository.getItemAncestors(mediaInfo.id ?: "")
                    .mapNotNull { it.id }
                libs.filter { ancestorIds.contains(it.itemId) }.ifEmpty { libs }
            } catch (_: Exception) {
                libs
            }
            markerLibraries = libsToShow
            markerStatus = MarkerLoadStatus.Loaded
        } catch (e: Exception) {
            // 4xx：权限不足（需管理员）；其他：网络/服务异常。菜单里静默降级为提示文字
            markerStatus = if (e.message?.contains("HTTP Error: 4") == true) {
                MarkerLoadStatus.Denied
            } else {
                MarkerLoadStatus.Failed
            }
        }
    }

    val options = listOf(
        false to stringResource(R.string.manual_skip_intro),
        true to stringResource(R.string.auto_skip_intro)
    )

    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        items(options) { (value, label) ->
            MarkerOptionRow(
                label = label,
                isSelected = autoSkipIntro == value,
                onClick = { onAutoSkipIntroChange(value) }
            )
        }

        // ===== 标记片头（服务端，仅电视剧播放时显示） =====
        if (isSeries) {
            item {
                Text(
                    text = stringResource(R.string.intro_marker_section),
                    style = TvMaterialTheme.typography.titleSmall,
                    color = TvMaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.intro_marker_desc),
                    style = TvMaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 18.sp
                )
            }
            when (markerStatus) {
                MarkerLoadStatus.Loading -> item {
                    MarkerStatusText(stringResource(R.string.intro_marker_loading))
                }
                MarkerLoadStatus.Denied -> item {
                    MarkerStatusText(stringResource(R.string.intro_marker_need_admin))
                }
                MarkerLoadStatus.Failed -> item {
                    MarkerStatusText(stringResource(R.string.intro_marker_load_failed))
                }
                MarkerLoadStatus.NoLibraries -> item {
                    MarkerStatusText(stringResource(R.string.intro_marker_no_libraries))
                }
                MarkerLoadStatus.Loaded -> item {
                    IntroMarkerSection(
                        libraries = markerLibraries,
                        onUpdateLibrary = { itemId, update ->
                            markerLibraries = markerLibraries.map {
                                if (it.itemId == itemId) update(it) else it
                            }
                        },
                        showDescription = false,
                        libraryNameLeading = TvMaterialTheme.colorScheme.secondary,
                        optionRow = { label, selected, onClick ->
                            MarkerOptionRow(label = label, isSelected = selected, onClick = onClick)
                        }
                    )
                }
            }
        }
    }
}

/** IntroSkip 页的选项行（手动/自动跳过与标记片头三档共用样式） */
@Composable
private fun MarkerOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
            focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
            containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.5f
            ) else Color.Transparent,
            contentColor = TvMaterialTheme.colorScheme.onSurface
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.03f,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = TvMaterialTheme.typography.bodyLarge)
            if (isSelected) Icon(
                Icons.Default.Check,
                stringResource(R.string.selected),
                tint = LocalContentColor.current
            )
        }
    }
}

/** 标记片头区块的状态提示文字 */
@Composable
private fun MarkerStatusText(text: String) {
    Text(
        text = text,
        style = TvMaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * 生成媒体源（版本）副标题：分辨率 · 编码 · 动态范围 · 体积 · 码率
 */
private fun buildSourceDetail(source: MediaSourceInfoDto): String {
    val video = source.mediaStreams?.firstOrNull { it.type == "Video" }
    val parts = mutableListOf<String>()
    video?.height?.takeIf { it > 0 }?.let { parts.add("${it}p") }
    video?.codec?.uppercase()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    video?.videoRange?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    source.size?.takeIf { it > 0 }?.let { parts.add(formatFileSize(it)) }
    source.bitrate?.takeIf { it > 0 }?.let { parts.add(Utils.formatMbps(it)) }
    return parts.joinToString(" · ")
}

/**
 * 多版本片源切换 Tab：同一影片在服务器上的不同版本（不同分辨率/编码/体积）
 */
@Composable
fun VersionTab(
    sources: List<MediaSourceInfoDto>,
    currentSourceId: String?,
    onSelect: (MediaSourceInfoDto) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        items(sources) { source ->
            val isSelected = source.id != null && source.id == currentSourceId
            val title = source.name?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.unknown)
            val detail = buildSourceDetail(source)

            Surface(
                onClick = { onSelect(source) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = TvMaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                style = TvMaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

/**
 * 外部播放器 Tab：枚举设备上可播放视频的播放器，交给上层统一拉起
 * （pkg 空串=系统选择器；偏好模式下的默认播放器由上层处理）
 */
@Composable
fun ExternalPlayerTab(
    media: MediaDto,
    mediaInfo: BaseItemDto,
    currentMediaSourceId: String?,
    serverUrl: String,
    repository: EmbyRepository,
    onLaunchExternalPlayer: (String?) -> Unit
) {
    val context = LocalContext.current

    // label to packageName（null 表示系统选择器）
    var options by remember { mutableStateOf<List<Pair<String, String?>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        options = ExternalPlayerHelper.queryPlayers(context).map { it.label to it.packageName }
        loaded = true
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        item {
            val label = stringResource(R.string.external_player_system)
            Surface(
                onClick = { onLaunchExternalPlayer("") },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    style = TvMaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        items(options) { (label, pkg) ->
            Surface(
                onClick = { onLaunchExternalPlayer(pkg) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    style = TvMaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (loaded && options.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.external_player_none_found),
                    style = TvMaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
