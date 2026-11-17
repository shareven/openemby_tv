package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto

/**
 * Emby BaseItemDto 数据模型
 * 对应 Emby API 返回的基础项目信息
 */
data class BaseItemDto(
    @SerializedName("Name") val name: String? = null,
    @SerializedName("SeriesName") val seriesName: String? = null,
    @SerializedName("OriginalTitle") val originalTitle: String? = null,
    @SerializedName("ServerId") val serverId: String? = null,
    @SerializedName("SeriesId") val seriesId: String? = null,
    @SerializedName("Id") val id: String? = null,
    @SerializedName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerializedName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerializedName("ParentPrimaryImageItemId") val parentPrimaryImageItemId: String? = null,
    @SerializedName("Etag") val etag: String? = null,
    @SerializedName("DateCreated") val dateCreated: String? = null,
    @SerializedName("DateModified") val dateModified: String? = null,
    @SerializedName("CanDelete") val canDelete: Boolean? = null,
    @SerializedName("CanDownload") val canDownload: Boolean? = null,
    @SerializedName("PresentationUniqueKey") val presentationUniqueKey: String? = null,
    @SerializedName("SupportsSync") val supportsSync: Boolean? = null,
    @SerializedName("Container") val container: String? = null,
    @SerializedName("SortName") val sortName: String? = null,
    @SerializedName("ForcedSortName") val forcedSortName: String? = null,
    @SerializedName("PremiereDate") val premiereDate: String? = null,
    @SerializedName("ExternalUrls") val externalUrls: List<BaseExternalUrlDto>? = null,
    @SerializedName("ProductionLocations") val productionLocations: List<String>? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("OfficialRating") val officialRating: String? = null,
    @SerializedName("Overview") val overview: String? = null,
    @SerializedName("Taglines") val taglines: List<String>? = null,
    @SerializedName("Genres") val genres: List<String>? = null,
    @SerializedName("CommunityRating") val communityRating: Double? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("Size") val size: Long? = null,
    @SerializedName("FileName") val fileName: String? = null,
    @SerializedName("Bitrate") val bitrate: Int? = null,
    @SerializedName("ProductionYear") val productionYear: Int? = null,
    @SerializedName("RemoteTrailers") val remoteTrailers: List<RemoteTrailer>? = null,
    @SerializedName("ProviderIds") val providerIds: Map<String, String>? = null,
    @SerializedName("IsFolder") val isFolder: Boolean? = null,
     @SerializedName("ParentId") val parentId: String? = null,
     @SerializedName("Type") val type: String? = null,
     @SerializedName("Role") val role: String? = null,
     @SerializedName("People") val people: List<PersonInfo>? = null,
    @SerializedName("Studios") val studios: List<NameGuidPairDto>? = null,
    @SerializedName("GenreItems") val genreItems: List<NameGuidPairDto>? = null,
    @SerializedName("TagItems") val tagItems: List<NameGuidPairDto>? = null,
    @SerializedName("LocalTrailerCount") val localTrailerCount: Int? = null,
    @SerializedName("UserData") val userData: UserDataDto? = null,
    @SerializedName("DisplayPreferencesId") val displayPreferencesId: String? = null,
    @SerializedName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerializedName("PartCount") val partCount: Int? = null,
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerializedName("ParentThumbImageTag") val parentThumbImageTag: String? = null,
    @SerializedName("ParentPrimaryImageTag") val parentPrimaryImageTag: String? = null,
    @SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerializedName("ParentBackdropImageTags") val parentBackdropImageTags: List<String>? = null,
    @SerializedName("MediaType") val mediaType: String? = null,
    @SerializedName("LockedFields") val lockedFields: List<String>? = null,
    @SerializedName("LockData") val lockData: Boolean? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("Height") val height: Int? = null,
    @SerializedName("PlayAccess") val playAccess: String? = null,
    @SerializedName("Number") val number: String? = null,
    @SerializedName("ChannelId") val channelId: String? = null,
    @SerializedName("Guid") val guid: String? = null,
    @SerializedName("HomePageUrl") val homePageUrl: String? = null,
     @SerializedName("IndexNumber") val indexNumber: Int? = null,
     @SerializedName("ParentIndexNumber") val parentIndexNumber: Int? = null,
     @SerializedName("Status") val status: String? = null,
     @SerializedName("SeasonName") val seasonName: String? = null,
     @SerializedName("SeasonId") val seasonId: String? = null,
     @SerializedName("Album") val album: String? = null,
     @SerializedName("AlbumId") val albumId: String? = null,
     @SerializedName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
     @SerializedName("MediaStreams") val mediaStreams: List<MediaStreamDto>? = null,
     @SerializedName("MediaSources") val mediaSources: List<MediaSourceInfoDto>? = null,
     @SerializedName("VideoType") val videoType: String? = null,
     @SerializedName("CriticRating") val criticRating: Float? = null,
     @SerializedName("SeriesStudio") val seriesStudio: String? = null,
    @SerializedName("PlayedPercentage") val playedPercentage: Float? = null,
    @SerializedName("LatestItems") val latestItems: List<BaseItemDto>? = null
){
    val playbackProgress: Float
        get() {
            val fromApi = playedPercentage
            if (fromApi != null) {
                return (fromApi / 100f).coerceIn(0f, 1f)
            }

            val total = runTimeTicks ?: 0L
            if (total <= 0L) return 0f

            val position = userData?.playbackPositionTicks ?: 0L
            return (position.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val playbackProgressPercent: Int
        get() = (playbackProgress * 100f).toInt().coerceIn(0, 100)


    val  isSeries: Boolean
        get() = type == "Series"
        
}

/**
 * 远程预告片数据模型
 */
data class RemoteTrailer(
    @SerializedName("Url") val url: String? = null
)

/**
 * 人物信息数据模型
 */
data class PersonInfo(
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Role") val role: String? = null,
    @SerializedName("PrimaryImageTag") val primaryImageTag: String? = null
)