package com.xxxx.emby_tv.data.remote.subtitle

/**
 * 客户端在线字幕源接口。
 *
 * 实现方直连字幕站点 API，不经过 Emby 服务器，
 * 供第三方数据源（服务器无字幕插件）场景使用。
 */
interface OnlineSubtitleProvider {
    /** 唯一标识（thunder / opensubtitles） */
    val id: String

    /** 该源是否支持指定语言（SubtitleLanguageResolver 归一后的代码） */
    fun supportsLanguage(language: String): Boolean

    /**
     * 搜索字幕。实现内部自行捕获网络异常并返回空列表，
     * 只有业务上需要提示的错误（如未配置 Key）才抛异常。
     */
    suspend fun search(query: OnlineSubtitleQuery): List<OnlineSubtitleResult>

    /** 下载字幕（返回 UTF-8 内容），失败抛异常 */
    suspend fun download(result: OnlineSubtitleResult): OnlineSubtitleFile
}
