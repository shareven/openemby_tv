package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel
 * 
 * 职责：
 * - 加载继续观看列表
 * - 加载媒体库最新内容
 * - 加载收藏列表
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    // === 首页数据 ===
    var resumeItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var libraryLatestItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var favoriteItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set

    // === 加载状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadData()
    }

    /**
     * 加载首页数据
     */
    fun loadData() {
        if (!repository.isLoggedIn) {
            resumeItems = emptyList()
            libraryLatestItems = emptyList()
            favoriteItems = emptyList()
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val resumeDeferred = async {
                    try { repository.getResumeItems() }
                    catch (e: Exception) { errorMessage = e.message; emptyList() }
                }
                val latestDeferred = async {
                    try { repository.getLatestItems() }
                    catch (e: Exception) { errorMessage = e.message; emptyList() }
                }
                val favDeferred = async {
                    try { repository.getFavoriteItems() }
                    catch (e: Exception) { errorMessage = e.message; emptyList() }
                }

                resumeItems = resumeDeferred.await()
                libraryLatestItems = latestDeferred.await()
                favoriteItems = favDeferred.await()
            } catch (e: Exception) {
                if (errorMessage == null) errorMessage = e.message
                resumeItems = emptyList()
                libraryLatestItems = emptyList()
                favoriteItems = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() = loadData()

    /**
     * 获取下一集信息
     */
    fun playNextUp(seriesId: String, onResult: (BaseItemDto) -> Unit) {
        viewModelScope.launch {
            try {
                val itemsArray = repository.getShowsNextUp(seriesId)
                val items = itemsArray.ifEmpty {
                    repository.getSeriesList(seriesId)
                }
                if (items.isNotEmpty()) {
                    onResult(items.first())
                }
            } catch (e: Exception) {
                ErrorHandler.logError("HomeViewModel", "加载数据失败", e)
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
