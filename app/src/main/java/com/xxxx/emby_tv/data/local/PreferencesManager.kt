package com.xxxx.emby_tv.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 封装
 * 管理应用的本地偏好设置
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("emby_tv", Context.MODE_PRIVATE)

    // === 主题设置 ===

    var themeId: String
        get() = prefs.getString(KEY_THEME_ID, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID
        set(value) = prefs.edit().putString(KEY_THEME_ID, value).apply()

    // === 播放器设置 ===

    var preferDirectPlay: Boolean
        get() = prefs.getBoolean(KEY_PREFER_DIRECT_PLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_DIRECT_PLAY, value).apply()

    var disableHevc: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_HEVC, false)
        set(value) = prefs.edit().putBoolean(KEY_DISABLE_HEVC, value).apply()

    // === 排序设置 ===

    var librarySortBy: String
        get() = prefs.getString(KEY_LIBRARY_SORT_BY, "SortName") ?: "SortName"
        set(value) = prefs.edit().putString(KEY_LIBRARY_SORT_BY, value).apply()

    var librarySortOrder: String
        get() = prefs.getString(KEY_LIBRARY_SORT_ORDER, "Ascending") ?: "Ascending"
        set(value) = prefs.edit().putString(KEY_LIBRARY_SORT_ORDER, value).apply()

    // === 代理设置 ===

    var proxyEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROXY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROXY_ENABLED, value).apply()

    var proxyType: String
        get() = prefs.getString(KEY_PROXY_TYPE, DEFAULT_PROXY_TYPE) ?: DEFAULT_PROXY_TYPE
        set(value) = prefs.edit().putString(KEY_PROXY_TYPE, value).apply()

    var proxyHost: String
        get() = prefs.getString(KEY_PROXY_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROXY_HOST, value).apply()

    var proxyPort: Int
        get() = prefs.getInt(KEY_PROXY_PORT, DEFAULT_PROXY_PORT)
        set(value) = prefs.edit().putInt(KEY_PROXY_PORT, value).apply()

    var proxyUsername: String
        get() = prefs.getString(KEY_PROXY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROXY_USERNAME, value).apply()

    var proxyPassword: String
        get() = prefs.getString(KEY_PROXY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROXY_PASSWORD, value).apply()

    // === 片头跳过设置 ===

    var autoSkipIntro: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SKIP_INTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SKIP_INTRO, value).apply()

    // === 倍速设置 ===

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()

    // === 缓冲设置 ===

    var minBufferMs: Int
        get() = prefs.getInt(KEY_MIN_BUFFER_MS, DEFAULT_MIN_BUFFER_MS)
        set(value) = prefs.edit().putInt(KEY_MIN_BUFFER_MS, value).apply()
    var maxBufferMs: Int
        get() = prefs.getInt(KEY_MAX_BUFFER_MS, DEFAULT_MAX_BUFFER_MS)
        set(value) = prefs.edit().putInt(KEY_MAX_BUFFER_MS, value).apply()

    var playbackBufferMs: Int
        get() = prefs.getInt(KEY_PLAYBACK_BUFFER_MS, DEFAULT_PLAYBACK_BUFFER_MS)
        set(value) = prefs.edit().putInt(KEY_PLAYBACK_BUFFER_MS, value).apply()

    var rebufferMs: Int
        get() = prefs.getInt(KEY_REBUFFER_MS, DEFAULT_REBUFFER_MS)
        set(value) = prefs.edit().putInt(KEY_REBUFFER_MS, value).apply()

    var bufferSizeBytes: Int
        get() = prefs.getInt(KEY_BUFFER_SIZE_BYTES, DEFAULT_BUFFER_SIZE_BYTES)
        set(value) = prefs.edit().putInt(KEY_BUFFER_SIZE_BYTES, value).apply()

    /**
     * 获取缓冲设置默认值
     */
    fun getBufferDefaults(): BufferDefaults {
        return BufferDefaults(
            minBufferMs = DEFAULT_MIN_BUFFER_MS,
            maxBufferMs = DEFAULT_MAX_BUFFER_MS,
            playbackBufferMs = DEFAULT_PLAYBACK_BUFFER_MS,
            rebufferMs = DEFAULT_REBUFFER_MS,
            bufferSizeBytes = DEFAULT_BUFFER_SIZE_BYTES
        )
    }

    /**
     * 重置所有缓冲设置为默认值
     */
    fun resetBufferDefaults() {
        minBufferMs = DEFAULT_MIN_BUFFER_MS
        maxBufferMs = DEFAULT_MAX_BUFFER_MS
        playbackBufferMs = DEFAULT_PLAYBACK_BUFFER_MS
        rebufferMs = DEFAULT_REBUFFER_MS
        bufferSizeBytes = DEFAULT_BUFFER_SIZE_BYTES
    }

    data class BufferDefaults(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val playbackBufferMs: Int,
        val rebufferMs: Int,
        val bufferSizeBytes: Int
    )

    // === 通用方法 ===

    fun getString(key: String, defaultValue: String? = null): String? {
        return prefs.getString(key, defaultValue)
    }

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LIBRARY_SORT_BY = "library_sort_by"
        private const val KEY_LIBRARY_SORT_ORDER = "library_sort_order"

        private const val KEY_THEME_ID = "selected_theme_id"
        private const val KEY_PREFER_DIRECT_PLAY = "prefer_direct_play"
        private const val KEY_DISABLE_HEVC = "disable_hevc"
        private const val KEY_AUTO_SKIP_INTRO = "auto_skip_intro"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"

        private const val KEY_PROXY_ENABLED = "proxy_enabled"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_USERNAME = "proxy_username"
        private const val KEY_PROXY_PASSWORD = "proxy_password"

        private const val DEFAULT_THEME_ID = "purple"
        const val DEFAULT_PROXY_TYPE = "http"
        const val DEFAULT_PROXY_PORT = 1080

        private const val KEY_MIN_BUFFER_MS = "min_buffer_ms"
        private const val KEY_MAX_BUFFER_MS = "max_buffer_ms"
        private const val KEY_PLAYBACK_BUFFER_MS = "playback_buffer_ms"
        private const val KEY_REBUFFER_MS = "rebuffer_ms"
        private const val KEY_BUFFER_SIZE_BYTES = "buffer_size_bytes"

        // 缓冲设置默认值
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
        const val DEFAULT_MIN_BUFFER_MS = 45_000
        const val DEFAULT_MAX_BUFFER_MS = 120_000
        const val DEFAULT_PLAYBACK_BUFFER_MS = 3_000
        const val DEFAULT_REBUFFER_MS = 5_000
        const val DEFAULT_BUFFER_SIZE_BYTES = 134_217_728 // 128MB
    }
}
