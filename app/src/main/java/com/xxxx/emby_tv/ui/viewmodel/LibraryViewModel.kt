package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    companion object {
        private const val TAG = "LibraryViewModel"
        const val PAGE_SIZE = 36

        val SORT_OPTIONS = listOf(
            "SortName" to R.string.sort_name,
            "ProductionYear,PremiereDate,SortName" to R.string.sort_premiere_date,
            "DateCreated,SortName" to R.string.sort_date_added,
            "DateModified,SortName" to R.string.sort_date_modified,
            "DateLastSaved,SortName" to R.string.sort_date_last_saved,
            "CommunityRating,SortName" to R.string.sort_community_rating,
            "CriticRating,SortName" to R.string.sort_rating,
            "Runtime,SortName" to R.string.sort_runtime,
            "DatePlayed,SortName" to R.string.sort_date_played,
            "ProductionYear,SortName" to R.string.sort_year,
            "OfficialRating,SortName" to R.string.sort_official_rating,
            "Studio,SortName" to R.string.sort_studio,
            "Resolution,SortName" to R.string.sort_resolution,
            "Framerate,SortName" to R.string.sort_framerate,
            "Random" to R.string.sort_random
        )

        val FILTER_OPTIONS = listOf(
            null to R.string.filter_all,
            "IsPlayed" to R.string.filter_watched,
            "IsUnplayed" to R.string.filter_unwatched,
            "IsFavorite" to R.string.filter_favorite
        )
    }

    var libraryItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set

    var totalCount by mutableIntStateOf(0)
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    var hasMoreData by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var currentParentId by mutableStateOf("")
        private set
    var currentType by mutableStateOf("")
        private set

    var currentSortBy by mutableStateOf("SortName")
    var currentSortOrder by mutableStateOf("Ascending")
    var currentFilter by mutableStateOf<String?>(null)

    fun loadItems(parentId: String, type: String) {
        if (parentId == currentParentId && type == currentType && libraryItems != null) {
            return
        }

        currentParentId = parentId
        currentType = type
        currentPage = 0
        hasMoreData = true

        fetchItems()
    }

    fun updateSortAndFilter(sortBy: String, sortOrder: String, filter: String?) {
        currentSortBy = sortBy
        currentSortOrder = sortOrder
        currentFilter = filter
        currentPage = 0
        hasMoreData = true

        fetchItems()
    }

    private fun fetchItems() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            libraryItems = null

            try {
                val (items, total) = withContext(Dispatchers.IO) {
                    repository.getLibraryList(
                        currentParentId, currentType, 0, PAGE_SIZE,
                        currentSortBy, currentSortOrder, currentFilter
                    )
                }
                libraryItems = items
                totalCount = total
                currentPage = 1
                hasMoreData = items.size < total

                ErrorHandler.logDebug(TAG, "加载完成: ${items.size}/$total 条数据")
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "加载媒体库失败", e)
                errorMessage = ErrorHandler.getFriendlyMessage(e)
                libraryItems = emptyList()
                hasMoreData = false
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMoreData || isLoading) {
            return
        }

        viewModelScope.launch {
            isLoadingMore = true

            try {
                val startIndex = currentPage * PAGE_SIZE
                val (newItems, total) = withContext(Dispatchers.IO) {
                    repository.getLibraryList(
                        currentParentId, currentType, startIndex, PAGE_SIZE,
                        currentSortBy, currentSortOrder, currentFilter
                    )
                }

                if (newItems.isNotEmpty()) {
                    libraryItems = (libraryItems ?: emptyList()) + newItems
                    totalCount = total
                    currentPage++
                    hasMoreData = (libraryItems?.size ?: 0) < total

                    ErrorHandler.logDebug(TAG, "加载更多完成: ${libraryItems?.size}/$total 条数据")
                } else {
                    hasMoreData = false
                }
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "加载更多失败", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun refresh() {
        val parentId = currentParentId
        val type = currentType
        currentParentId = ""
        currentType = ""
        loadItems(parentId, type)
    }

    fun clear() {
        libraryItems = null
        currentParentId = ""
        currentType = ""
        currentPage = 0
        totalCount = 0
        hasMoreData = true
    }
}
