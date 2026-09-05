package com.xxxx.emby_tv.ui.player

import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

/**
 * 字幕时间偏移控制器
 *
 * 缓存播放器分发的 CueGroup（含字幕的媒体时间戳），
 * 按 当前播放位置 - 偏移量 选取应显示的字幕；
 * 文本 Cue 的内置 line/position 定位会被剥离，
 * 统一交由 SubtitleView 的 bottomPaddingFraction 控制垂直位置。
 *
 * 偏移为正 = 字幕延后显示（完全精确）；
 * 偏移为负 = 字幕提前显示（切换时机最多滞后 |偏移|，因无法预知未来字幕）。
 */
class SubtitleOffsetController {

    private class TimedCues(val timeMs: Long, val cues: List<Cue>)

    private val groups = ArrayDeque<TimedCues>()

    fun onCues(cueGroup: CueGroup) {
        val timeMs = cueGroup.presentationTimeUs / 1000
        // 回退跳转（seek 回退/换源）时清空历史
        if (groups.isNotEmpty() && timeMs + SEEK_CLEAR_THRESHOLD_MS < groups.last().timeMs) {
            groups.clear()
        }
        groups.addLast(TimedCues(timeMs, cueGroup.cues.map { it.withoutEmbeddedPosition() }))
        if (groups.size > MAX_BUFFERED_GROUPS) {
            groups.removeFirst()
        }
    }

    private fun Cue.withoutEmbeddedPosition(): Cue {
        if (bitmap != null) return this
        if (line == Cue.DIMEN_UNSET && position == Cue.DIMEN_UNSET) return this
        return buildUpon()
            .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
            .setLineAnchor(Cue.TYPE_UNSET)
            .setPosition(Cue.DIMEN_UNSET)
            .setPositionAnchor(Cue.TYPE_UNSET)
            .build()
    }

    fun reset() {
        groups.clear()
    }

    fun cuesFor(positionMs: Long, offsetMs: Long): List<Cue> {
        val targetMs = positionMs - offsetMs
        // 淘汰已被后续字幕取代的旧组，保留当前应显示的组
        while (groups.size > 1 && groups[1].timeMs <= targetMs) {
            groups.removeFirst()
        }
        val first = groups.firstOrNull() ?: return emptyList()
        return if (first.timeMs <= targetMs) first.cues else emptyList()
    }

    companion object {
        private const val MAX_BUFFERED_GROUPS = 256
        private const val SEEK_CLEAR_THRESHOLD_MS = 500L
    }
}
