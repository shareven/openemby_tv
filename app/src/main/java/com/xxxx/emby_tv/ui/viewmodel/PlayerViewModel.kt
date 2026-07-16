package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.DvProfileInfo
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.getSupportedDolbyVisionProfiles
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 定义一个全局的静态变量作为内存缓存
private var cachedDvProfiles: List<DvProfileInfo>? = null
/**
 * 播放器 ViewModel
 * 
 * 职责：
 * - 获取播放信息
 * - 管理播放状态
 * - 上报播放进度
 * - 管理音轨/字幕轨道
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    // === 播放信息 ===
    var playbackInfo by mutableStateOf<MediaDto?>(null)
        private set
    var currentMediaSource by mutableStateOf<MediaSourceInfoDto?>(null)
        private set

    // === 轨道信息 ===
    var audioTracks by mutableStateOf<List<MediaStreamDto>>(emptyList())
        private set
    var subtitleTracks by mutableStateOf<List<MediaStreamDto>>(emptyList())
        private set
    var selectedAudioIndex by mutableStateOf<Int?>(null)
        private set
    var selectedSubtitleIndex by mutableStateOf<Int?>(null)
        private set

    // === 播放会话 ===
    var playSessionId: String? = null
        private set
    var mediaSourceId: String? = null
        private set

    // === 加载状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // === 当前播放的媒体 ID ===
    var currentMediaId: String = ""
        private set


    private val _supportedDvProfiles = MutableStateFlow<List<DvProfileInfo>>(cachedDvProfiles ?: emptyList())
    val supportedDvProfiles = _supportedDvProfiles.asStateFlow()

    init {
        if (cachedDvProfiles == null) {
            viewModelScope.launch(Dispatchers.Default) {
                val result = getSupportedDolbyVisionProfiles()
                cachedDvProfiles = result
                _supportedDvProfiles.value = result
            }
        }
    }

    /**
     * 加载播放信息
     */
    fun loadPlaybackInfo(
        mediaId: String,
        startPosition: Long,
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
        disableHevc: Boolean = false,
        onSuccess: (MediaSourceInfoDto?) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        currentMediaId = mediaId

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val info = withContext(Dispatchers.IO) {
                    repository.getPlaybackInfo(
                        mediaId,
                        startPosition,
                        audioIndex,
                        subtitleIndex,
                        disableHevc
                    )
                }

                playbackInfo = info
                currentMediaSource = info.mediaSources?.firstOrNull()

                // playSessionId 在 MediaDto 级别
                playSessionId = info.playSessionId
                
                currentMediaSource?.let { source ->
                    mediaSourceId = source.id

                    // 解析轨道信息
                    val streams = source.mediaStreams ?: emptyList()
                    audioTracks = streams.filter { it.type == "Audio" }
                    subtitleTracks = streams.filter { it.type == "Subtitle" }

                    // 设置默认选中的轨道
                    selectedAudioIndex = audioIndex ?: source.defaultAudioStreamIndex
                    selectedSubtitleIndex = subtitleIndex ?: source.defaultSubtitleStreamIndex
                }

                withContext(Dispatchers.Main) {
                    isLoading = false
                    onSuccess(currentMediaSource)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = e.message
                    onError(e.message ?: "加载播放信息失败")
                }
            }
        }
    }

    /**
     * 上报开始播放
     */
    fun reportPlaying(
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int,
        playbackRate: Float = 1.0f
    ) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                val body = buildPlayingBody(mediaId, media, position, selectedSubtitleIndex, selectedAudioIndex, playbackRate)
                repository.playing(body)
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerViewModel", "操作失败", e)
            }
        }
    }

    /**
     * 上报播放进度
     */
    fun reportProgress(
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int,
        isPaused: Boolean = false
    ) {
        Log.e("reportProgress position", position.toString())
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                val body = buildProgressBody(mediaId, media, position, selectedSubtitleIndex, selectedAudioIndex, isPaused)
                repository.reportPlaybackProgress(body)
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerViewModel", "操作失败", e)
            }
        }
    }

    /**
     * 上报停止播放
     */
    fun reportStopped(
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int,
        playbackRate: Float = 1.0f
    ) {
        Log.e("reportStopped position", position.toString())
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                val body = buildStoppedBody(mediaId, media, position, selectedSubtitleIndex, selectedAudioIndex, playbackRate)
                repository.stopped(body)

                // 停止活动编码
                if(media.mediaSources?.firstOrNull()?.transcodingUrl != null){
                    media.playSessionId?.let { sessionId ->
                        repository.stopActiveEncodings(sessionId)
                    }
                }
            } catch (e: Exception) {
                ErrorHandler.logError("PlayerViewModel", "操作失败", e)
            }
        }
    }

    /**
     * 更新选中的轨道
     */
    fun updateSelectedTracks(audioIndex: Int?, subtitleIndex: Int?) {
        selectedAudioIndex = audioIndex
        selectedSubtitleIndex = subtitleIndex
    }

    /**
     * 重新加载播放信息（用于切换轨道后）
     */
    fun reloadWithTracks(
        startPosition: Long,
        audioIndex: Int?,
        subtitleIndex: Int?,
        disableHevc: Boolean = false,
        onSuccess: (MediaSourceInfoDto?) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        loadPlaybackInfo(
            currentMediaId,
            startPosition,
            audioIndex,
            subtitleIndex,
            disableHevc,
            onSuccess,
            onError
        )
    }

    /**
     * 清理播放状态
     */
    fun clear() {
        playbackInfo = null
        currentMediaSource = null
        audioTracks = emptyList()
        subtitleTracks = emptyList()
        selectedAudioIndex = null
        selectedSubtitleIndex = null
        playSessionId = null
        mediaSourceId = null
        currentMediaId = ""
    }

    // === 构建请求体 ===

    private fun buildPlayingBody(
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int,
        playbackRate: Float = 1.0f
    ): Map<String, Any?> {
        val ticks = position * 10000
        val playMethod = Utils.determinePlayMethod(media)
        val mediaSources = media.mediaSources
        val firstSource = mediaSources?.firstOrNull()
        val runTimeTicks = firstSource?.runTimeTicks ?: 0L
        val mediaSourceId = firstSource?.id ?: ""

        return mapOf(
            "VolumeLevel" to 100,
            "IsMuted" to false,
            "IsPaused" to false,
            "RepeatMode" to "RepeatNone",
            "Shuffle" to false,
            "SubtitleOffset" to 0,
            "PlaybackRate" to playbackRate,
            "MaxStreamingBitrate" to 200000000,
            "PositionTicks" to ticks,
            "PlaybackStartTimeTicks" to System.currentTimeMillis() * 10000,
            "SubtitleStreamIndex" to selectedSubtitleIndex,
            "AudioStreamIndex" to selectedAudioIndex,
            "BufferedRanges" to emptyList<Any>(),
            "SeekableRanges" to listOf(
                mapOf("start" to 0, "end" to runTimeTicks)
            ),
            "PlayMethod" to playMethod,
            "PlaySessionId" to (media.playSessionId ?: ""),
            "MediaSourceId" to mediaSourceId,
            "CanSeek" to true,
            "ItemId" to mediaId
        )
    }

    private fun buildProgressBody(
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int,
        isPaused: Boolean
    ): Map<String, Any?> {
        val ticks = position * 10000
        val playMethod = Utils.determinePlayMethod(media)
        val mediaSources = media.mediaSources
        val firstSource = mediaSources?.firstOrNull()
        val runTimeTicks = firstSource?.runTimeTicks ?: 0L
        val mediaSourceId = firstSource?.id ?: ""

        return mapOf(
            "VolumeLevel" to 100,
            "IsMuted" to false,
            "IsPaused" to isPaused,
            "RepeatMode" to "RepeatNone",
            "PositionTicks" to ticks,
            "PlaybackStartTimeTicks" to System.currentTimeMillis() * 10000,
            "SubtitleStreamIndex" to selectedSubtitleIndex,
            "AudioStreamIndex" to selectedAudioIndex,
            "BufferedRanges" to emptyList<Any>(),
            "SeekableRanges" to listOf(
                mapOf("start" to 0, "end" to runTimeTicks)
            ),
            "PlayMethod" to playMethod,
            "PlaySessionId" to (media.playSessionId ?: ""),
            "MediaSourceId" to mediaSourceId,
            "CanSeek" to true,
            "ItemId" to mediaId,
            "EventName" to "timeupdate"
        )
    }

    private fun buildStoppedBody(
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int,
        playbackRate: Float = 1.0f
    ): Map<String, Any?> {
        val ticks = position * 10000
        val playMethod = Utils.determinePlayMethod(media)
        val mediaSources = media.mediaSources
        val firstSource = mediaSources?.firstOrNull()
        val runTimeTicks = firstSource?.runTimeTicks ?: 0L
        val mediaSourceId = firstSource?.id ?: ""

        return mapOf(
            "VolumeLevel" to 100,
            "IsMuted" to false,
            "IsPaused" to true,
            "RepeatMode" to "RepeatNone",
            "Shuffle" to false,
            "SubtitleOffset" to 0,
            "PlaybackRate" to playbackRate,
            "MaxStreamingBitrate" to 200000000,
            "PositionTicks" to ticks,
            "PlaybackStartTimeTicks" to System.currentTimeMillis() * 10000,
            "SubtitleStreamIndex" to selectedSubtitleIndex,
            "AudioStreamIndex" to selectedAudioIndex,
            "BufferedRanges" to emptyList<Any>(),
            "SeekableRanges" to listOf(
                mapOf("start" to 0, "end" to runTimeTicks)
            ),
            "PlayMethod" to playMethod,
            "PlaySessionId" to (media.playSessionId ?: ""),
            "MediaSourceId" to mediaSourceId,
            "CanSeek" to true,
            "ItemId" to mediaId,
            "EventName" to "Stopped"
        )
    }
}
