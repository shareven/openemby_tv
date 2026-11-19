package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Emby MediaStream 数据模型
 * 对应 Emby API 返回的媒体流信息
 */
data class MediaStreamDto(
    @SerializedName("Codec") val codec: String? = null,
    @SerializedName("CodecTag") val codecTag: String? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("ColorRange") val colorRange: String? = null,
    @SerializedName("ColorSpace") val colorSpace: String? = null,
    @SerializedName("ColorTransfer") val colorTransfer: String? = null,
    @SerializedName("ColorPrimaries") val colorPrimaries: String? = null,
    @SerializedName("Comment") val comment: String? = null,
    @SerializedName("TimeBase") val timeBase: String? = null,
    @SerializedName("CodecTimeBase") val codecTimeBase: String? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("VideoRange") val videoRange: String? = null,
    @SerializedName("VideoRangeType") val videoRangeType: String? = null,
    @SerializedName("VideoDoViTitle") val videoDoViTitle: String? = null,
    @SerializedName("LocalizedUndefined") val localizedUndefined: String? = null,
    @SerializedName("LocalizedDefault") val localizedDefault: String? = null,
    @SerializedName("LocalizedForced") val localizedForced: String? = null,
    @SerializedName("DisplayTitle") val displayTitle: String? = null,
    @SerializedName("NalLengthSize") val nalLengthSize: String? = null,
    @SerializedName("IsInterlaced") val isInterlaced: Boolean? = null,
    @SerializedName("IsAVC") val isAvC: Boolean? = null,
    @SerializedName("ChannelLayout") val channelLayout: String? = null,
    @SerializedName("BitRate") val bitRate: Int? = null,
    @SerializedName("BitDepth") val bitDepth: Int? = null,
    @SerializedName("RefFrames") val refFrames: Int? = null,
    @SerializedName("PacketLength") val packetLength: Int? = null,
    @SerializedName("Channels") val channels: Int? = null,
    @SerializedName("SampleRate") val sampleRate: Int? = null,
    @SerializedName("IsDefault") val isDefault: Boolean? = null,
    @SerializedName("IsForced") val isForced: Boolean? = null,
    @SerializedName("Height") val height: Int? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("AverageFrameRate") val averageFrameRate: Double? = null,
    @SerializedName("RealFrameRate") val realFrameRate: Double? = null,
    @SerializedName("Profile") val profile: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("AspectRatio") val aspectRatio: String? = null,
    @SerializedName("Index") val index: Int? = null,
    @SerializedName("Score") val score: Int? = null,
    @SerializedName("IsExternal") val isExternal: Boolean? = null,
    @SerializedName("DeliveryMethod") val deliveryMethod: String? = null,
    @SerializedName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerializedName("IsExternalUrl") val isExternalUrl: Boolean? = null,
    @SerializedName("IsTextSubtitleStream") val isTextSubtitleStream: Boolean? = null,
    @SerializedName("SupportsExternalStream") val supportsExternalStream: Boolean? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("ExtendedVideoSubType") val extendedVideoSubType: String? = null,
    @SerializedName("ExtendedVideoSubTypeDescription") val extendedVideoSubTypeDescription: String? = null,
    @SerializedName("ExtendedVideoType") val extendedVideoType: String? = null,
    @SerializedName("PixelFormat") val pixelFormat: String? = null,
    @SerializedName("Level") val level: Int? = null,
    @SerializedName("IsAnamorphic") val isAnamorphic: Boolean? = null,
    // 客户端在线字幕：本地保存路径（非服务器字段，仅播放端合成轨道使用）
    val localSubtitlePath: String? = null
)