package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Emby 在线字幕搜索结果
 * 对应 GET /Items/{Id}/RemoteSearch/Subtitles/{Language}
 */
data class RemoteSubtitleInfoDto(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("ProviderName") val providerName: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Format") val format: String? = null,
    @SerializedName("Author") val author: String? = null,
    @SerializedName("Comment") val comment: String? = null,
    @SerializedName("CommunityRating") val communityRating: Float? = null,
    @SerializedName("DownloadCount") val downloadCount: Int? = null,
    @SerializedName("IsHashMatch") val isHashMatch: Boolean? = null,
    @SerializedName("IsForced") val isForced: Boolean? = null,
    @SerializedName("IsHearingImpaired") val isHearingImpaired: Boolean? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("ThreeLetterISOLanguageName") val threeLetterISOLanguageName: String? = null
)

/**
 * 在线字幕下载结果
 * 对应 POST /Items/{Id}/RemoteSearch/Subtitles/{SubtitleId}
 */
data class SubtitleDownloadResultDto(
    @SerializedName("NewIndex") val newIndex: Int? = null
)
