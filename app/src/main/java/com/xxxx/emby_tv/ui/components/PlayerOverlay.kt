package com.xxxx.emby_tv.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.*
import androidx.compose.material3.Icon // Explicit import for Icon if using material3 Icon
// Or use androidx.tv.material3.Icon if available, but usually it's compatible.
// If ambiguity persists, use fully qualified names or aliases.
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
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.Utils.formatDuration
import com.xxxx.emby_tv.Utils.formatKbps
import com.xxxx.emby_tv.Utils.formatMbps
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.model.SessionDto
import com.xxxx.emby_tv.ui.components.PlayerMenu
import com.xxxx.emby_tv.DvProfileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlayerOverlay(
    isTunnelingSafe: Boolean,
    mediaInfo: BaseItemDto,
    mediaSource: MediaSourceInfoDto?,
    session: SessionDto?,
    videoStream: MediaStreamDto?,
    audioStream: MediaStreamDto?,
    position: Long,
    duration: Long,
    buffered: Long,
    isPlaying: Boolean,
    player: ExoPlayer,
    isBuffering: Boolean,
    downloadSpeed: Long = 0,
    supportedDvProfiles: List<DvProfileInfo> = emptyList(),
    currentVideoDecoderName: String,
    playbackSpeed: Float = 1.0f,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        // 倍速角标（仅 != 1.0x 时显示）
        if (playbackSpeed != 1.0f) {
            Text(
                text = String.format("%.2fx", playbackSpeed),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Top Info
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            val parentIndex = mediaInfo.parentIndexNumber
            val index = mediaInfo.indexNumber
            val productionYear = mediaInfo.productionYear?.toString()
            Text(
                text = mediaInfo.seriesName ?: mediaInfo.name ?: "",
                style = TvMaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            if (parentIndex != null && index != null) {
                Text(
                    text = "S${parentIndex}:E${index} ${mediaInfo.name ?: ""}",
                    style = TvMaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
            } else if (productionYear != null) {
                Text(
                    text = productionYear,
                    style = TvMaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if(isTunnelingSafe) Text(
                text = stringResource(R.string.tunneling_mode),
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.streaming),
                color = Color.White,
                fontSize = 14.sp
            )
            Text(text = getStreamContainerLine(mediaSource), color = Color.White, fontSize = 12.sp)
            Row {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = getStreamModeLine(session, mediaSource),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.video), color = Color.White, fontSize = 14.sp)
            Text(
                text = getVideoMainLine(videoStream, session, mediaSource),
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
            Text(
                text = getVideoDetailLine(videoStream, mediaSource),
                color = Color.White,
                fontSize = 12.sp
            )
            Row {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = getVideoModeLine(
                        session,
                        videoStream,
                        supportedDvProfiles,
                        currentVideoDecoderName,
                    ),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.audio), color = Color.White, fontSize = 14.sp)
            Text(
                text = getAudioMainLine(audioStream, session, mediaSource),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(text = getAudioDetailLine(audioStream), color = Color.White, fontSize = 12.sp)
            Row {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = getAudioModeLine(session, mediaSource),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
        }

        // Center Play/Loading Icon
        Box(modifier = Modifier.align(Alignment.Center)) {
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
            } else if (!isPlaying) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Paused",
                    modifier = Modifier.size(100.dp),
                    tint = Color.White
                )
            }
        }

        // Bottom Progress
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatDuration(position), color = Color.White)
                Text(text = formatDuration(duration), color = Color.White)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.Gray)
            ) {
                // Buffered
                if (duration > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(
                                fraction = (buffered.toFloat() / duration.toFloat()).coerceIn(
                                    0f,
                                    1f
                                )
                            )
                            .background(Color.LightGray)
                    )

                    // Current
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(
                                fraction = (position.toFloat() / duration.toFloat()).coerceIn(
                                    0f,
                                    1f
                                )
                            )
                            .background(TvMaterialTheme.colorScheme.secondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {

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


@Composable
fun getStreamContainerLine(mediaSource: MediaSourceInfoDto?): String {
    if (mediaSource == null) return ""
    val container = mediaSource.container?.uppercase() ?: ""
    val bitrate = (mediaSource.bitrate) ?: (mediaSource.transcodingBitrate)
    val bitrateStr = formatMbps(bitrate)
    if (container.isEmpty() && bitrateStr.isEmpty()) return ""
    if (bitrateStr.isEmpty()) return container
    if (container.isEmpty()) return bitrateStr
    return "$container ($bitrateStr)"
}

@Composable
fun getStreamModeLine(session: SessionDto?, mediaSource: MediaSourceInfoDto?): String {
    val context = LocalContext.current

    // 1. 基础校验：获取播放方法（DirectPlay, DirectStream, Transcode）
    val playMethod = session?.playState?.playMethod ?: return ""
    val ti = session.transcodingInfo

    // 2. 解析转码原因 (Transcode Reasons) - 官方风格：每个原因独占一行
    val reasons = ti?.transcodeReasons ?: emptyList()
    val reasonTag = if (reasons.isNotEmpty()) {
        "\n" + reasons.joinToString("\n") { reason ->
            val resId = context.resources.getIdentifier(reason, "string", context.packageName)
            if (resId != 0) context.getString(resId) else reason
        }
    } else ""

    // 3. 处理：直接播放 (Direct Play)
    if (playMethod == "DirectPlay") {
        return stringResource(R.string.direct_play)
    }

    // 4. 获取流协议与码率信息 (例如: HLS (8 mbps))
    val container = ti?.container?.uppercase() ?: ""
    val bitrate = if (ti?.bitrate != null) " (${ti.bitrate / 1000000} mbps)" else ""
    val protocolInfo = if (container.isNotEmpty()) "$container$bitrate" else ""

    // 5. 处理：直接串流 (Direct Stream)
    if (playMethod == "DirectStream") {
        val streamHeader =
            if (protocolInfo.isNotEmpty()) protocolInfo else stringResource(R.string.direct_stream)
        // 官方 DirectStream 如果有原因也会换行显示
        return "$streamHeader$reasonTag"
    }

    // 6. 处理：转码播放 (Transcode)
    if (playMethod == "Transcode") {
        // 硬件加速标识 (⚡)
        val isHardware = ti?.videoDecoderIsHardware == true || ti?.videoEncoderIsHardware == true
        val hardwareTag = if (isHardware) " ⚡" else ""

        // 如果没有具体协议信息，使用“转码”作为标题
        val header =
            if (protocolInfo.isNotEmpty()) "$protocolInfo$hardwareTag" else "${stringResource(R.string.transcode)}$hardwareTag"

        return "$header$reasonTag"
    }

    return ""
}


@Composable
fun getVideoMainLine(
    videoStream: MediaStreamDto?,
    session: SessionDto?,
    mediaSource: MediaSourceInfoDto?,
): String {
    if (videoStream == null && mediaSource == null) return ""
    val displayTitle = videoStream?.displayTitle
    if (!displayTitle.isNullOrEmpty()) return displayTitle

    val ti = session?.transcodingInfo
    val isVideoDirect = ti?.isVideoDirect == true

    val height = videoStream?.height
    val res = if (height != null && height > 0) "${height}p" else ""

    if (ti != null && !isVideoDirect) {
        var codec = (ti.videoCodec ?: "").uppercase()
        val bitrate = ti.videoBitrate

        if (codec.isEmpty()) codec = (videoStream?.codec ?: "").uppercase()
        val bitrateStr = formatMbps(bitrate)

        return listOfNotNull(
            res.takeIf { it.isNotEmpty() },
            codec.takeIf { it.isNotEmpty() },
            bitrateStr.takeIf { it.isNotEmpty() }).joinToString(" ")
    }

    val codec = (videoStream?.codec ?: "").uppercase()
    return listOfNotNull(
        res.takeIf { it.isNotEmpty() },
        codec.takeIf { it.isNotEmpty() }).joinToString(" ")
}

@Composable
fun getVideoDetailLine(videoStream: MediaStreamDto?, mediaSource: MediaSourceInfoDto?): String {
    if (videoStream == null && mediaSource == null) return ""
    val profile = (videoStream?.profile ?: "")
    val levelVal = (videoStream?.level)
    val level = if (levelVal != null && levelVal > 0) levelVal.toString() else ""

    val fpsVal =
        (videoStream?.averageFrameRate)?.toInt() ?: (videoStream?.realFrameRate)?.toInt() ?: 0
    val fps = if (fpsVal != 0) "$fpsVal ${stringResource(R.string.fps_suffix)}" else ""

    val bitrateVal = videoStream?.bitRate ?: mediaSource?.bitrate ?: mediaSource?.transcodingBitrate
    val bitrateStr = formatMbps(bitrateVal)

    val dolbyVisionProfile = if (videoStream?.extendedVideoType == "DolbyVision") {
        videoStream.extendedVideoSubTypeDescription ?: ""
    } else ""

    return listOfNotNull(
        profile.takeIf { it.isNotEmpty() },
        level.takeIf { it.isNotEmpty() },
        dolbyVisionProfile.takeIf { it.isNotEmpty() },
        bitrateStr.takeIf { it.isNotEmpty() },
        fps.takeIf { it.isNotEmpty() }).joinToString(" ")
}

@Composable
fun getVideoModeLine(
    session: SessionDto?,
    videoStream: MediaStreamDto?,
    supportedDvProfiles: List<DvProfileInfo>,
    currentVideoDecoderName: String,
): String {
    // 基础校验
    val playMethod = session?.playState?.playMethod ?:  ""
    val ti = session?.transcodingInfo

    // 1. 直接播放 (Direct Play): 无需任何处理，服务器负载最低
    val isVideoDirect = playMethod == "DirectPlay" || ti == null || ti.isVideoDirect == true

    val supportDolbyVisionProfile =
        if (videoStream?.extendedVideoType == "DolbyVision" && currentVideoDecoderName.isNotEmpty()) {
            val profileNames = supportedDvProfiles.joinToString(", ") { it.profileName }
            if(supportedDvProfiles.isNotEmpty()) stringResource(R.string.dolby_vision_profiles, profileNames) + "\n"
            else stringResource(R.string.not_support_dolby_vision)+ "\n"
        } else ""


    val dolbyVisionDecoder =
        if (videoStream?.extendedVideoType == "DolbyVision" && currentVideoDecoderName.isNotEmpty()) {

            when {
                // 匹配杜比视界：包含 .dv. 或者包含 dolby、dvhe (针对 OMX.MS.DOLBY_VISION.DVHE...)
                currentVideoDecoderName.contains(".dv.", ignoreCase = true) ||
                        currentVideoDecoderName.contains("dolby", ignoreCase = true) ||
                        currentVideoDecoderName.contains("dvhe", ignoreCase = true) -> {
                    stringResource(
                        R.string.dolby_vision_decoder,
                        currentVideoDecoderName
                    )
                }

                // 匹配 HEVC：包含 .hevc. 或者包含 h265
                currentVideoDecoderName.contains(".hevc.", ignoreCase = true) ||
                        currentVideoDecoderName.contains("h265", ignoreCase = true) -> {
                    stringResource(
                        R.string.hevc_decoder,
                        currentVideoDecoderName
                    )
                }

                else -> stringResource(R.string.video_decoder, currentVideoDecoderName)
            } + "\n"
        } else ""


    if (isVideoDirect) {
        return dolbyVisionDecoder + supportDolbyVisionProfile + stringResource(R.string.direct_play)
    }

    var isHardware = false
    var vCodec = ""
    var bitrate: Int? = null


    isHardware = ti.videoDecoderIsHardware == true
    vCodec = (ti.videoCodec ?: "").uppercase()
    bitrate = ti.videoBitrate ?: ti.bitrate


    val mbps = formatMbps(bitrate)
    val hardwareTag = if (isHardware) "⚡" else ""
    val codecInfo = listOfNotNull(
        vCodec.takeIf { it.isNotEmpty() },
        hardwareTag.takeIf { it.isNotEmpty() },
        mbps.takeIf { it.isNotEmpty() }).joinToString(" ")

    if (codecInfo.isEmpty()) return stringResource(R.string.transcode)
    return "${stringResource(R.string.transcode)} ($codecInfo)"
}

@Composable
fun getAudioMainLine(
    audioStream: MediaStreamDto?, // 确保使用的是你的 MediaStream DTO
    session: SessionDto?,
    mediaSource: MediaSourceInfoDto?, // MediaSource 通常也是 BaseItemDto 类型
): String {
    if (audioStream == null) return ""


    val lang = audioStream.language ?: ""
    val title = audioStream.displayTitle ?: ""
    val label = if (title.isNotEmpty()) title
    else if (lang.isNotEmpty()) lang
    else stringResource(R.string.unknown)

    // 6. 组合字符串
    return label
}

@Composable
fun getAudioDetailLine(audioStream: MediaStreamDto?): String {
    if (audioStream == null) return ""
    val bitrate = audioStream.bitRate
    val sampleRate = audioStream.sampleRate

    val kbps = formatKbps(bitrate)
    val hz = if (sampleRate != null && sampleRate > 0) "$sampleRate Hz" else ""

    return listOfNotNull(
        kbps.takeIf { it.isNotEmpty() },
        hz.takeIf { it.isNotEmpty() }).joinToString(" ")
}

@Composable
fun getAudioModeLine(session: SessionDto?, mediaSource: MediaSourceInfoDto?): String {
    // 基础校验
    val playMethod = session?.playState?.playMethod ?: return ""
    val ti = session.transcodingInfo

    // 1. 直接播放 (Direct Play): 无需任何处理，服务器负载最低
    if (playMethod == "DirectPlay" || ti == null) {
        return stringResource(R.string.direct_play)
    }

    val isAudioDirect = ti.isAudioDirect == true


    if (isAudioDirect) return stringResource(R.string.direct_play)

    var codec = ""
    var bitrate: Int? = null

    codec = (ti.audioCodec ?: "").uppercase()
    bitrate = ti.audioBitrate ?: ti.bitrate


    val kbps = formatKbps(bitrate)
    val info = listOfNotNull(
        codec.takeIf { it.isNotEmpty() },
        kbps.takeIf { it.isNotEmpty() }).joinToString(" ")

    if (info.isEmpty()) return stringResource(R.string.transcode)
    return "${stringResource(R.string.transcode)} ($info)"
}

fun getVideoTrack(media: MediaDto): MediaStreamDto? {
    val source = media.mediaSources?.firstOrNull()
    val streams = source?.mediaStreams
    return streams?.firstOrNull { it.type == "Video" }
}

fun getAudioTrack(media: MediaDto, selectedIndex: Int): MediaStreamDto? {
    val source = media.mediaSources?.firstOrNull()
    val streams = source?.mediaStreams
    val audios = streams?.filter { it.type == "Audio" }

    if (audios.isNullOrEmpty()) return null

    return if (selectedIndex != -1) {
        audios.firstOrNull { it.index == selectedIndex }
    } else {
        audios.firstOrNull()
    }
}

// ---------------- UI Components ----------------
