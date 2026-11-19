package com.xxxx.emby_tv.data.remote.subtitle

/**
 * 客户端在线字幕搜索的公共模型。
 *
 * 与走 Emby 服务器的 RemoteSubtitleInfoDto 区分：
 * 这里由 App 直连字幕站点（迅雷/OpenSubtitles），不依赖服务器插件，
 * 适用于第三方数据源等服务器无法提供字幕搜索的场景。
 */

/** 搜索上下文：由播放页从当前媒体源构建 */
data class OnlineSubtitleQuery(
    /** 媒体源文件名（含扩展名），迅雷按文件名匹配，优先级最高 */
    val filename: String? = null,
    /** 兜底标题（剧集为「剧名 SxxExx」，电影为片名） */
    val title: String? = null,
    /** 语言代码（SubtitleLanguageResolver 归一后的值，如 zh-CN / eng） */
    val language: String = "zh-CN",
    /** 预计算的迅雷 CID（仓库层对播放流 Range 取样算出，失败为 null） */
    val cid: String? = null
) {
    /** 默认搜索词：优先文件名，其次标题 */
    val defaultKeyword: String
        get() = filename?.takeIf { it.isNotBlank() } ?: title.orEmpty()
}

/** 搜索结果项（provider 内唯一 id + provider 自用的下载载荷） */
data class OnlineSubtitleResult(
    val providerId: String,
    val id: String,
    val name: String,
    /** 展示用语言文本，如「简体中文」 */
    val language: String? = null,
    /** 字幕格式：srt / ass / ssa / vtt */
    val format: String,
    /** 迅雷 CID 哈希命中（与当前播放文件完全一致） */
    val isHashMatch: Boolean = false,
    val downloadCount: Int? = null,
    val rating: Float? = null,
    /** provider 内部下载所需数据（仅本次会话内存中保存） */
    val payload: Any? = null
)

/** 下载完成的字幕文件 */
data class OnlineSubtitleFile(
    /** 字幕格式：srt / ass / ssa / vtt */
    val format: String,
    /** 建议文件名（不含路径） */
    val fileName: String,
    /** 已转为 UTF-8 的字幕内容 */
    val content: ByteArray
)

/**
 * 内置播放器可显示的字幕格式。
 * 注意：smi（SAMI）media3 不支持解析，不在此列，搜索结果中直接过滤。
 */
val supportedOnlineSubtitleFormats = setOf("srt", "ass", "ssa", "vtt")

/** 归一化格式：带点/大小写/别名 → 标准小写；不支持的返回 null */
fun normalizeOnlineSubtitleFormat(raw: String?): String? {
    val value = raw?.trim()?.trimStart('.')?.lowercase() ?: return null
    return when (value) {
        "subrip" -> "srt"
        "webvtt" -> "vtt"
        in supportedOnlineSubtitleFormats -> value
        else -> null
    }
}
