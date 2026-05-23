package com.xxxx.emby_tv.ui

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.tv.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.model.SessionDto
import com.xxxx.emby_tv.ui.components.PlayerMenu
import com.xxxx.emby_tv.ui.components.PlayerOverlay
import com.xxxx.emby_tv.ui.components.ResumePlaybackButtons
import com.xxxx.emby_tv.ui.components.SkipIntroButton
import com.xxxx.emby_tv.ui.components.getAudioTrack
import com.xxxx.emby_tv.ui.components.getVideoTrack

import com.xxxx.emby_tv.ui.player.PlayerTrackManager
import com.xxxx.emby_tv.ui.player.SubtitleConfigBuilder
import com.xxxx.emby_tv.ui.viewmodel.PlayerViewModel
import com.xxxx.emby_tv.util.ErrorHandler
import com.xxxx.emby_tv.util.IntroSkipHelper
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.data.remote.EmbyApi.CLIENT_VERSION
import com.xxxx.emby_tv.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放器界面（Screen）- 使用 PlayerViewModel
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    mediaId: String,
    playbackPositionTicks: Long = 0L,
    onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {},
    onNavigateToPlayer: (BaseItemDto) -> Unit = {},
    onRePlayer: (BaseItemDto, Long) -> Unit = { _, _ -> },
    onExit: () -> Unit = {},  // 添加退出回调
) {
    val context = LocalContext.current
    val view = LocalView.current

    // 获取 Repository 用于直接访问
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""
    val apiKey = repository.apiKey ?: ""

    // 使用 rememberCoroutineScope() 替代 GlobalScope，确保协程可取消
    val scope = rememberCoroutineScope()

    // 状态变量
    var isPlaying by remember { mutableStateOf(false) }
    var isShowInfo by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<MediaDto>(MediaDto()) }
    var mediaInfo by remember { mutableStateOf<BaseItemDto>(BaseItemDto()) }
    var session by remember { mutableStateOf<SessionDto?>(null) }
    var hasReportedPlaying by remember { mutableStateOf(false) }

    // 收藏状态 - 在播放页层面管理，菜单打开/关闭时保持状态
    var isFavorite by remember { mutableStateOf(false) }

    var subtitleTracks by remember { mutableStateOf<List<MediaStreamDto>>(emptyList()) }
    var selectedSubtitleIndex by remember { mutableStateOf(-99) }
    var audioTracks by remember { mutableStateOf<List<MediaStreamDto>>(emptyList()) }
    var selectedAudioIndex by remember { mutableStateOf(-1) }
    var videoUrl by remember { mutableStateOf<String?>(null) }

    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var buffered by remember { mutableStateOf(0L) }

    var playbackCorrection by remember { mutableStateOf(0) } // 0: off, 1: server transcode
    var playMode by remember { mutableStateOf(0) } // 0: list loop, 1: single loop, 2: no loop
    var endedHandled by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    // 继续播放/从头开始 按钮状态
    var showResumeButtons by remember { mutableStateOf(playbackPositionTicks > 0) }
    var resumeButtonsShownOnce by remember { mutableStateOf(false) }

    // 用于跟踪是否已经尝试过转码回退
    var hasTriedTranscodeFallback by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }

    // 片头跳过相关状态
    val preferencesManager = remember { PreferencesManager(context) }
    var introStartMs by remember { mutableStateOf<Long?>(null) }
    var introEndMs by remember { mutableStateOf<Long?>(null) }
    var showSkipIntroButton by remember { mutableStateOf(false) }
    var autoSkipIntro by remember { mutableStateOf(preferencesManager.autoSkipIntro) }
    var hasAutoSkipped by remember { mutableStateOf(false) }

    // 缓冲设置 - 从 PreferencesManager 加载保存的值，使用 preferencesManager 作为 key 确保重新加载
    var minBufferMs by remember(preferencesManager.minBufferMs) { mutableIntStateOf(preferencesManager.minBufferMs) }
    var maxBufferMs by remember(preferencesManager.maxBufferMs) { mutableIntStateOf(preferencesManager.maxBufferMs) }
    var playbackBufferMs by remember(preferencesManager.playbackBufferMs) { mutableIntStateOf(preferencesManager.playbackBufferMs) }
    var rebufferMs by remember(preferencesManager.rebufferMs) { mutableIntStateOf(preferencesManager.rebufferMs) }
    var bufferSizeBytes by remember(preferencesManager.bufferSizeBytes) { mutableIntStateOf(preferencesManager.bufferSizeBytes) }

    // 收集设备支持的杜比视界profile
    val supportedDvProfiles by playerViewModel.supportedDvProfiles.collectAsState()


    val playbackInfoFailText = stringResource(R.string.failed_get_playback_info)
    var playbackTrigger by remember { mutableStateOf(0) }
    var currentVideoDecoderName by remember { mutableStateOf("") }
    var isTunnelingSafe by remember { mutableStateOf(false) }

    /**
     * 2026 深度探测：查询底层 MediaCodec 列表，确认硬件是否声明了隧道能力
     */
    fun checkActualHardwareTunnelingSupport(): Boolean {
        return try {
            // 检查最常用的两种 4K 格式
            val mimes = listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265)
            val isMinesOK = mimes.any { mime ->
                val decoderInfos = MediaCodecUtil.getDecoderInfos(mime, false, false)
                decoderInfos.any { info ->
                    // 核心判断：硬件必须显式声明 it.tunneling 为 true
                    info.hardwareAccelerated && info.tunneling && !info.softwareOnly
                }
            }
            if (!isMinesOK) return false
            supportedDvProfiles.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // TrackSelector
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            // 1. 获取基础参数
            val baseParameters = buildUponParameters()
                .setPreferredTextLanguage("zh")
                // 开启这个：允许尝试超出硬件声明能力的解码（对杜比 P7 至关重要）
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build()

            // 2. 动态判断隧道模式：仅在电视支持且非音频软解时开启
            isTunnelingSafe = checkActualHardwareTunnelingSupport()

            setParameters(
                baseParameters.buildUpon()
                    .setTunnelingEnabled(isTunnelingSafe)
                    .build()
            )
        }
    }


    val renderersFactory = DefaultRenderersFactory(context).apply {
        // 1. 核心：增加解码器自动降级判断
        setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            // 1. 获取默认解码器（如果是杜比视频，首选通常是 DV 解码器）
            val dvDecoders =
                MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)

            if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                // 2. 获取 HEVC 备选解码器
                val hevcDecoders = MediaCodecUtil.getDecoderInfos(
                    MimeTypes.VIDEO_H265,
                    requiresSecure,
                    requiresTunneling
                )

                // 3. 智能判断排序
                val combined = ArrayList<MediaCodecInfo>()

                // 检查是否有任何一个杜比解码器明确声称支持当前 Level/Profile
                // Media3 会自动过滤掉完全不支持的，但对于 P7，很多电视报的是 "SUPPORT_UNKNOWN" 或功能受限
                // 而且必须要有杜比视界的profile
                val isHardwareLikelyToHandleDV =
                    dvDecoders.any { it.hardwareAccelerated && !it.softwareOnly } && supportedDvProfiles.isNotEmpty()

                if (isHardwareLikelyToHandleDV) {
                    // 高性能电视：杜比优先，HEVC 垫后
                    combined.addAll(dvDecoders)
                    combined.addAll(hevcDecoders)
                } else {
                    // 低性能电视（或 DV 解码器缺失）：HEVC 优先，确保能播
                    combined.addAll(hevcDecoders)
                    combined.addAll(dvDecoders)
                }
                combined
            } else {
                dvDecoders
            }
        }

        // 逻辑：ExoPlayer 会先扫描系统 MediaCodecList。
        // 1. 如果电视硬件报支持该 Codec，优先用硬解。
        // 2. 如果电视硬件不支持（如 TrueHD/DTS），则自动切换到你的 FFmpeg 扩展。
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
//        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // 这确保了渲染器能够处理复杂的字幕样式
        setEnableDecoderFallback(true)
    }

    // 同步检查并修复约束条件 - 在 loadControl 创建之前执行
    if (maxBufferMs < minBufferMs || minBufferMs < rebufferMs || rebufferMs < playbackBufferMs) {
        val defaults = preferencesManager.getBufferDefaults()
        minBufferMs = defaults.minBufferMs
        maxBufferMs = defaults.maxBufferMs
        playbackBufferMs = defaults.playbackBufferMs
        rebufferMs = defaults.rebufferMs
        bufferSizeBytes = defaults.bufferSizeBytes
        preferencesManager.resetBufferDefaults()
    }

    // 缓存控制配置 - 针对 TV 端视频流媒体优化
    val loadControl =
        remember(minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs, bufferSizeBytes) {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryClass = activityManager.memoryClass
            val largeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0

            val targetBytes = when {
                largeHeap || memoryClass >= 512 -> bufferSizeBytes.coerceAtLeast(256 * 1024 * 1024)
                memoryClass >= 256 -> bufferSizeBytes.coerceAtLeast(128 * 1024 * 1024)
                memoryClass >= 128 -> bufferSizeBytes.coerceAtLeast(64 * 1024 * 1024)
                else -> bufferSizeBytes.coerceAtLeast(32 * 1024 * 1024)
            }

            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    minBufferMs,
                    maxBufferMs,
                    playbackBufferMs,
                    rebufferMs
                )
                .setTargetBufferBytes(targetBytes)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

    val bandwidthMeter = remember {
        DefaultBandwidthMeter.getSingletonInstance(context)
    }

    val okHttpClient = HttpClient.getClient(context)

// 创建支持 OkHttp 的工厂
    val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent(EmbyApi.CLIENT + "/" + CLIENT_VERSION)
        .setDefaultRequestProperties(
            mapOf(
                "X-Emby-Client" to EmbyApi.CLIENT,
                "X-Emby-Client-Version" to CLIENT_VERSION,
                "X-Emby-Device-Name" to EmbyApi.DEVICE_NAME,
            )
        )

    val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)

    // ExoPlayer
    val player = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true // Ensure it tries to play immediately
            }
    }

    var isBuffering by remember { mutableStateOf(true) } // Start true assuming we wait for load
    var downloadSpeed by remember { mutableStateOf(0L) }
    var lastSpeedUpdate by remember { mutableStateOf(0L) }

    // Long press state
    var leftKeyDownTime by remember { mutableStateOf(0L) }
    var rightKeyDownTime by remember { mutableStateOf(0L) }

    // 即使暂停播放也不会熄屏
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
        }
    }

    LaunchedEffect(leftKeyDownTime) {
        if (leftKeyDownTime > 0) {
            delay(500)
            while (isActive) {
                val newPos = (player.currentPosition - 30000).coerceAtLeast(0)
                player.seekTo(newPos)
                position = newPos // Update progress bar
                if (!player.isPlaying) {
                    player.play() // Auto-play if paused
                }
                delay(200)
            }
        }
    }

    LaunchedEffect(rightKeyDownTime) {
        if (rightKeyDownTime > 0) {
            delay(500)
            while (isActive) {
                val newPos = (player.currentPosition + 30000).coerceAtMost(duration)
                player.seekTo(newPos)
                position = newPos // Update progress bar
                if (!player.isPlaying) {
                    player.play() // Auto-play if paused
                }
                delay(200)
            }
        }
    }


    // 实现转码回退逻辑
    fun fallbackToServerTranscode() {
        if (hasTriedTranscodeFallback) {
            return
        }

        hasTriedTranscodeFallback = true
        Log.d("PlayerScreen", "开始执行转码回退逻辑")

        // 使用 rememberCoroutineScope() 替代 GlobalScope，确保协程可取消
        scope.launch(Dispatchers.IO) {
            val requestAudioIndex = if (selectedAudioIndex <= -1) null else selectedAudioIndex
            val requestSubtitleIndex =
                if (selectedSubtitleIndex <= -1) null else selectedSubtitleIndex

            try {
                if (media.mediaSources?.firstOrNull()?.transcodingUrl != null) {
                    repository.stopActiveEncodings(
                        media.playSessionId
                    )
                }
                val mediaResult = repository.getPlaybackInfo(
                    mediaId,
                    if (position > 0) position * 10000 else playbackPositionTicks,
                    requestAudioIndex,
                    requestSubtitleIndex,
                    true
                )

                if (mediaResult.mediaSources.isNullOrEmpty()) {
                    Log.e("PlayerScreen", "转码回退失败：无法获取播放信息")
                    return@launch
                }

                // 直接访问mediaSources属性
                val source = mediaResult.mediaSources.firstOrNull()

                // 获取转码URL - 优先使用直链
                val path = source?.directStreamUrl ?: source?.transcodingUrl

                if (path != null) {
                    val newVideoUrl = "${serverUrl}/emby$path"

                    // 切换到主线程更新UI
                    withContext(Dispatchers.Main) {
                        Log.d("PlayerScreen", "转码回退成功，更新视频URL")
                        videoUrl = newVideoUrl

                        media = mediaResult

                        //重要步骤
                        player.stop()

                        // 重新设置播放源
                        val mediaItem = MediaItem.Builder()
                            .setUri(newVideoUrl)
                            .build()

                        player.setMediaItem(mediaItem, position)
                        Log.e(
                            "FFmpegCheck",
                            "FFmpeg Library Available: ${androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()}"
                        )
                        player.prepare()
                        player.playWhenReady = true
                    }
                } else {
                    Log.e("PlayerScreen", "转码回退失败：没有找到转码URL")
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "转码回退过程中出现异常: ${e.message}", e)
            }
        }
    }


    // 加载设置（playbackCorrection 不再持久化，每次进入播放页默认关闭，只对当前视频生效）
    LaunchedEffect(Unit) {
        try {
            val prefs = context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
            playMode = prefs.getInt("play_mode", 0)
        } catch (e: Exception) {
            ErrorHandler.logError("PlayerScreen", "操作失败", e)
        }
    }

    // 提供一个函数供 UI 调用切换（例如点击列表时调用）
    fun changeTrack(audioIndex: Int, subIndex: Int) {
        val needChange =
            (selectedAudioIndex != audioIndex && audioIndex > -1) || (selectedSubtitleIndex != subIndex && subIndex > -1)
        selectedAudioIndex = audioIndex
        selectedSubtitleIndex = subIndex
        hasTriedTranscodeFallback = false
        if (needChange && !(media.mediaSources?.firstOrNull()?.supportsDirectPlay
                ?: false)
        ) playbackTrigger++ // 只有手动修改时，才递增触发器，重启协程
    }

    // 数据加载逻辑
    LaunchedEffect(mediaId, playbackCorrection, playbackTrigger) {
        //  这里的逻辑只会运行一次（初始化时）或者在手动递增 trigger 时运行
        val requestAudioIndex = if (selectedAudioIndex <= -1) null else selectedAudioIndex
        val requestSubtitleIndex = if (selectedSubtitleIndex <= -1) null else selectedSubtitleIndex

        try {
            if (media.mediaSources?.firstOrNull()?.transcodingUrl != null) {
                repository.stopActiveEncodings(
                    media.playSessionId
                )
            }
            val mediaResult = repository.getPlaybackInfo(
                mediaId,
                if (position > 0) position * 10000 else playbackPositionTicks,
                requestAudioIndex,
                requestSubtitleIndex,
                hasTriedTranscodeFallback || playbackCorrection == 1
            )

            if (mediaResult.mediaSources.isNullOrEmpty()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        playbackInfoFailText,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@LaunchedEffect
            }

            // 直接使用 mediaResult 对象，赋值给状态
            media = mediaResult
            val mediaInfoResult = repository.getMediaInfo(mediaId)
            mediaInfo = mediaInfoResult

            // 更新收藏状态
            isFavorite = mediaInfoResult.userData?.isFavorite == true

            val source = mediaResult.mediaSources.firstOrNull()
            val streams = source?.mediaStreams ?: emptyList()
            // 检测片头信息
            val introRange = IntroSkipHelper.detectIntroRange(source?.chapters)
            if (introRange != null) {
                introStartMs = introRange.first
                introEndMs = introRange.second
                hasAutoSkipped = false // 重置自动跳过标志
            } else {
                introStartMs = null
                introEndMs = null
                showSkipIntroButton = false
                hasAutoSkipped = false
            }

            subtitleTracks = streams.filter { s ->
                s.type == "Subtitle"
            }

            audioTracks = streams.filter { s ->
                s.type == "Audio"
            }

            if (selectedAudioIndex == -1) {
                selectedAudioIndex = source?.defaultAudioStreamIndex ?: -1
            }
            if (selectedSubtitleIndex == -99) {
                selectedSubtitleIndex = source?.defaultSubtitleStreamIndex ?: -1
            }

            // 构建 URL (参考 Flutter 逻辑)
            var path = source?.directStreamUrl

            // 如果强制转码(correction=1) 或者 没有直链(path=null)，则尝试使用转码链接
            if (playbackCorrection == 1 || path == null) {
                val transcodeUrl = source?.transcodingUrl
                if (transcodeUrl != null) {
                    path = transcodeUrl
                }
            }


            videoUrl = if (path != null) "${serverUrl}/emby$path" else null
            hasReportedPlaying = false
        } catch (e: Throwable) {
            Log.e("PlayerScreen", "加载播放信息失败", e)
        }
    }

    // 设置 MediaItem 和 字幕
    LaunchedEffect(videoUrl) {
        if (videoUrl != null) {
            val source = media.mediaSources?.firstOrNull()
            val mediaSourceId = source?.id ?: ""

            // 使用 SubtitleConfigBuilder 构建字幕配置
            val subtitleConfigs = SubtitleConfigBuilder.buildSubtitleConfigs(
                subtitleTracks = subtitleTracks,
                serverUrl = serverUrl,
                mediaId = mediaId,
                mediaSourceId = mediaSourceId,
                apiKey = apiKey,
                selectedSubtitleIndex = selectedSubtitleIndex
            )

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(videoUrl)
                .setSubtitleConfigurations(subtitleConfigs)

           // 计算起始位置：优先级为播放进度 > 参数传入位置 > 0
            val startPositionMs = if (position > 0) {
                position
            } else if (playbackPositionTicks > 0) {
                playbackPositionTicks / 10000
            } else {
                0L
            }

            val mediaItem = mediaItemBuilder.build()

            // 直接在setMediaItem中设置跳转位置（Media3 API支持）
            player.setMediaItem(mediaItem, startPositionMs)

            if (startPositionMs > 0) {
                Log.d("Player", "Setting initial position via setMediaItem: $startPositionMs ms")
            }

            player.prepare()
            player.playWhenReady = true
        }
    }

    // 更新选中字幕 - 使用 PlayerTrackManager
    LaunchedEffect(selectedSubtitleIndex, subtitleTracks, currentTracks) {
        PlayerTrackManager.selectSubtitle(
            player,
            subtitleTracks,
            selectedSubtitleIndex,
            currentTracks
        )
    }

    // 更新选中音频 - 使用 PlayerTrackManager
    LaunchedEffect(selectedAudioIndex, audioTracks, currentTracks) {
        PlayerTrackManager.selectAudio(player, audioTracks, selectedAudioIndex, currentTracks)
    }


    // Handle Ended Logic for List Loop
    LaunchedEffect(endedHandled) {
        if (endedHandled && playMode == 0) {
            val seriesId = mediaInfo.seriesId
            if (seriesId != null) {
                try {
                    val list = repository.getSeriesList(seriesId)

                    @Suppress("UNCHECKED_CAST")
                    val episodes = list

                    val currentId = mediaId
                    val currentIndex = episodes.indexOfFirst { it.id == currentId }

                    if (currentIndex >= 0 && currentIndex < episodes.size - 1) {
                        val nextEpisode = episodes[currentIndex + 1]
                        onNavigateToPlayer(nextEpisode)
                    } else {

                    }
                } catch (e: Exception) {
                    ErrorHandler.logError("PlayerScreen", "操作失败", e)

                }
            } else {

            }
        }
    }

    // Session Reporting & Updates - 使用 playerViewModel 定期报告进度
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var tickCount = 0
            while (isActive) {
                try {
                    // 每 9 秒报告一次进度
                    if (tickCount % 9 == 0) {
                        playerViewModel.reportProgress(
                            mediaId = mediaId,
                            media = media,
                            position = position,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            selectedAudioIndex = selectedAudioIndex
                        )
                    }
                    tickCount++
                } catch (e: Exception) {
                    ErrorHandler.logError("PlayerScreen", "报告进度失败", e)
                }
                delay(1000)
            }
        }
    }

    // Initial Playing Report - 使用 playerViewModel 报告播放开始
    LaunchedEffect(isPlaying, videoUrl) {
        if (isPlaying && videoUrl != null && !hasReportedPlaying) {
            try {
                playerViewModel.reportPlaying(
                    mediaId = mediaId,
                    media = media,
                    position = position,
                    selectedSubtitleIndex = selectedSubtitleIndex,
                    selectedAudioIndex = selectedAudioIndex
                )
                hasReportedPlaying = true
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerScreen", "报告播放开始失败", e)
            }
        }
    }

    // 自动隐藏继续播放按钮（3秒后）
    LaunchedEffect(showResumeButtons) {
        if (showResumeButtons && !resumeButtonsShownOnce) {
            resumeButtonsShownOnce = true
            delay(3000)
            showResumeButtons = false
        }
    }

    // Load session once after reporting playing
    // Load session once after reporting playing (with retry)
    LaunchedEffect(hasReportedPlaying, media.playSessionId) {
        if (!hasReportedPlaying) return@LaunchedEffect

        val currentId = media.playSessionId ?: return@LaunchedEffect
        val retries = 4

        repeat(retries) { attempt ->
            try {
                // Delay waiting for server to process playing report (500ms initial + retry interval)
                delay(1200)

                val sessions = repository.getPlayingSessions()
                val source =
                    media.mediaSources?.firstOrNull()
                val mediaSourceId = source?.id

                val found = sessions
                    .find { s ->
                        // Match by NowPlayingItem.Id
                        val nowPlayingId = s.nowPlayingItem?.id
                        if (nowPlayingId == mediaId || (mediaSourceId != null && nowPlayingId == mediaSourceId)) {
                            return@find true
                        }
                        false
                    }

                if (found != null) {
                    // 检查是否需要继续等待转码信息
                    val playMethod = found.playState?.playMethod
                    val transcodingInfo = found.transcodingInfo

                    if (playMethod == "Transcode" && transcodingInfo == null) {
                        android.util.Log.d(
                            "PlayerSession",
                            "Found session but transcodingInfo is null for Transcode mode, retrying..."
                        )
                        session = null  // 清空，继续重试
                    } else {

                        session = found
                        android.util.Log.d(
                            "PlayerSession",
                            "Matched session on attempt ${5 - retries}, playMethod=$playMethod"
                        )

                        return@LaunchedEffect
                    }
                }


            } catch (e: Exception) {
                ErrorHandler.logError("PlayerScreen", "操作失败", e)

            }
            // 如果运行到这里，说明本次没找到，repeat 会继续下一次
            if (attempt == retries - 1) {
                Log.w("Session", "达到最大重试次数，未能找到 Session: $currentId")
            }
        }
    }


    // 使用 LaunchedEffect 监听 player 实例
    // 当 player 变化或 Composable 销毁时，这个协程会自动重启或取消
    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                // 1. 更新当前播放位置
                val rawPosition = player.currentPosition
                position = if (rawPosition > 0) rawPosition else 0L

                // 2. 更新缓存进度
                buffered = player.bufferedPosition.coerceAtLeast(0L)

            }

            // 每 800 毫秒更新一次进度
            delay(800)
        }
    }

    // 片头检测和自动跳过逻辑
    LaunchedEffect(isPlaying, position, introStartMs, introEndMs, autoSkipIntro, hasAutoSkipped) {
        if (introStartMs != null && introEndMs != null && isPlaying) {
            // 检查是否在片头范围内
            val inIntroRange = position >= introStartMs!! && position < introEndMs!!

            if (inIntroRange) {
                // 如果启用了自动跳过且还未跳过，则自动跳过
                if (autoSkipIntro && !hasAutoSkipped) {
                    player.seekTo(introEndMs!!)
                    hasAutoSkipped = true
                    showSkipIntroButton = false
                } else if (!autoSkipIntro) {
                    // 如果未启用自动跳过，显示跳过按钮
                    showSkipIntroButton = true
                }
            } else {
                // 不在片头范围内，隐藏跳过按钮
                if (position >= introEndMs!!) {
                    showSkipIntroButton = false
                }
            }
        } else {
            showSkipIntroButton = false
        }
    }

    // 播放器监听
    DisposableEffect(player) {
        player.addAnalyticsListener(object : AnalyticsListener {

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedMs: Long,
                initializationDurationMs: Long,
            ) {
                currentVideoDecoderName = decoderName
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedMs: Long,
                initializationDurationMs: Long,
            ) {
                // 例如 libffmpeg (如果用了FFmpeg扩展) 或 c2.android.ac3.decoder
//                android.util.Log.d("DecoderInfo", "音频解码器已初始化: $decoderName")
            }
        })

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackStateChanged(playing)
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                    val durationMs = player.duration // 此时时长已可用
                    if (durationMs != C.TIME_UNSET) {
                        // 执行逻辑
                        duration = durationMs
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING) {
                    isBuffering = true
                } else if (state == Player.STATE_READY) {
                    isBuffering = false
                    // 在STATE_READY时获取准确时长
                    val rawDuration = player.duration
                    if (rawDuration > 0) {
                        duration = rawDuration
                        Log.d("Player", "Duration updated from READY state: $duration ms")
                    }
                }


                if (state == Player.STATE_ENDED) {
                    isBuffering = false
                    onPlaybackStateChanged(false)
                    // Handle loop logic
                    if (playMode == 1) { // Single Loop
                        player.seekTo(0)
                        player.play()
                    } else if (playMode == 0) { // List Loop

                        val seriesId = mediaInfo.seriesId

                        if (seriesId != null) {
                            // Trigger next episode logic
                            // ...
                            if (!endedHandled) {
                                endedHandled = true
                                // Trigger logic handled in LaunchedEffect
                            }
                        } else {
                            // 播放结束，发送停止报告
                            playerViewModel.reportStopped(
                                mediaId = mediaId,
                                media = media,
                                position = position,
                                selectedSubtitleIndex = selectedSubtitleIndex,
                                selectedAudioIndex = selectedAudioIndex
                            )

                        }
                    } else {
                        // 播放结束，发送停止报告
                        playerViewModel.reportStopped(
                            mediaId = mediaId,
                            media = media,
                            position = position,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            selectedAudioIndex = selectedAudioIndex
                        )

                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerScreen", "播放器错误: ${error.message}", error)
                val causeMsg = error.cause?.message ?: ""
                if (causeMsg.contains("SOCKS", ignoreCase = true) ||
                    causeMsg.contains("Proxy", ignoreCase = true) ||
                    causeMsg.contains("Connection refused", ignoreCase = true) ||
                    causeMsg.contains("Malformed reply", ignoreCase = true)
                ) {
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.error_proxy_connection),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
                fallbackToServerTranscode()
            }
        }

        val bandwidthListener = object : BandwidthMeter.EventListener {
            override fun onBandwidthSample(
                elapsedMs: Int,
                bytesTransferred: Long,
                bitrateEstimate: Long,
            ) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpeedUpdate >= 1000) {
                    downloadSpeed = bitrateEstimate
                    lastSpeedUpdate = currentTime
                }
            }
        }

        bandwidthMeter.addEventListener(
            Handler(Looper.getMainLooper()),
            bandwidthListener
        )
        player.addListener(listener)

        onDispose {
            bandwidthMeter.removeEventListener(bandwidthListener)
            // 发送停止报告
            playerViewModel.reportStopped(
                mediaId = mediaId,
                media = media,
                position = position,
                selectedSubtitleIndex = selectedSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex
            )
            player.stop()
            player.removeListener(listener)
            player.setVideoSurface(null)
            player.release()
        }
    }

    // 监听按键显示菜单
    val focusRequester = remember { FocusRequester() }

    // UI 结构 - 最外层纯黑背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = true, onClick = {
                    if (isPlaying) {
                        player.pause()
                        isShowInfo = true
                    } else {
                        player.play()
                        isShowInfo = false
                    }
                })
                .onKeyEvent { event ->
                    // 如果 Resume 按钮正在显示，让按钮处理焦点，不拦截按键
                    if (showResumeButtons && playbackPositionTicks > 0) {
                        return@onKeyEvent false
                    }

                    if (event.key == Key.DirectionLeft) {
                        if (event.type == KeyEventType.KeyDown) {
                            if (leftKeyDownTime == 0L) {
                                leftKeyDownTime = System.currentTimeMillis()
                                isShowInfo = true
                            }
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (leftKeyDownTime > 0) {
                                if (System.currentTimeMillis() - leftKeyDownTime < 500) {
                                    player.seekBack()
                                }
                                leftKeyDownTime = 0L
                            }
                        }
                        return@onKeyEvent true
                    }
                    if (event.key == Key.DirectionRight) {
                        if (event.type == KeyEventType.KeyDown) {
                            if (rightKeyDownTime == 0L) {
                                rightKeyDownTime = System.currentTimeMillis()
                                isShowInfo = true
                            }
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (rightKeyDownTime > 0) {
                                if (System.currentTimeMillis() - rightKeyDownTime < 500) {
                                    player.seekForward()
                                }
                                rightKeyDownTime = 0L
                            }
                        }
                        return@onKeyEvent true
                    }

                    if (event.type == KeyEventType.KeyDown) {
                        if (event.key == Key.DirectionDown || event.key == Key.Menu) {
                            showMenu = true
                            return@onKeyEvent true
                        }
                        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (showMenu) return@onKeyEvent false
                            if (isPlaying) {
                                player.pause()
                                isShowInfo = true
                            } else {
                                player.play()
                                isShowInfo = false
                            }
                            return@onKeyEvent true
                        }
                        // Show info on any key
                        isShowInfo = true
                        // Hide info after delay?
                    }
                    false
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            // 1. Video Layer
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false // Use custom overlay
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // --- 核心配置：还原作者原定样式 ---
                        subtitleView?.apply {
                            // 1. 允许应用字幕文件内置的样式（颜色、字体、定位等）
                            setApplyEmbeddedStyles(true)

                            // 2. 允许应用字幕文件内置的字体大小
                            setApplyEmbeddedFontSizes(true)

                            // 3. 关键：将渲染模式设为 BITMAP（位图模式）
                            // 只有在这种模式下，复杂的 ASS/SSA 特效和 PGS 图形字幕才能精准还原
                            // 默认的层次模式（VIEW_TYPE_TEXT）会丢失很多高级特效
                            // VIEW_TYPE_CANVAS = 2
                            setViewType(SubtitleView.VIEW_TYPE_CANVAS)

                            // 4. 强制设置透明背景，避免默认样式的黑色背景遮挡
                            val transparentStyle = CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_NONE,
                                android.graphics.Color.WHITE,
                                null
                            )
                            setStyle(transparentStyle)
                        }
                    }
                },
                update = { view ->
                    view.player = player
                },
//                modifier = Modifier.fillMaxSize()
            )


            // 2. Full Info Overlay Layer (only when isShowInfo)
            if (isShowInfo && !isPlaying) {
                PlayerOverlay(
                    isTunnelingSafe = isTunnelingSafe,
                    mediaInfo = mediaInfo,
                    mediaSource = media.mediaSources?.firstOrNull(),
                    session = session,
                    videoStream = getVideoTrack(media),
                    audioStream = getAudioTrack(media, selectedAudioIndex),
                    position = position,
                    duration = duration,
                    buffered = buffered,
                    isPlaying = isPlaying,
                    player = player,
                    isBuffering = isBuffering,
                    downloadSpeed = downloadSpeed,
                    supportedDvProfiles = supportedDvProfiles,
                    currentVideoDecoderName = currentVideoDecoderName
                )

            }

            // 3. Simple Pause/Loading Overlay (no info)
            if ((!isPlaying || isBuffering) && !isShowInfo) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = Utils.formatBandwidth(downloadSpeed),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Play Icon
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Paused",
                            modifier = Modifier.size(100.dp),
                            tint = Color.White
                        )
                    }
                }

                if (!isBuffering) {
                    // Menu Hint at bottom
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 48.dp)
                        ) {


                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.press_menu_down_to_show_menu),
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 4. Resume Buttons (从头开始 / 继续播放)
            if (showResumeButtons && playbackPositionTicks > 0) {
                ResumePlaybackButtons(
                    countdownSeconds = 3,
                    onPlayFromStart = {
                        showResumeButtons = false
                        resumeButtonsShownOnce = true
                        player.seekTo(0)
                        player.play()
                    },
                    onContinue = {
                        showResumeButtons = false
                        resumeButtonsShownOnce = true
                    },
                    onTimeout = {
                        showResumeButtons = false
                        resumeButtonsShownOnce = true
                    }
                )
            }

            // 4.5. Skip Intro Button (跳过片头)
            if (showSkipIntroButton && introEndMs != null && !showResumeButtons) {
                SkipIntroButton(
                    introEndMs = introEndMs!!,
                    onSkip = {
                        showSkipIntroButton = false
                        player.seekTo(introEndMs!!)
                    },
                    onTimeout = {
                        showSkipIntroButton = false
                    },
                    countdownSeconds = 5
                )
            }

            // 5. Menu Dialog
            if (showMenu) {
                PlayerMenu(
                    onDismiss = { showMenu = false },
                    media = media,
                    mediaInfo = mediaInfo,
                    subtitleTracks = subtitleTracks,
                    selectedSubtitleIndex = selectedSubtitleIndex,
                    onSubtitleSelect = { index ->
                        changeTrack(selectedAudioIndex, index)
                    },
                    audioTracks = audioTracks,
                    selectedAudioIndex = selectedAudioIndex,
                    onAudioSelect = { index -> changeTrack(index, selectedSubtitleIndex) },
                    playbackCorrection = playbackCorrection,
                    onPlaybackCorrectionChange = {
                        // 只对当前播放视频生效，不持久化保存
                        playbackCorrection = it
                    },
                    playMode = playMode,
                    onPlayModeChange = {
                        playMode = it
                        val prefs =
                            context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putInt("play_mode", it).apply()
                    },
                    autoSkipIntro = autoSkipIntro,
                    onAutoSkipIntroChange = {
                        autoSkipIntro = it
                        preferencesManager.autoSkipIntro = it
                    },
                    minBufferMs = minBufferMs,
                    onMinBufferMsChange = {
                        minBufferMs = it
                        if (it < rebufferMs) {
                            rebufferMs = it
                            preferencesManager.rebufferMs = it
                            if (it < playbackBufferMs) {
                                playbackBufferMs = it
                                preferencesManager.playbackBufferMs = it
                            }
                        }
                        preferencesManager.minBufferMs = it
                    },
                    maxBufferMs = maxBufferMs,
                    onMaxBufferMsChange = {
                        maxBufferMs = it
                        if (it < minBufferMs) {
                            minBufferMs = it
                            preferencesManager.minBufferMs = it
                        }
                        preferencesManager.maxBufferMs = it
                    },
                    playbackBufferMs = playbackBufferMs,
                    onPlaybackBufferMsChange = {
                        playbackBufferMs = it
                        if (it > rebufferMs) {
                            rebufferMs = it
                            preferencesManager.rebufferMs = it
                            if (it > minBufferMs) {
                                minBufferMs = it
                                preferencesManager.minBufferMs = it
                                if (it > maxBufferMs) {
                                    maxBufferMs = it
                                    preferencesManager.maxBufferMs = it
                                }
                            }
                        }
                        preferencesManager.playbackBufferMs = it
                    },
                    rebufferMs = rebufferMs,
                    onRebufferMsChange = {
                        rebufferMs = it
                        if (it < playbackBufferMs) {
                            playbackBufferMs = it
                            preferencesManager.playbackBufferMs = it
                        }
                        if (it > minBufferMs) {
                            minBufferMs = it
                            preferencesManager.minBufferMs = it
                            if (it > maxBufferMs) {
                                maxBufferMs = it
                                preferencesManager.maxBufferMs = it
                            }
                        }
                        preferencesManager.rebufferMs = it
                    },
                    bufferSizeBytes = bufferSizeBytes,
                    onBufferSizeBytesChange = {
                        bufferSizeBytes = it
                        preferencesManager.bufferSizeBytes = it
                    },
                    onResetBufferDefaults = {
                        val defaults = preferencesManager.getBufferDefaults()
                        minBufferMs = defaults.minBufferMs
                        maxBufferMs = defaults.maxBufferMs
                        playbackBufferMs = defaults.playbackBufferMs
                        rebufferMs = defaults.rebufferMs
                        bufferSizeBytes = defaults.bufferSizeBytes
                        preferencesManager.resetBufferDefaults()
                    },
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        isFavorite = !isFavorite
                        if (mediaInfo.id != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    if (isFavorite) {
                                        repository.addToFavorites(mediaInfo.id!!)
                                    } else {
                                        repository.removeFromFavorites(mediaInfo.id!!)
                                    }
                                } catch (e: Exception) {
                                    ErrorHandler.logError("PlayerScreen", "操作失败", e)
                                }
                            }
                        }
                    },
                    serverUrl = serverUrl,
                    repository = repository,
                    position = position,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onRePlayer = onRePlayer
                )
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    } // 最外层纯黑背景 Box 闭合
}

