package com.xxxx.emby_tv.data.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Emby 虚拟媒体库信息（GET /Library/VirtualFolders/Query）
 * LibraryOptions 保留原始 JsonObject，修改标记片头时整体回传，避免覆盖库的其他配置
 */
data class VirtualFolderInfoDto(
    @SerializedName("Name") val name: String = "",
    @SerializedName("ItemId") val itemId: String = "",
    @SerializedName("CollectionType") val collectionType: String? = null,
    @SerializedName("LibraryOptions") val libraryOptions: JsonObject? = null
)
