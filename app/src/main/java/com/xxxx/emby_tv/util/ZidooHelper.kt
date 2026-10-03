package com.xxxx.emby_tv.util

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Zidoo 播放器本地控制 API（官方开发者接口，端口 9529）
 *
 * 在 Zidoo 机器上运行本 App 时，可用官方 API 把 Emby 直链推给 Zidoo 系统播放器，
 * 并通过轮询播放状态实现观看进度回传（Zidoo 播放器提供 DV 双层直通与音轨源码输出）。
 * 文档：https://www.zidoo.tv/Support/developer
 */
object ZidooHelper {
    private const val TAG = "ZidooHelper"
    private const val BASE = "http://127.0.0.1:9529"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var apiAvailable: Boolean? = null

    /** 是否为 Zidoo 设备（厂商名判断 + API 探测双保险） */
    fun isZidooDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: ""
        return manufacturer.contains("zidoo", ignoreCase = true) ||
            Build.MODEL?.contains("zidoo", ignoreCase = true) == true ||
            Build.HARDWARE?.contains("rtd", ignoreCase = true) == true &&
            Build.BOARD?.contains("zidoo", ignoreCase = true) == true
    }

    /** 探测 Zidoo 控制 API 是否可用（结果缓存） */
    fun isApiAvailable(): Boolean {
        apiAvailable?.let { return it }
        val available = try {
            httpGet("$BASE/ZidooControlCenter/getModel") != null
        } catch (e: Exception) {
            false
        }
        apiAvailable = available
        Log.i(TAG, "Zidoo API 探测: ${if (available) "可用" else "不可用"}")
        return available
    }

    /** 推送 URL 给 Zidoo 播放器开始播放 */
    fun openFile(url: String, playMode: Int = 0): Boolean {
        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
        val result = httpGet("$BASE/ZidooFileControl/openFile?path=$encoded&videoplaymode=$playMode")
        return result != null && result.optInt("status") == 200
    }

    /** 跳转到指定位置（毫秒） */
    fun seekTo(positionMs: Long): Boolean {
        val result = httpGet("$BASE/ZidooVideoPlay/seekTo?positon=$positionMs")
        return result != null && result.optInt("status") == 200
    }

    /**
     * 获取播放状态
     * @return Triple(位置ms, 时长ms, 是否正在播放)；无播放会话时返回 null
     */
    fun getPlayStatus(): PlayStatus? {
        val json = httpGet("$BASE/ZidooVideoPlay/getPlayStatus") ?: return null
        if (json.optInt("status") != 200) return null
        val position = json.optLong("position", -1L)
        val duration = json.optLong("duration", -1L)
        if (position < 0 && duration < 0) return null
        // status 字段在不同固件含义不一，播放/暂停以外视为非播放中
        val playing = when (json.opt("status2") ?: json.opt("playState") ?: json.opt("state")) {
            is String -> listOf("play", "playing").any {
                (json.opt("status2") ?: json.opt("playState") ?: json.opt("state"))
                    .toString().contains(it, true)
            }
            is Int -> true // 数值状态码默认视为播放中，由上层用 position 变化判断
            else -> true
        }
        return PlayStatus(
            positionMs = position.coerceAtLeast(0),
            durationMs = duration.coerceAtLeast(0),
            isPlaying = playing
        )
    }

    data class PlayStatus(
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean
    )

    private fun httpGet(url: String): JSONObject? {
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                if (body.isBlank()) return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            null
        }
    }
}
