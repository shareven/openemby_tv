package com.xxxx.emby_tv.data.repository

import android.content.Context
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.data.session.SessionManager
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.SessionDto

/**
 * 数据仓库 - ViewModel 唯一依赖的数据源
 * 内部组合 SessionManager + EmbyApi
 */
class EmbyRepository private constructor(private val context: Context) {
    private val session = SessionManager.getInstance(context)

    // === 代理 SessionManager 的属性 ===
    val isLoggedIn: Boolean get() = session.isLoggedIn
    val isLoaded: Boolean get() = session.isLoaded
    val savedServerUrl: String? get() = session.savedServerUrl
    val savedUsername: String get() = session.savedUsername
    val savedPassword: String get() = session.savedPassword

    val serverUrl: String? get() = session.serverUrl
    val apiKey: String? get() = session.apiKey
    val userId: String? get() = session.userId
    val deviceId: String get() = session.deviceId

    // 多账号支持
    val savedAccounts get() = session.savedAccounts
    val currentAccountId get() = session.currentAccountId

    // === 认证 ===

    /**
     * 加载已保存的凭证
     */
    suspend fun loadCredentials(): Boolean {
        return session.loadCredentials(object : SessionManager.CredentialValidator {
            override suspend fun validate(serverUrl: String, userId: String, apiKey: String): Boolean {
                return EmbyApi.testKey(context, serverUrl, userId, apiKey)
            }
        })
    }

    /**
     * 登录
     */
    suspend fun login(serverUrl: String, username: String, password: String) {
        val result = EmbyApi.authenticate(context, serverUrl, session.deviceId, username, password)
        session.saveCredentials(
            serverUrl,
            result.accessToken ?: "",
            result.user?.id ?: "",
            username,
            password
        )
    }

    /**
     * 退出登录
     */
    fun logout() {
        session.logout()
    }

    /**
     * 切换账号
     */
    fun switchAccount(accountId: String): Boolean {
        return session.switchAccount(accountId)
    }

    /**
     * 删除账号
     */
    fun removeAccount(accountId: String) {
        session.removeAccount(accountId)
    }

    // === 媒体库 ===

    /**
     * 获取继续观看列表
     */
    suspend fun getResumeItems(seriesId: String = ""): List<BaseItemDto> {
        requireLoggedIn()
        return EmbyApi.getResumeItems(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            seriesId
        )
    }

    /**
     * 获取最新媒体（按视图分组）
     */
    suspend fun getLatestItems(): List<BaseItemDto> {
        requireLoggedIn()
        return EmbyApi.getLatestItems(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!
        )
    }

    /**
     * 获取媒体库列表（支持分页）
     * 
     * @param parentId 父级ID
     * @param type 类型
     * @param startIndex 起始索引
     * @param limit 每页数量
     * @return Pair<List<BaseItemDto>, Int> 数据列表和总数
     */
    suspend fun getLibraryList(
        parentId: String, 
        type: String,
        startIndex: Int = 0,
        limit: Int = 20,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        filters: String? = null
    ): Pair<List<BaseItemDto>, Int> {
        requireLoggedIn()
        return EmbyApi.getLibraryList(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            parentId,
            type,
            startIndex,
            limit,
            sortBy,
            sortOrder,
            filters
        )
    }

    /**
     * 搜索媒体项（支持分页）
     * 
     * @param query 搜索关键词
     * @param startIndex 起始索引
     * @param limit 每页数量
     * @return Pair<List<BaseItemDto>, Int> 数据列表和总数
     */
    suspend fun searchItems(
        query: String,
        startIndex: Int = 0,
        limit: Int = 20
    ): Pair<List<BaseItemDto>, Int> {
        requireLoggedIn()
        return EmbyApi.searchItems(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            query,
            startIndex,
            limit
        )
    }

    /**
     * 获取收藏列表
     */
    suspend fun getFavoriteItems(): List<BaseItemDto> {
        requireLoggedIn()
        return EmbyApi.getFavoriteItems(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!
        )
    }

    // === 详情 ===

    /**
     * 获取媒体详情
     */
    suspend fun getMediaInfo(mediaId: String): BaseItemDto {
        requireLoggedIn()
        return EmbyApi.getMediaInfo(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            mediaId
        )
    }

    /**
     * 获取季列表
     */
    suspend fun getSeasonList(seriesId: String): List<BaseItemDto> {
        requireLoggedIn()
        return EmbyApi.getSeasonList(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            seriesId
        )
    }

    /**
     * 获取剧集列表
     */
    suspend fun getSeriesList(parentId: String): List<BaseItemDto> {
        requireLoggedIn()
        return EmbyApi.getSeriesList(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            parentId
        )
    }

    /**
     * 获取下一集
     */
    suspend fun getShowsNextUp(seriesId: String): List<BaseItemDto> {
        requireLoggedIn()
        return EmbyApi.getShowsNextUp(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            seriesId
        )
    }

    // === 播放 ===

    /**
     * 获取播放信息
     */
    suspend fun getPlaybackInfo(
        mediaId: String,
        startPosition: Long,
        selectedAudioIndex: Int? = null,
        selectedSubtitleIndex: Int? = null,
        disableHevc: Boolean = false
    ): MediaDto {
        requireLoggedIn()
        return EmbyApi.getPlaybackInfo(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            mediaId,
            startPosition,
            selectedAudioIndex,
            selectedSubtitleIndex,
            disableHevc
        )
    }

    /**
     * 上报播放进度
     */
    suspend fun reportPlaybackProgress(body: Any) {
        requireLoggedIn()
        EmbyApi.reportPlaybackProgress(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            body
        )
    }

    /**
     * 上报开始播放
     */
    suspend fun playing(body: Any) {
        requireLoggedIn()
        EmbyApi.playing(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            body
        )
    }

    /**
     * 上报停止播放
     */
    suspend fun stopped(body: Any) {
        requireLoggedIn()
        EmbyApi.stopped(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            body
        )
    }

    /**
     * 停止活动编码
     */
    suspend fun stopActiveEncodings(playSessionId: String?) {
        requireLoggedIn()
        EmbyApi.stopActiveEncodings(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            playSessionId
        )
    }

    /**
     * 获取播放会话列表
     */
    suspend fun getPlayingSessions(): List<SessionDto> {
        requireLoggedIn()
        return EmbyApi.getPlayingSessions(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId
        )
    }

    // === 收藏 ===

    /**
     * 添加收藏
     */
    suspend fun addToFavorites(itemId: String): Boolean {
        requireLoggedIn()
        return EmbyApi.addToFavorites(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            itemId
        )
    }

    /**
     * 取消收藏
     */
    suspend fun removeFromFavorites(itemId: String): Boolean {
        requireLoggedIn()
        return EmbyApi.removeFromFavorites(
            context,
            session.serverUrl!!,
            session.apiKey!!,
            session.deviceId,
            session.userId!!,
            itemId
        )
    }

    // === 内部方法 ===

    private fun requireLoggedIn() {
        require(session.serverUrl != null) { "Not logged in: serverUrl is null" }
        require(session.apiKey != null) { "Not logged in: apiKey is null" }
        require(session.userId != null) { "Not logged in: userId is null" }
    }

    companion object {
        @Volatile
        private var instance: EmbyRepository? = null

        fun getInstance(context: Context): EmbyRepository {
            return instance ?: synchronized(this) {
                instance ?: EmbyRepository(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 重置实例（用于测试或账号切换后的完全重置）
         */
        fun reset() {
            synchronized(this) {
                instance = null
            }
        }
    }
}
