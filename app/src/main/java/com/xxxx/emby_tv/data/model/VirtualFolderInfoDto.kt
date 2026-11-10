package com.xxxx.emby_tv.data.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Emby 媒体库信息（GET /Library/VirtualFolders/Query）。
 * VirtualFolders 是 Emby API 对"媒体库"的官方叫法，对应网页后台
 * 「控制台 → 媒体库」里的真实库。
 *
 * 注意：LibraryOptions 的结构在不同版本间不一致（官方 beta 文档标注为数组，
 * 实测 4.10 稳定版返回单对象），这里两种都兼容；
 * 修改后回传时必须保持与服务端返回一致的包装结构（见 [libraryOptionsIsArray]）
 */
data class VirtualFolderInfoDto(
    @SerializedName("Name") val name: String = "",
    @SerializedName("ItemId") val itemId: String = "",
    @SerializedName("Id") val id: String = "",
    @SerializedName("CollectionType") val collectionType: String? = null,
    @SerializedName("LibraryOptions") private val libraryOptionsRaw: JsonElement? = null
) {
    /** 该库的完整 LibraryOptions（兼容对象与数组两种返回，取对象本体） */
    val libraryOptions: JsonObject?
        get() = when {
            libraryOptionsRaw?.isJsonArray == true ->
                libraryOptionsRaw.asJsonArray.firstOrNull()
                    ?.takeIf { it.isJsonObject }?.asJsonObject
            libraryOptionsRaw?.isJsonObject == true -> libraryOptionsRaw.asJsonObject
            else -> null
        }

    /** 服务端返回的 LibraryOptions 是否为数组包装（POST 回传时需按相同结构） */
    val libraryOptionsIsArray: Boolean
        get() = libraryOptionsRaw?.isJsonArray == true

    /** 用于 POST /Library/VirtualFolders/LibraryOptions 的库标识（ItemId 优先，兼容 Id） */
    val effectiveItemId: String get() = itemId.ifEmpty { id }
}

