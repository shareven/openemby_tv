package com.xxxx.emby_tv.ui.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.xxxx.emby_tv.data.model.MediaStreamDto

/**
 * 播放器轨道管理器
 * 
 * 负责处理字幕和音频轨道的选择逻辑
 */
object PlayerTrackManager {
    private const val TAG = "PlayerTrackManager"

    /**
     * 选择字幕轨道
     * 
     * @param player ExoPlayer 实例
     * @param subtitleTracks Emby 返回的字幕轨道列表
     * @param selectedIndex 要选择的字幕 index
     * @param currentTracks 当前轨道信息
     * @param configRoutedIndices 改由外挂配置轨承载的字幕索引集合
     *        （外置文件 / 内嵌轨因 zlib 压缩改走 Stream.srt）
     */
    fun selectSubtitle(
        player: ExoPlayer,
        subtitleTracks: List<MediaStreamDto>,
        selectedIndex: Int,
        currentTracks: Tracks?,
        configRoutedIndices: Set<Int> = emptySet()
    ) {
        if (subtitleTracks.isEmpty()) return

        // 如果是 -1，关闭字幕
        if (selectedIndex == -1) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            Log.d(TAG, "Subtitle disabled")
            return
        }

        // 找到目标字幕
        val targetTrack = subtitleTracks.find { it.index == selectedIndex }
            ?: run {
                Log.w(TAG, "Target subtitle index $selectedIndex not found in metadata")
                return
            }

        val targetOrdinalIndex = subtitleTracks.indexOf(targetTrack)
        val targetLabel = targetTrack.displayTitle
        val targetIndex = targetTrack.index?.toString() ?: ""
        val isConfigRouted = targetTrack.index in configRoutedIndices
        val isLoadedViaConfig = targetTrack.supportsExternalStream == true
                || targetTrack.isExternal == true || isConfigRouted

//        Log.d(TAG, "Subtitle Selection: Target [EmbyIndex:$targetIndex, Label:$targetLabel, OrdinalIndex:$targetOrdinalIndex, LoadedViaConfig:$isLoadedViaConfig]")

        // 开启文本轨道
        var parametersBuilder = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)

        val groups = currentTracks?.groups ?: player.currentTracks.groups
        val trackGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
        var isMatched = false

        // 打印可用字幕轨道
//        Log.d(TAG, "--- Available Text Tracks ---")
        trackGroups.forEachIndexed { gi, group ->
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
//                Log.d(TAG, "Group[$gi] Track[$i]: ID=${f.id}, Label=${f.label}, Lang=${f.language}")
            }
        }

        // 策略1：外挂配置轨按唯一 Label（"Label [Index]"，与 SubtitleConfigBuilder 一致）精确匹配。
        // 必须先于顺序匹配：改走服务器提取的字幕轨同时存在内嵌原轨时，
        // 顺序匹配会命中（无法解析的）内嵌轨。
        if (isLoadedViaConfig) {
            val uniqueTargetLabel = "$targetLabel [$targetIndex]"
            outer@ for (group in trackGroups) {
                for (i in 0 until group.length) {
                    if (group.getTrackFormat(i).label == uniqueTargetLabel) {
                        parametersBuilder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, i)
                        )
                        isMatched = true
                        break@outer
                    }
                }
            }
        }

        // 策略2：顺序匹配（用于直接播放的内嵌字幕）
        if (!isMatched && isLoadedViaConfig && targetOrdinalIndex >= 0) {
            var trackCounter = 0
            outerOrdinal@ for (group in trackGroups) {
                for (i in 0 until group.length) {
                    if (trackCounter == targetOrdinalIndex) {
                        parametersBuilder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, i)
                        )
                        isMatched = true
                        val f = group.getTrackFormat(i)
//                        Log.d(TAG, "Matched subtitle by Ordinal: OrdinalIndex=$targetOrdinalIndex -> Track (ID:${f.id}, Label:${f.label})")
                        break@outerOrdinal
                    }
                    trackCounter++
                }
            }
        }

        if (isMatched) {
            player.trackSelectionParameters = parametersBuilder.build()
        } else {
            Log.w(TAG, "Unable to match subtitle: EmbyIndex=$targetIndex, OrdinalIndex=$targetOrdinalIndex, Label=$targetLabel")
        }
    }

    /**
     * 选择音频轨道
     * 
     * @param player ExoPlayer 实例
     * @param audioTracks Emby 返回的音频轨道列表
     * @param selectedIndex 要选择的音频 index
     * @param currentTracks 当前轨道信息
     */
    fun selectAudio(
        player: ExoPlayer,
        audioTracks: List<MediaStreamDto>,
        selectedIndex: Int,
        currentTracks: Tracks?
    ) {
        if (audioTracks.isEmpty()) return
        if (selectedIndex <= -1) return

        val targetTrack = audioTracks.find { it.index == selectedIndex }
            ?: run {
                Log.w(TAG, "Target audio index $selectedIndex not found in metadata")
                return
            }

        val targetOrdinalIndex = audioTracks.indexOf(targetTrack)
        val targetLabel = targetTrack.displayTitle
        val targetIndex = targetTrack.index?.toString() ?: ""

        Log.d(TAG, "Audio Selection: Target [EmbyIndex:$targetIndex, Label:$targetLabel, OrdinalIndex:$targetOrdinalIndex]")

        var parametersBuilder = player.trackSelectionParameters.buildUpon()

        val groups = currentTracks?.groups ?: player.currentTracks.groups
        val trackGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        var isMatched = false

        // 打印可用音频轨道
        // Log.d(TAG, "--- Available Audio Tracks ---")
        var totalTracks = 0
        trackGroups.forEachIndexed { gi, group ->
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
                // Log.d(TAG, "Group[$gi] Track[$i]: ID=${f.id}, Label=${f.label}, Lang=${f.language}")
                totalTracks++
            }
        }
        Log.d(TAG, "Total audio tracks: $totalTracks")

        // 使用顺序匹配：音频列表的第一个对应轨道顺序0
        if (targetOrdinalIndex >= 0) {
            var trackCounter = 0
            outerOrdinal@ for (group in trackGroups) {
                for (i in 0 until group.length) {
                    if (trackCounter == targetOrdinalIndex) {
                        parametersBuilder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, i)
                        )
                        isMatched = true
                        val f = group.getTrackFormat(i)
//                        Log.d(TAG, "Matched audio by Ordinal: OrdinalIndex=$targetOrdinalIndex -> Track (ID:${f.id}, Label:${f.label})")
                        break@outerOrdinal
                    }
                    trackCounter++
                }
            }
        }

        if (isMatched) {
            player.trackSelectionParameters = parametersBuilder.build()
        } else {
            Log.w(TAG, "Unable to match audio: OrdinalIndex=$targetOrdinalIndex, Label=$targetLabel")
        }
    }
}
