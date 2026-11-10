package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Emby User 数据模型
 * 对应 Emby API 返回的用户信息
 */
data class UserDto(
    @SerializedName("Id") val id: String = "",
    @SerializedName("ServerId") val serverId: String? = null,
    @SerializedName("Name") val name: String = "",
    @SerializedName("PrincipalName") val principalName: String? = null,
    @SerializedName("LastLoginDate") val lastLoginDate: String? = null,
    @SerializedName("LastActivityDate") val lastActivityDate: String? = null,
    @SerializedName("Configuration") val configuration: Map<String, Any>? = null,
    @SerializedName("Policy") val policy: Map<String, Any>? = null,
    @SerializedName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerializedName("HasConfiguredPassword") val hasConfiguredPassword: Boolean = false,
    @SerializedName("HasConfiguredEasyPassword") val hasConfiguredEasyPassword: Boolean = false,
    @SerializedName("EnableAutoLogin") val enableAutoLogin: Boolean? = null,
    @SerializedName("IsAdministrator") val isAdministrator: Boolean = false,
    @SerializedName("IsHidden") val isHidden: Boolean = false,
    @SerializedName("IsDisabled") val isDisabled: Boolean = false,
    @SerializedName("MaxActiveSessions") val maxActiveSessions: Int = 0,
    @SerializedName("AuthenticationProviderId") val authenticationProviderId: String? = null,
    @SerializedName("PasswordResetProviderId") val passwordResetProviderId: String? = null,
    @SerializedName("ExcludedSubFolders") val excludedSubFolders: List<String>? = null
)

/**
 * 公开用户信息（GET /Users/Public，无需认证）
 * Emby 官方登录页用它显示可选用户，用于发现服务器后预填用户名
 */
data class PublicUserDto(
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Id") val id: String? = null,
    @SerializedName("HasPassword") val hasPassword: Boolean = false
)