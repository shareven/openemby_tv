package com.xxxx.emby_tv.ui.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.xxxx.emby_tv.data.model.MediaStreamDto

/**
 * 字幕配置构建器
 * 
 * 负责构建 ExoPlayer 的字幕配置
 */
object SubtitleConfigBuilder {
    private const val TAG = "SubtitleConfigBuilder"

    /**
     * 构建字幕配置列表
     * 
     * @param subtitleTracks Emby 返回的字幕轨道列表
     * @param serverUrl 服务器地址
     * @param mediaId 媒体ID
     * @param mediaSourceId 媒体源ID
     * @param apiKey API密钥
     * @param selectedSubtitleIndex 当前选中的字幕索引
     * @param serverDeliveredIndices 需要 Emby 服务器提取外挂的字幕索引集合
     *        （如内嵌轨被 zlib 压缩，media3 无法解析，只能改走 Stream.srt）
     * @return 字幕配置列表
     */
    fun buildSubtitleConfigs(
        subtitleTracks: List<MediaStreamDto>,
        serverUrl: String,
        mediaId: String,
        mediaSourceId: String,
        apiKey: String,
        selectedSubtitleIndex: Int,
        serverDeliveredIndices: Set<Int> = emptySet()
    ): List<MediaItem.SubtitleConfiguration> {
        val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

//        Log.d(TAG, "=== Building Subtitle Configurations ===")
//        Log.d(TAG, "Total subtitleTracks: ${subtitleTracks.size}")

        subtitleTracks.forEach { track ->
            val index = track.index ?: 0
            val codec = track.codec?.lowercase() ?: "srt"
            val label = track.displayTitle ?: "Subtitle"
            val lang = track.language ?: "und"

            // 判断是否可以通过URL加载（外置文件，或内嵌轨因压缩等原因需服务器提取）
            val isLoadableSubtitle = track.isExternal == true || index in serverDeliveredIndices

//            Log.d(TAG, "Track[$index]: Codec=$codec, IsExternal=${track.isExternal}, SupportsExternal=${track.supportsExternalStream}, Loadable=$isLoadableSubtitle")

            if (isLoadableSubtitle) {
                // 对于 ASS/SSA/SUBRIP 请求 SRT 格式，让 Emby 转码
                val requestFormat = when {
                    codec.contains("ass") || codec.contains("ssa") || codec.contains("subrip") -> "srt"
                    codec.contains("vtt") -> "vtt"
                    else -> "srt"
                }

                val subUrl = "${serverUrl}/emby/Videos/$mediaId/$mediaSourceId/Subtitles/$index/Stream.$requestFormat?api_key=$apiKey"

                // 根据请求格式确定 MIME 类型
                val mimeType = when (requestFormat) {
                    "vtt" -> MimeTypes.TEXT_VTT
                    else -> MimeTypes.APPLICATION_SUBRIP
                }

                // 使用 "Label [Index]" 格式作为唯一标识，防止 ExoPlayer 自动去重
                val uniqueLabel = "$label [$index]"

                val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                    .setId(index.toString())
                    .setMimeType(mimeType)
                    .setLanguage(lang)
                    .setLabel(uniqueLabel)
                    .setSelectionFlags(if (index == selectedSubtitleIndex) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()

                subtitleConfigs.add(config)
//                Log.d(TAG, "Added subtitle config: Index=$index, URL=$subUrl, UniqueLabel=$uniqueLabel")
            }
        }

//        Log.d(TAG, "Total subtitle configs added: ${subtitleConfigs.size}")
        return subtitleConfigs
    }

    /**
     * 获取字幕的 MIME 类型
     */
    fun getMimeType(codec: String): String {
        return when {
            codec.contains("ass") || codec.contains("ssa") -> MimeTypes.TEXT_SSA
            codec.contains("vtt") || codec.contains("webvtt") -> MimeTypes.TEXT_VTT
            codec.contains("srt") || codec.contains("subrip") -> MimeTypes.APPLICATION_SUBRIP
            codec.contains("pgs") || codec.contains("hdmv_pgs") -> MimeTypes.APPLICATION_PGS
            codec.contains("dvb") || codec.contains("dvbsub") -> MimeTypes.APPLICATION_DVBSUBS
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}
