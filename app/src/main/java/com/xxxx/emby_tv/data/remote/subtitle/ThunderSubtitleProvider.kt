package com.xxxx.emby_tv.data.remote.subtitle

import android.net.Uri
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.xxxx.emby_tv.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.content.Context

/**
 * 迅雷字幕源（客户端直连，无需 API Key）。
 *
 * 接口与 Emby 服务端插件 MeiamSub.Thunder 使用同一公开端点：
 *   GET https://api-shoulei-ssl.xunlei.com/oracle/subtitle?name={文件名}
 * 返回 JSON 小写字段（code=0 与 data[]：name/simple_name/url/ext/cid/languages/score），
 * 下载为 url 直链。
 *
 * 排序规则（同 MeiamSub）：URL 去重 → CID 命中优先 → 简体中文优先 → 取前 20。
 * smi/sub 等内置播放器无法显示的格式直接过滤。
 */
class ThunderSubtitleProvider private constructor(
    private val client: OkHttpClient
) : OnlineSubtitleProvider {

    companion object {
        const val ID = "thunder"
        private const val TAG = "ThunderSubProvider"
        private const val API_URL = "https://api-shoulei-ssl.xunlei.com/oracle/subtitle"
        private const val TIMEOUT_MS = 20_000L
        private const val MAX_RESULTS = 20

        private val gson = com.google.gson.Gson()

        fun create(context: Context): ThunderSubtitleProvider =
            ThunderSubtitleProvider(
                HttpClient.getClient(context).newBuilder()
                    .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()
            )
    }

    override val id = ID

    /** 迅雷以中文字幕为主，语言过滤交由结果本身的简体优先排序 */
    override fun supportsLanguage(language: String): Boolean = true

    override suspend fun search(query: OnlineSubtitleQuery): List<OnlineSubtitleResult> =
        withContext(Dispatchers.IO) {
            val keyword = query.filename?.takeIf { it.isNotBlank() }
                ?: query.title?.takeIf { it.isNotBlank() }
                ?: return@withContext emptyList()
            val cid = query.cid
            try {
                val url = "$API_URL?name=${Uri.encode(keyword)}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "OpenEmbyTV")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "search failed: HTTP ${response.code}")
                        return@withContext emptyList()
                    }
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val root = gson.fromJson(body, ThunderResponse::class.java)
                    if (root?.code != 0) {
                        Log.w(TAG, "search returned code=${root?.code}")
                        return@withContext emptyList()
                    }
                    mapResults(root.data.orEmpty(), cid)
                }
            } catch (e: Exception) {
                Log.w(TAG, "search error: ${e.message}")
                emptyList()
            }
        }

    private fun mapResults(
        items: List<ThunderSubtitleItem>,
        cid: String?
    ): List<OnlineSubtitleResult> {
        return items.asSequence()
            .filter { !it.name.isNullOrBlank() && !it.url.isNullOrBlank() }
            .mapNotNull { item ->
                val format = normalizeOnlineSubtitleFormat(item.ext)
                if (format == null) null   // smi/sub 等不支持的格式直接不展示
                else item to format
            }
            .distinctBy { it.first.url!!.lowercase() }
            .sortedWith { a, b ->
                val hashA = !cid.isNullOrEmpty() && cid.equals(a.first.cid, ignoreCase = true)
                val hashB = !cid.isNullOrEmpty() && cid.equals(b.first.cid, ignoreCase = true)
                if (hashA != hashB) {
                    return@sortedWith if (hashA) -1 else 1
                }
                val scA = a.first.languagesOrNull()?.any { it.contains("简体") } == true
                val scB = b.first.languagesOrNull()?.any { it.contains("简体") } == true
                if (scA != scB) {
                    return@sortedWith if (scA) -1 else 1
                }
                (b.first.score ?: 0).compareTo(a.first.score ?: 0)
            }
            .take(MAX_RESULTS)
            .mapIndexed { index, (item, format) ->
                val langs = item.languagesOrNull()?.joinToString("/")
                OnlineSubtitleResult(
                    providerId = ID,
                    id = item.url!!,
                    // simple_name（如“简体[SRT]沙丘2(2024)”）比完整发布名更适合 TV 展示
                    name = item.simpleName?.takeIf { it.isNotBlank() } ?: item.name!!,
                    language = langs?.takeIf { it.isNotBlank() },
                    format = format,
                    isHashMatch = !cid.isNullOrEmpty() && cid.equals(item.cid, ignoreCase = true),
                    downloadCount = item.score?.takeIf { it > 0 },
                    payload = ThunderDownloadPayload(
                        url = item.url!!,
                        format = format,
                        fileName = item.name ?: item.simpleName
                    )
                )
            }
            .toList()
    }

    override suspend fun download(result: OnlineSubtitleResult): OnlineSubtitleFile =
        withContext(Dispatchers.IO) {
            val payload = result.payload as? ThunderDownloadPayload
                ?: ThunderDownloadPayload(
                    url = result.id,
                    format = result.format,
                    fileName = result.name
                )
            val request = Request.Builder()
                .url(payload.url)
                .header("User-Agent", "OpenEmbyTV")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP Error: ${response.code}")
                }
                val raw = response.body?.bytes()
                    ?: throw IllegalStateException("empty subtitle body")
                if (raw.isEmpty()) throw IllegalStateException("empty subtitle body")
                val sourceName = payload.fileName ?: result.name
                OnlineSubtitleFile(
                    format = payload.format,
                    fileName = sanitizeFileName(sourceName, payload.format),
                    content = SubtitleEncodingConverter.toUtf8(raw)
                )
            }
        }

    /** 用结果名生成安全文件名，非法字符替换为下划线；已是该扩展名时不再追加 */
    private fun sanitizeFileName(name: String, format: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(80)
            .ifBlank { "subtitle" }
        return if (cleaned.endsWith(".$format", ignoreCase = true)) cleaned
        else "$cleaned.$format"
    }

    private data class ThunderResponse(
        @SerializedName("code") val code: Int? = null,
        @SerializedName("data") val data: List<ThunderSubtitleItem>? = null
    )

    private data class ThunderSubtitleItem(
        @SerializedName("cid") val cid: String? = null,
        @SerializedName("gcid") val gcid: String? = null,
        @SerializedName("url") val url: String? = null,
        @SerializedName("ext") val ext: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("simple_name") val simpleName: String? = null,
        @SerializedName("languages") val languages: List<String>? = null,
        @SerializedName("langs") val langs: String? = null,
        @SerializedName("score") val score: Int? = null
    ) {
        fun languagesOrNull(): List<String>? =
            languages?.takeIf { it.isNotEmpty() }
                ?: langs?.takeIf { it.isNotBlank() }?.split(",")
    }

    private data class ThunderDownloadPayload(
        val url: String,
        val format: String,
        val fileName: String? = null
    )
}
