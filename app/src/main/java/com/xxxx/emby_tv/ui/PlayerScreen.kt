package com.xxxx.emby_tv.ui

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
import com.xxxx.emby_tv.pickPreferredMediaSource
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
import com.xxxx.emby_tv.ui.player.SubtitleOffsetController
import com.xxxx.emby_tv.ui.viewmodel.PlayerViewModel
import com.xxxx.emby_tv.util.ErrorHandler
import com.xxxx.emby_tv.util.ExternalPlayerHelper
import com.xxxx.emby_tv.util.IntroSkipHelper
import com.xxxx.emby_tv.util.ZidooHelper
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
// 字幕顶部预留比例：UI 100% 映射到实际 88%，保证字幕不出屏
private const val SUBTITLE_TOP_RESERVED_FRACTION = 0.12f

// 音频安全模式（进程级）：
// 部分电视固件在"多声道 PCM → 立体声"系统降混路径上存在 bug，触发后音频 HAL 卡死，
// AudioTrack 创建持续返回 DEAD_OBJECT（有画面无声音、一直转圈加载），只能重启电视恢复。
// 触发过一次故障后，本进程内后续播放器一律直接以立体声安全模式构建，从源头避开该路径；
// 重启 APP 后自动重试原生多声道。
private var audioSafeModeChannels = -1
private const val AUDIO_RECOVERY_TAG = "AudioRecovery"

/**
 * 手写声道混合矩阵。不使用 ChannelMixingMatrix.create()：
 * 它只实现了少数组合（如 6->2、8->2），对 3->2、4->2、5->2、7->2 等会直接抛
 * UnsupportedOperationException（曾导致进入播放页即闪退）。
 *
 * 一维系数数组布局（由 media3 源码确认）：coefficients[inputChannel * outputChannelCount + outputChannel]
 *
 * @param inputChannels 输入声道数 (1..8)
 * @param outputChannels 输出声道数，小于输入声道数时执行降混
 */
private fun buildChannelMixingMatrix(inputChannels: Int, outputChannels: Int): ChannelMixingMatrix {
    val coefficients: FloatArray = if (inputChannels == outputChannels) {
        // 恒等矩阵
        FloatArray(inputChannels * outputChannels).also { c ->
            for (ch in 0 until inputChannels) c[ch * outputChannels + ch] = 1f
        }
    } else if (outputChannels == 2) {
        // 降混到立体声。各声道数的标准 WAV 声道序：
        // 3:FL,FR,FC  4:FL,FR,BL,BR  5:FL,FR,FC,BL,BR  6:FL,FR,FC,LFE,BL,BR
        // 7:FL,FR,FC,LFE,BC,SL,SR  8:FL,FR,FC,LFE,BL,BR,SL,SR
        // 约定（同 ITU-R/系统降混）：FL/FR 直通，FC 双侧 0.707，LFE 丢弃，
        // BL/SL 折叠到 FL，BR/SR 折叠到 FR，BC 双侧 0.707
        val g = 0.70710678f
        val toStereo: Array<Pair<Float, Float>> = when (inputChannels) {
            1 -> arrayOf(1f to 1f)
            3 -> arrayOf(1f to 0f, 0f to 1f, g to g)
            4 -> arrayOf(1f to 0f, 0f to 1f, g to 0f, 0f to g)
            5 -> arrayOf(1f to 0f, 0f to 1f, g to g, g to 0f, 0f to g)
            6 -> arrayOf(1f to 0f, 0f to 1f, g to g, 0f to 0f, g to 0f, 0f to g)
            7 -> arrayOf(1f to 0f, 0f to 1f, g to g, 0f to 0f, g to g, g to 0f, 0f to g)
            8 -> arrayOf(1f to 0f, 0f to 1f, g to g, 0f to 0f, g to 0f, 0f to g, g to 0f, 0f to g)
            else -> Array(inputChannels) { i ->
                when (i) {
                    0 -> 1f to 0f
                    1 -> 0f to 1f
                    2 -> g to g   // FC
                    3 -> 0f to 0f // LFE
                    else -> if (i % 2 == 0) g to 0f else 0f to g
                }
            }
        }
        FloatArray(inputChannels * 2).also { c ->
            toStereo.forEachIndexed { i, pair ->
                c[i * 2] = pair.first      // 输入声道 i → 输出 FL
                c[i * 2 + 1] = pair.second // 输入声道 i → 输出 FR
            }
        }
    } else {
        // 罕见组合（输入 > 输出 >= 3）：前 outputChannels 路恒等，多余声道丢弃
        FloatArray(inputChannels * outputChannels).also { c ->
            for (ch in 0 until outputChannels) c[ch * outputChannels + ch] = 1f
        }
    }
    return ChannelMixingMatrix(inputChannels, outputChannels, coefficients)
}
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

    // 多版本片源切换：null 表示使用服务器返回的默认源
    var selectedMediaSourceId by remember { mutableStateOf<String?>(null) }
    var currentMediaSourceId by remember { mutableStateOf<String?>(null) }
    // 完整片源列表：指定 MediaSourceId 重新请求时服务器只返回单个源，
    // 需保留首次获取的完整列表供菜单切换使用
    var allMediaSources by remember { mutableStateOf<List<com.xxxx.emby_tv.data.model.MediaSourceInfoDto>>(emptyList()) }

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

    // 倍速设置 - 从 PreferencesManager 加载保存的值
    var playbackSpeed by remember(preferencesManager.playbackSpeed) { mutableFloatStateOf(preferencesManager.playbackSpeed) }

    // 字幕位置设置 - 从 PreferencesManager 加载保存的值
    var subtitleBottomPadding by remember(preferencesManager.subtitleBottomPadding) { mutableFloatStateOf(preferencesManager.subtitleBottomPadding) }

    // 字幕时间偏移 - 仅当前播放会话生效
    var subtitleTimeOffsetMs by remember { mutableLongStateOf(0L) }
    val subtitleOffsetController = remember { SubtitleOffsetController() }
    val overlaySubtitleView = remember { mutableStateOf<SubtitleView?>(null) }

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

    // TrackSelector 构建函数（播放器重建时复用；copiedParameters 非空时直接继承原播放器参数）
    fun createTrackSelector(copiedParameters: TrackSelectionParameters? = null): DefaultTrackSelector {
        return DefaultTrackSelector(context).apply {
            if (copiedParameters != null) {
                setParameters(copiedParameters)
            } else {
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
    }


    /**
     * 构建渲染器工厂。
     * 核心：把多声道 PCM 在应用内降混到设备实际支持的输出声道数（如 5.1 → 2.0）再创建 AudioTrack，
     * 避免触发部分电视固件在"系统级多声道降混"路径上的 AudioTrack 创建失败 bug（HAL 卡死需重启电视）。
     *
     * 注意：AudioCapabilities.maxChannelCount 会把直通（DD+/Atmos bitstream）能力也算进去，
     * 在部分电视上严重虚高（实测 TCL 报 10），因此以 AudioDeviceInfo 上各输出设备的
     * 实际 PCM 声道数为准，两者取小。
     */
    fun createRenderersFactory(): DefaultRenderersFactory {
        val reportedMaxChannels =
            AudioCapabilities.getCapabilities(context).maxChannelCount.coerceIn(2, 8)
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devicePcmChannels = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .flatMap { device -> device.channelCounts.toList() }
            .filter { it in 2..8 }
            .distinct()
            .sorted()
        val capableChannels = if (devicePcmChannels.isNotEmpty()) {
            minOf(reportedMaxChannels, devicePcmChannels.last())
        } else {
            reportedMaxChannels
        }
        val targetChannels = if (audioSafeModeChannels > 0) audioSafeModeChannels else capableChannels
        Log.i(
            AUDIO_RECOVERY_TAG,
            "音频输出配置: 上报最大声道数=$reportedMaxChannels, 设备实测PCM声道=$devicePcmChannels, " +
                "可用声道数=$capableChannels, 目标输出声道数=$targetChannels"
        )
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return try {
                    val channelMixer = ChannelMixingAudioProcessor()
                    // 全量注册 1..8 声道矩阵：超出目标的降混，未超出的恒等通过。
                    // 必须保证任何 PCM 输入都有矩阵，否则 media3 会抛 UnhandledAudioFormatException
                    // 并升级为致命播放错误（黑屏）。
                    for (inputChannels in 1..8) {
                        val outputChannels =
                            if (inputChannels > targetChannels) targetChannels else inputChannels
                        channelMixer.putChannelMixingMatrix(
                            buildChannelMixingMatrix(inputChannels, outputChannels)
                        )
                    }
                    DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        // 关闭 AudioTrack 系统变速路径：部分电视 HAL（实测 TCL/MStar）设置成功后
                        // PCM 写入异常，系统会把倍速异步重置回 1.0（表现为倍速失效）。
                        // 强制走 Sonic 软件变速，不依赖 HAL。
                        .setEnableAudioTrackPlaybackParams(false)
                        // 保留 Sonic（倍速支持）并前置声道降混处理器
                        .setAudioProcessors(arrayOf(channelMixer, SonicAudioProcessor()))
                        .build()
                } catch (e: Exception) {
                    // 兜底：任何矩阵/构建异常都回落到原生 sink，绝不影响进入播放页
                    Log.e(AUDIO_RECOVERY_TAG, "构建降混音频管线失败，回落原生输出", e)
                    DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(false)
                        .build()
                }
            }
        }.apply {
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

    // 缓存控制配置 - 针对 TV 端视频流媒体优化（构建函数，播放器重建时复用）
    fun createLoadControl(): DefaultLoadControl {
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

        return DefaultLoadControl.Builder()
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

    // ExoPlayer 构建函数（音频自动恢复时需要整体重建播放器）
    fun buildPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context, createRenderersFactory())
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(createTrackSelector())
            .setLoadControl(createLoadControl())
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

    var player by remember { mutableStateOf(buildPlayer()) }

    // ===== 外部播放器统一拉起 + 进度回传 =====
    // pkg 语义：null=使用偏好设置的默认播放器；""=系统选择器；非空=指定包名
    // Zidoo 设备上走官方控制 API（直链推送 + 断点续播 + 进度轮询回传）
    var zidooMonitorActive by remember { mutableStateOf(false) }

    fun launchZidooPlayer(url: String): Boolean {
        if (zidooMonitorActive) return true
        zidooMonitorActive = true
        try {
            player.pause()
            player.stop()
        } catch (_: Exception) {
        }
        scope.launch(Dispatchers.IO) {
            var lastPosition = position
            try {
                // 上报拉起时进度
                playerViewModel.reportProgress(
                    mediaId = mediaId, media = media, position = position,
                    selectedSubtitleIndex = selectedSubtitleIndex,
                    selectedAudioIndex = selectedAudioIndex, isPaused = true
                )
                if (!ZidooHelper.openFile(url)) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context, context.getString(R.string.external_player_launch_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                // 等待播放器就绪后跳到续播位置
                if (position > 0) {
                    kotlinx.coroutines.delay(3000)
                    ZidooHelper.seekTo(position)
                }
                // 轮询 Zidoo 播放状态，实时回传进度；播放会话结束（用户退出播放器）时上报停止
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    kotlinx.coroutines.delay(5000)
                    val st = ZidooHelper.getPlayStatus() ?: break
                    if (st.positionMs > 0) {
                        lastPosition = st.positionMs
                        playerViewModel.reportProgress(
                            mediaId = mediaId, media = media, position = st.positionMs,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            selectedAudioIndex = selectedAudioIndex
                        )
                    }
                    if (st.durationMs > 0 && st.positionMs >= st.durationMs - 5000) {
                        break // 播放完成
                    }
                }
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerScreen", "Zidoo 播放监控异常", e)
            } finally {
                zidooMonitorActive = false
                if (lastPosition > 0) {
                    playerViewModel.reportStopped(
                        mediaId = mediaId, media = media, position = lastPosition,
                        selectedSubtitleIndex = selectedSubtitleIndex,
                        selectedAudioIndex = selectedAudioIndex
                    )
                }
            }
        }
        return true
    }

    // Intent 方式拉起外部播放器的结果回传（VLC/MX Player 退出时回传播放位置）
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // VLC/MX Player 等退出时会回传播放位置，同步到服务器保证"继续观看"进度正确
        val returnedPos = ExternalPlayerHelper.extractReturnedPosition(result.data)
        if (returnedPos != null && returnedPos > 0) {
            position = returnedPos
            playerViewModel.reportProgress(
                mediaId = mediaId,
                media = media,
                position = returnedPos,
                selectedSubtitleIndex = selectedSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex
            )
        }
    }

    fun launchViaIntent(url: String, targetPkg: String?): Boolean {
        val intent = ExternalPlayerHelper.buildLaunchIntent(url, targetPkg, position)
        // 指定包名在本机不可用时回退系统选择器
        if (!targetPkg.isNullOrBlank()) {
            try {
                if (intent.resolveActivity(context.packageManager) == null) intent.setPackage(null)
            } catch (_: Exception) {
                intent.setPackage(null)
            }
        }
        return try {
            // 暂停内置播放并上报当前进度，交由外部播放器接管（经 launcher 拉起以接收退出位置回传）
            player.pause()
            playerViewModel.reportProgress(
                mediaId = mediaId,
                media = media,
                position = position,
                selectedSubtitleIndex = selectedSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex,
                isPaused = true
            )
            externalPlayerLauncher.launch(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError("PlayerScreen", "拉起外部播放器失败", e)
            scope.launch {
                android.widget.Toast.makeText(
                    context, context.getString(R.string.external_player_launch_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            false
        }
    }

    /**
     * 外部播放器统一入口：
     * Zidoo 设备走官方控制 API（DV 双层直通 + 进度完整回传），其余走 Intent 方式
     */
    fun launchExternalPlayer(pkg: String?): Boolean {
        val url = ExternalPlayerHelper.buildPlayUrl(
            media, mediaId, currentMediaSourceId, serverUrl, apiKey
        )
        if (url == null) {
            scope.launch {
                android.widget.Toast.makeText(
                    context, context.getString(R.string.external_player_launch_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        if (ZidooHelper.isZidooDevice() && ZidooHelper.isApiAvailable()) {
            return launchZidooPlayer(url)
        }
        return launchViaIntent(url, pkg ?: preferencesManager.preferredExternalPlayerPackage)
    }

    // ===== 音频故障自动恢复（全程自动，无用户交互）=====
    // 0: 正常；1: 已用立体声安全模式重建过播放器；2: 已降级为无声播放
    var audioRecoveryStage by remember { mutableIntStateOf(0) }
    // 当前播放器实例是否出现过音频输出错误（播放器重建时自动重置）
    var currentPlayerAudioError by remember { mutableStateOf(false) }

    fun handleAudioSinkFailure() {
        when (audioRecoveryStage) {
            0 -> {
                audioRecoveryStage = 1
                audioSafeModeChannels = 2
                Log.w(AUDIO_RECOVERY_TAG, "音频输出初始化失败，自动切换立体声安全模式并重建播放器")
                val oldPlayer = player
                val resumePositionMs = oldPlayer.currentPosition
                val currentMediaItem = oldPlayer.currentMediaItem
                val trackParameters = oldPlayer.trackSelectionParameters
                oldPlayer.stop()
                val newPlayer = buildPlayer()
                if (currentMediaItem != null) {
                    newPlayer.setMediaItem(currentMediaItem, resumePositionMs)
                }
                newPlayer.trackSelectionParameters = trackParameters
                newPlayer.prepare()
                newPlayer.playWhenReady = true
                player = newPlayer // Compose 自动释放旧实例、重新绑定监听与画面
            }
            1 -> {
                audioRecoveryStage = 2
                Log.w(AUDIO_RECOVERY_TAG, "立体声安全模式仍无音频，自动转为无声播放（视频继续）")
                val p = player
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                scope.launch {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.audio_output_degraded),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
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

    // 应用倍速（player 重建后也需重新应用）
    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    // 字幕时间偏移：接管字幕渲染（内置 subtitleView 已隐藏，由 overlay SubtitleView 显示）
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                subtitleOffsetController.onCues(cueGroup)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                subtitleOffsetController.reset()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            overlaySubtitleView.value?.setCues(
                subtitleOffsetController.cuesFor(player.currentPosition, subtitleTimeOffsetMs)
            )
            delay(50)
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
                    true,
                    selectedMediaSourceId
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
                hasTriedTranscodeFallback || playbackCorrection == 1,
                selectedMediaSourceId
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

            // 加载播放模式（剧集与电影分开保存，电影默认不循环）
            val isSeriesContent =
                mediaInfoResult.type == "Episode" || mediaInfoResult.seriesId != null
            val modePrefs = context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
            playMode = if (isSeriesContent) {
                modePrefs.getInt("play_mode", 0)
            } else {
                modePrefs.getInt("play_mode_movie", 2)
            }

            // 服务器返回完整片源列表时更新缓存（指定 MediaSourceId 时只返回单源，保留原列表）
            if ((mediaResult.mediaSources?.size ?: 0) > 1) {
                allMediaSources = mediaResult.mediaSources!!
            } else if (allMediaSources.isEmpty()) {
                allMediaSources = mediaResult.mediaSources ?: emptyList()
            }

            // 初次进入：自动选择片源。优先级：偏好策略（杜比视界/HDR/流畅优先）> 上次手动选择的片源名（仅默认排序时生效）
            if (selectedMediaSourceId == null && allMediaSources.size > 1) {
                val sourcePref = preferencesManager.sourcePreference
                val preferredName = preferencesManager.preferredMediaSourceName
                val autoPick = if (sourcePref != 3) {
                    // 用户显式配置了策略时，策略优先，忽略历史手动选择记忆
                    pickPreferredMediaSource(allMediaSources, sourcePref)
                } else if (preferredName.isNotBlank()) {
                    allMediaSources.firstOrNull { !it.name.isNullOrBlank() && it.name == preferredName }
                } else null

                val defaultId = mediaResult.mediaSources?.firstOrNull()?.id
                if (autoPick?.id != null && autoPick.id != defaultId) {
                    selectedMediaSourceId = autoPick.id
                    playbackTrigger++
                    return@LaunchedEffect
                }
            }

            // 优先使用用户选择的媒体源（多版本切换），否则取服务器返回的第一个
            val source = mediaResult.mediaSources.firstOrNull { it.id == selectedMediaSourceId }
                ?: mediaResult.mediaSources.firstOrNull()
            currentMediaSourceId = source?.id
            // 切换片源后重新匹配会话信息
            session = null
            hasReportedPlaying = false
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
            // 外部播放器偏好：1=总是 2=仅杜比视界，命中时不启动内置播放器，直接交给外部播放器
            val externalMode = preferencesManager.externalPlayerMode
            if (externalMode == 1 || externalMode == 2) {
                val isDv = externalMode == 2 && ExternalPlayerHelper.isDolbyVision(
                    ExternalPlayerHelper.getVideoStream(media, currentMediaSourceId)
                )
                if (externalMode == 1 || isDv) {
                    val launched = launchExternalPlayer(null)
                    if (launched) {
                        player.stop()
                        isBuffering = false
                        return@LaunchedEffect
                    }
                    // 拉起失败则回落到内置播放器继续播
                }
            }

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
                    selectedAudioIndex = selectedAudioIndex,
                    playbackRate = playbackSpeed
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

    // 缓冲卡死看门狗：长时间缓冲且缓冲进度零增长时，若当前播放器存在音频输出错误则触发自动恢复；
    // 同时把停滞的网速显示归零，避免一直显示冻结的加载速度
    LaunchedEffect(player) {
        var lastBufferedPosition = -1L
        var lastChangeTime = System.currentTimeMillis()
        while (isActive) {
            delay(1000)
            val p = player
            val currentBuffered = p.bufferedPosition
            if (currentBuffered != lastBufferedPosition) {
                lastBufferedPosition = currentBuffered
                lastChangeTime = System.currentTimeMillis()
            } else {
                downloadSpeed = 0
                if (p.playbackState == Player.STATE_BUFFERING && !p.isPlaying &&
                    currentBuffered > 0 &&
                    System.currentTimeMillis() - lastChangeTime >= 15_000 &&
                    currentPlayerAudioError && audioRecoveryStage < 2
                ) {
                    Log.w(AUDIO_RECOVERY_TAG, "检测到音频故障导致的缓冲卡死，触发自动恢复")
                    handleAudioSinkFailure()
                    lastChangeTime = System.currentTimeMillis()
                }
            }
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
        val p = player
        currentPlayerAudioError = false
        p.addAnalyticsListener(object : AnalyticsListener {

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
                Log.i("DecoderInfo", "音频解码器已初始化: $decoderName")
            }

            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkException: Exception,
            ) {
                Log.e(
                    AUDIO_RECOVERY_TAG,
                    "音频输出错误(stage=$audioRecoveryStage): ${audioSinkException.message}"
                )
                currentPlayerAudioError = true
                handleAudioSinkFailure()
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
                    val durationMs = p.duration // 此时时长已可用
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
                    val rawDuration = p.duration
                    if (rawDuration > 0) {
                        duration = rawDuration
                        Log.d("Player", "Duration updated from READY state: $duration ms")
                    }
                } else if (state == Player.STATE_IDLE) {
                    // 出错/停止后的空闲态：清掉缓冲指示，避免永远转圈
                    isBuffering = false
                }


                if (state == Player.STATE_ENDED) {
                    isBuffering = false
                    onPlaybackStateChanged(false)
                    // Handle loop logic
                    if (playMode == 1) { // Single Loop
                        p.seekTo(0)
                        p.play()
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
                                selectedAudioIndex = selectedAudioIndex,
                                playbackRate = playbackSpeed
                            )

                        }
                    } else {
                        // 播放结束，发送停止报告
                        playerViewModel.reportStopped(
                            mediaId = mediaId,
                            media = media,
                            position = position,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            selectedAudioIndex = selectedAudioIndex,
                            playbackRate = playbackSpeed
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
                // 服务器限流（429）：明确提示并停止自动重试，避免重试风暴加剧限流
                val httpStatusCode = generateSequence<Throwable>(error.cause) { it.cause }
                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()?.responseCode
                if (httpStatusCode == 429) {
                    Log.w("PlayerScreen", "服务器限流(429)，停止自动重试")
                    scope.launch {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.error_rate_limited),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
                // 音频输出类错误 → 交给音频自动恢复（安全模式重建/无声降级），不消耗一次性的转码回退
                val isAudioTrackError =
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED ||
                        generateSequence<Throwable>(error.cause) { it.cause }.any {
                            it is AudioSink.InitializationException ||
                                it is AudioSink.ConfigurationException ||
                                it is AudioSink.WriteException
                        }
                if (isAudioTrackError) {
                    Log.w(
                        AUDIO_RECOVERY_TAG,
                        "播放错误为音频输出类(code=${error.errorCode})，触发音频自动恢复"
                    )
                    handleAudioSinkFailure()
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
        p.addListener(listener)

        onDispose {
            bandwidthMeter.removeEventListener(bandwidthListener)
            p.stop()
            p.removeListener(listener)
            p.setVideoSurface(null)
            p.release()
        }
    }

    // 退出播放页时上报停止（与播放器实例重建解耦，音频自动恢复重建时不会误报）
    DisposableEffect(Unit) {
        onDispose {
            playerViewModel.reportStopped(
                mediaId = mediaId,
                media = media,
                position = position,
                selectedSubtitleIndex = selectedSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex,
                playbackRate = playbackSpeed
            )
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
                        // 内置字幕视图改为由下方 overlay SubtitleView 渲染（支持时间偏移）
                        subtitleView?.visibility = View.GONE
                    }
                },
                update = { view ->
                    view.player = player
                },
//                modifier = Modifier.fillMaxSize()
            )

            // 1.5 Subtitle Layer - 接管字幕渲染，支持时间偏移
            AndroidView(
                factory = { ctx ->
                    SubtitleView(ctx).apply {
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

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        overlaySubtitleView.value = this
                    }
                },
                update = { view ->
                    view.setBottomPaddingFraction(
                        subtitleBottomPadding * (1f - SUBTITLE_TOP_RESERVED_FRACTION)
                    )
                },
                modifier = Modifier.fillMaxSize()
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
                    currentVideoDecoderName = currentVideoDecoderName,
                    playbackSpeed = playbackSpeed
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
                        // 剧集与电影的播放模式分开保存
                        val isSeriesContent =
                            mediaInfo.type == "Episode" || mediaInfo.seriesId != null
                        val key = if (isSeriesContent) "play_mode" else "play_mode_movie"
                        prefs.edit().putInt(key, it).apply()
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
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = {
                        playbackSpeed = it
                        preferencesManager.playbackSpeed = it
                    },
                    subtitleBottomPadding = subtitleBottomPadding,
                    onSubtitleBottomPaddingChange = {
                        subtitleBottomPadding = it
                        preferencesManager.subtitleBottomPadding = it
                    },
                    subtitleTimeOffsetMs = subtitleTimeOffsetMs,
                    onSubtitleTimeOffsetChange = { subtitleTimeOffsetMs = it },
                    currentMediaSourceId = currentMediaSourceId,
                    allMediaSources = allMediaSources,
                    onSourceSelect = { source ->
                        if (source.id != null && source.id != currentMediaSourceId) {
                            selectedMediaSourceId = source.id
                            // 记住选择的片源名，下次播放同类多版本影片时自动匹配
                            source.name?.takeIf { it.isNotBlank() }?.let {
                                preferencesManager.preferredMediaSourceName = it
                            }
                            // 重置轨道选择，切换后使用新版本的默认音轨/字幕
                            selectedAudioIndex = -1
                            selectedSubtitleIndex = -99
                            hasTriedTranscodeFallback = false
                            playbackTrigger++
                        }
                    },
                    onLaunchExternalPlayer = { pkg ->
                        launchExternalPlayer(pkg)
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

