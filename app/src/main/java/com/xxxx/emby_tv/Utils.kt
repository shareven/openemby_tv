package com.xxxx.emby_tv

import android.content.Context
import android.media.MediaCodecInfo
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import android.media.MediaCodecList

object Utils {
    val gson = Gson()

    fun getImageUrl(serverUrl: String, item: BaseItemDto, isShowImg17: Boolean): String {
        // 1. 获取图片标签（确保你的 DTO 中 imageTags 是 Map<String, String>?）
        val imageTags = item.imageTags
        val parentBackdropImageTags = item.parentBackdropImageTags
        val itemId = item.id

        if (isShowImg17) {
            // 优先使用父级的背景图 (Backdrop)
            if (item.parentBackdropItemId != null && !parentBackdropImageTags.isNullOrEmpty()) {
                val tag = parentBackdropImageTags[0]
                return "$serverUrl/emby/Items/${item.parentBackdropItemId}/Images/Backdrop?maxWidth=500&tag=$tag&quality=80"
            }
            // 其次使用父级的缩略图 (Thumb)
            else if (item.parentThumbItemId != null) {
                return "$serverUrl/emby/Items/${item.parentThumbItemId}/Images/Thumb?maxWidth=500&tag=${item.parentThumbImageTag}&quality=80"
            }
            // 再次使用自己的缩略图 (Thumb)
            else if (imageTags?.containsKey("Thumb") == true) {
                val tag = imageTags["Thumb"]
                return "$serverUrl/emby/Items/$itemId/Images/Thumb?maxWidth=500&tag=$tag&quality=80"
            }
        }

        // 默认返回主封面图 (Primary)
        val primaryTag = imageTags?.get("Primary") ?: item.parentPrimaryImageTag
        val primaryId =
            if (imageTags?.containsKey("Primary") == true) itemId else item.parentPrimaryImageItemId

        if (!primaryTag.isNullOrEmpty() && !primaryId.isNullOrEmpty()) {
            return "$serverUrl/emby/Items/$primaryId/Images/Primary?maxWidth=500&tag=$primaryTag&quality=80"
        }

        return ""
    }

    fun formatDate(dateStr: Any?): String {
        if (dateStr == null) return ""
        val s = dateStr.toString()
        try {
            // Simple ISO 8601 substring (YYYY-MM-DD)
            if (s.length >= 10) {
                return s.substring(0, 10)
            }
        } catch (e: Exception) {
            return s
        }
        return s
    }

    fun formatRuntimeFromTicks(ticks: Long): String {
        val totalSeconds = ticks / 10000000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        if (hours > 0) {
            return "${hours}h ${minutes}m"
        }
        return "${minutes}m"
    }

    /**
     * 根据 PlaybackInfo 响应判定播放方式
     * 判定优先级: DirectPlay > DirectStream > Transcode
     *
     * @param media 包含 MediaSources 列表的媒体信息
     * @return PlayMethod: "DirectPlay" | "DirectStream" | "Transcode"
     */
    fun determinePlayMethod(media: MediaDto): String {
        val source = media.mediaSources?.firstOrNull() ?: return "Transcode"

        return when {
            source.supportsDirectPlay == true -> "DirectPlay"
            source.supportsDirectStream == true -> "DirectStream"
            !source.transcodingUrl.isNullOrEmpty() -> "Transcode"
            else -> "Transcode"
        }
    }

    fun formatFileSize(bytes: Long?): String {
        val b = bytes ?: 0L
        if (b <= 0) return ""
        val mb = b / 1024.0 / 1024.0
        if (mb > 1024) {
            return "%.2f GB".format(mb / 1024.0)
        }
        return "%.0f MB".format(mb)
    }

    fun formatDuration(ms: Long): String {
        // 再次保险：如果传入的是负数（如 C.TIME_UNSET 直接透传），返回 0
        if (ms <= 0) return "00:00"

        val seconds = ms / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    fun formatMbps(bps: Int?): String {
        val b = bps ?: 0
        if (b <= 0) return ""
        val mbps = b / 1000000.0
        return "%.1f Mbps".format(mbps)
    }

    fun formatKbps(bps: Int?): String {
        val b = bps ?: 0
        if (b <= 0) return ""
        val kbps = b / 1000
        return "$kbps kbps"
    }

    fun formatBandwidth(bps: Long): String {
        if (bps <= 0) return ""

        // bps (bits/s) → bytes/s
        val bytesPerSecond = bps / 8.0

        // bytes/s → KB/s
        val kbs = bytesPerSecond / 1024.0

        // bytes/s → MB/s
        val mbs = kbs / 1024.0

        return if (mbs >= 1.0) {
            "%.1f MB/s".format(mbs)
        } else {
            "%.0f KB/s".format(kbs)
        }
    }

    /**
     * 将 Emby ticks 转换为毫秒
     * Emby 使用 10000 ticks = 1ms 的转换比例
     *
     * @param ticks Emby ticks 值
     * @return 毫秒值
     */
    fun ticksToMs(ticks: Long): Long {
        return ticks / 10000
    }


    fun getTranscodeReasonText(context: Context, reasons: List<String>): String {
        if (reasons.isEmpty()) return ""

        return reasons.joinToString(", ") { reason ->
            // 动态根据字符串名称获取资源 ID
            val resId = context.resources.getIdentifier(
                reason, "string", context.packageName
            )
            if (resId != 0) context.getString(resId) else reason
        }
    }
}

// Extension functions for JsonObject to safely get values
fun JsonObject.safeGetString(key: String): String? {
    return if (has(key) && !get(key).isJsonNull) {
        get(key).asString
    } else {
        null
    }
}


data class DvProfileInfo(
    val profileInt: Int,
    val profileName: String,
    val levelInt: Int,
    val maxSupportedLevel: String,
)

/**
 * 多片源版本自动选择（按用户偏好策略）
 *
 * @param preference 0:杜比视界优先 1:HDR优先 2:流畅优先(低码率) 3:默认排序
 */
fun pickPreferredMediaSource(
    sources: List<MediaSourceInfoDto>,
    preference: Int
): MediaSourceInfoDto? {
    if (sources.size <= 1) return sources.firstOrNull()
    return when (preference) {
        0 -> sources.firstOrNull { isDolbyVisionSource(it) } ?: sources.firstOrNull()
        1 -> sources.firstOrNull { isHdrClassSource(it) } ?: sources.firstOrNull()
        2 -> sources.filter { (it.bitrate ?: 0) > 0 }.minByOrNull { it.bitrate!! }
            ?: sources.firstOrNull()
        else -> sources.firstOrNull()
    }
}

/** 该片源是否为杜比视界（元数据缺失时按片源名/路径兜底，".DV." 精确匹配避免误判 DVD） */
fun isDolbyVisionSource(source: MediaSourceInfoDto): Boolean {
    val video = source.mediaStreams?.firstOrNull { it.type == "Video" }
    val rangeType = video?.videoRangeType?.uppercase() ?: ""
    if (rangeType.startsWith("DOVI")) return true
    if (video?.videoRange?.equals("DV", ignoreCase = true) == true) return true
    val nameSource = "${source.name ?: ""}|${source.path ?: ""}"
    if (nameSource.contains(".DV.") || nameSource.contains(".DoVi.")) return true
    if (nameSource.contains("Dolby.Vision", ignoreCase = true)) return true
    return false
}

/** 该片源是否为 HDR 类（HDR10/HDR10+/HLG/杜比视界；元数据缺失时按名称兜底） */
fun isHdrClassSource(source: MediaSourceInfoDto): Boolean {
    if (isDolbyVisionSource(source)) return true
    val video = source.mediaStreams?.firstOrNull { it.type == "Video" } ?: return false
    val rangeType = video.videoRangeType?.uppercase() ?: ""
    val range = video.videoRange?.uppercase() ?: ""
    if (rangeType.contains("HDR10") || rangeType.contains("HLG")) return true
    if (range == "HDR") return true
    val nameSource = "${source.name ?: ""}|${source.path ?: ""}"
    if (nameSource.contains(".HDR", ignoreCase = true) || nameSource.contains("HDR10", ignoreCase = true) ||
        nameSource.contains("HLG", ignoreCase = true)
    ) return true
    return false
}

fun getSupportedDolbyVisionProfiles(): List<DvProfileInfo> {
    val profileList = mutableListOf<DvProfileInfo>()

    // 获取所有支持解码的媒体库列表
    val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
    val codecInfos = codecList.codecInfos

    for (info in codecInfos) {
        // 排除编码器，只看解码器
        if (info.isEncoder) continue

        val types = info.supportedTypes
        for (type in types) {
            // 匹配杜比视界的 MIME 类型
            if (type.equals("video/dolby-vision", ignoreCase = true)) {
                val capabilities = info.getCapabilitiesForType(type)
                val profileLevels = capabilities.profileLevels

                for (pl in profileLevels) {
                    val name = mapDvProfileToName(pl.profile)
                    val level = mapDvLevelToName(pl.level)

                    // 避免重复添加
                    if (profileList.none { it.profileInt == pl.profile }) {
                        profileList.add(DvProfileInfo(pl.profile, name, pl.level, level))
                    }
                }
            }
        }
    }
    return profileList.sortedBy { it.profileInt }
}

// 映射 Profile 数值为可读名称
private fun mapDvProfileToName(profile: Int): String {
    return when (profile) {
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPen -> "P1 (AVC/Enh)"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe -> "P9 (SDR/Comp)" // 部分版本映射 P9

        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDer -> "P2 (HEVC/Base)"

        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDen -> "P3 (HEVC/Enh)"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr -> "P7 (UHD Blue)" // 你 蓝光 原盘用的
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn -> "P8 (HDR10/Comp)" // 兼容性最强
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDth -> "P5 (OTT/Std)"  // 流媒体最常用
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb -> "P6 (Legacy/DL)"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt -> "P4 (Legacy/ST)"

        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvav110 -> "P10 (AV1/Next)" // 2026年开始流行的 AV1 杜比

        else -> "P$profile (UNK)"
    }
}

// 映射 Level 数值为可读名称
private fun mapDvLevelToName(level: Int): String {
    return when (level) {
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd24 -> "FHD 24fps"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd30 -> "FHD 30fps"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60 -> "FHD 60fps"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd24 -> "4K 24fps"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd30 -> "4K 30fps"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd48 -> "4K 48fps"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd60 -> "4K 60fps"
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd120 -> "4K 120fps"
        else -> "Level $level"
    }
}


