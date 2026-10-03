package com.xxxx.emby_tv.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto

/**
 * 外部播放器公共工具：枚举播放器、构建播放直链、拉起 Intent
 */
object ExternalPlayerHelper {

    /** 外部播放器选项：label + 包名（null 表示系统选择器） */
    data class PlayerOption(val label: String, val packageName: String?)

    /** 枚举设备上可播放视频的播放器（排除本应用），系统选择器置顶 */
    fun queryPlayers(context: Context): List<PlayerOption> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("http://example.com/video.mkv"), "video/*")
        val resolveInfos: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }
        val ownPackage = context.packageName
        val players: List<PlayerOption> = resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.applicationInfo?.packageName ?: return@mapNotNull null
            if (pkg == ownPackage) null
            else PlayerOption(ri.loadLabel(pm).toString(), pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        return players
    }

    /** 构建外部播放器用的播放地址：优先服务器直链，兜底静态原始流 */
    fun buildPlayUrl(
        media: MediaDto,
        mediaId: String?,
        currentMediaSourceId: String?,
        serverUrl: String,
        apiKey: String
    ): String? {
        val sources = media.mediaSources ?: return null
        val source: MediaSourceInfoDto = sources.firstOrNull { it.id == currentMediaSourceId }
            ?: sources.firstOrNull()
            ?: return null
        (source.directStreamUrl ?: source.transcodingUrl)?.let { return "$serverUrl/emby$it" }
        val id = mediaId ?: return null
        val container = source.container?.takeIf { it.isNotBlank() } ?: "mp4"
        val url = StringBuilder("$serverUrl/emby/videos/$id/stream.$container?Static=true")
        source.id?.let { url.append("&MediaSourceId=").append(it) }
        if (apiKey.isNotEmpty()) url.append("&api_key=").append(apiKey)
        return url.toString()
    }

    /** 构建拉起外部播放器的 Intent；packageName 为空时走系统选择器 */
    fun buildLaunchIntent(url: String, packageName: String?, startPositionMs: Long): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (startPositionMs > 0) {
                // VLC / MX Player 等支持以毫秒定位起播
                putExtra("position", startPositionMs)
                putExtra("startPositionMs", startPositionMs)
            }
            packageName?.takeIf { it.isNotBlank() }?.let { setPackage(it) }
        }
    }

    /** 拉起外部播放器；指定包名不存在时回退系统选择器。返回是否成功 */
    fun launch(context: Context, intent: Intent, onBeforeLaunch: () -> Unit = {}): Boolean {
        return try {
            onBeforeLaunch()
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            try {
                onBeforeLaunch()
                context.startActivity(intent.setPackage(null))
                true
            } catch (e2: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 判断视频流是否为杜比视界（用于"仅杜比视界自动外部播放"偏好） */
    fun isDolbyVision(videoStream: MediaStreamDto?): Boolean {
        if (videoStream == null) return false
        val rangeType = videoStream.videoRangeType?.uppercase() ?: ""
        if (rangeType.startsWith("DOVI")) return true
        if (videoStream.videoRange?.equals("DV", ignoreCase = true) == true) return true
        if (videoStream.extendedVideoType?.equals("DolbyVision", ignoreCase = true) == true) return true
        if (!videoStream.videoDoViTitle.isNullOrBlank()) return true
        return false
    }

    /** 获取当前播放的视频流 */
    fun getVideoStream(media: MediaDto, currentMediaSourceId: String?): MediaStreamDto? {
        val source = media.mediaSources
            ?.firstOrNull { it.id == currentMediaSourceId }
            ?: media.mediaSources?.firstOrNull()
        return source?.mediaStreams?.firstOrNull { it.type == "Video" }
    }

    /** 从外部播放器返回的 Intent 中提取播放位置（毫秒），不支持回传的播放器返回 null */
    fun extractReturnedPosition(resultIntent: Intent?): Long? {
        if (resultIntent == null) return null
        // VLC: "position"(long, ms)；MX Player: "position"(int, ms)
        val position = resultIntent.extras?.get("position")
        return when (position) {
            is Long -> position.takeIf { it > 0 }
            is Int -> position.toLong().takeIf { it > 0 }
            else -> null
        }
    }
}
