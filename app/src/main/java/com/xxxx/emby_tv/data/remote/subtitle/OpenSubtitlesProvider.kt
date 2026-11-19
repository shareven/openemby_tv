package com.xxxx.emby_tv.data.remote.subtitle

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.xxxx.emby_tv.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenSubtitles 字幕源（官方 REST API，需免费注册获取 API Key）。
 *
 *   搜索: GET  /api/v1/subtitles?query=&languages=
 *   下载: POST /api/v1/download {file_id} → {link} → GET link
 *
 * 仅返回 srt/ass/ssa/vtt 等内置播放器可显示的格式，smi/sub 等直接过滤。
 */
class OpenSubtitlesProvider private constructor(
    private val apiKey: String,
    private val client: OkHttpClient
) : OnlineSubtitleProvider {

    companion object {
        const val ID = "opensubtitles"
        private const val TAG = "OpenSubsProvider"
        private const val API_BASE = "https://api.opensubtitles.com/api/v1"
        private const val TIMEOUT_MS = 20_000L
        private const val MAX_RESULTS = 20

        private val gson = Gson()

        /** SubtitleLanguageResolver 归一代码 → OpenSubtitles 语言代码 */
        private val languageCodes = mapOf(
            "zh-CN" to "zh-cn",
            "zh-TW" to "zh-tw",
            "eng" to "en",
            "jpn" to "ja",
            "kor" to "ko",
            "fra" to "fr",
            "deu" to "de",
            "spa" to "es",
            "rus" to "ru",
            "por" to "pt"
        )

        fun create(context: Context, apiKey: String): OpenSubtitlesProvider =
            OpenSubtitlesProvider(
                apiKey,
                HttpClient.getClient(context).newBuilder()
                    .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()
            )
    }

    override val id = ID

    override fun supportsLanguage(language: String): Boolean =
        languageCodes.containsKey(language)

    override suspend fun search(query: OnlineSubtitleQuery): List<OnlineSubtitleResult> =
        withContext(Dispatchers.IO) {
            val keyword = query.filename?.takeIf { it.isNotBlank() }
                ?: query.title?.takeIf { it.isNotBlank() }
                ?: return@withContext emptyList()
            val langCode = languageCodes[query.language] ?: return@withContext emptyList()
            try {
                val url = "$API_BASE/subtitles" +
                    "?query=${encode(keyword)}" +
                    "&languages=$langCode"
                val request = baseRequest(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "search failed: HTTP ${response.code}")
                        // 401/403：API Key 无效/无权限消费，上抛给 UI 提示
                        if (response.code == 401 || response.code == 403) {
                            throw IllegalStateException("opensubtitles_key_invalid")
                        }
                        return@withContext emptyList()
                    }
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val root = gson.fromJson(body, OsSearchResponse::class.java)
                    mapResults(root?.data.orEmpty())
                }
            } catch (e: IllegalStateException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "search error: ${e.message}")
                emptyList()
            }
        }

    private fun mapResults(items: List<OsSubtitleEntry>): List<OnlineSubtitleResult> {
        return items.asSequence()
            .mapNotNull { entry ->
                val attrs = entry.attributes ?: return@mapNotNull null
                val file = attrs.files?.firstOrNull { !it.fileId.isNullOrEmpty() }
                    ?: return@mapNotNull null
                val fileName = file.fileName ?: attrs.release ?: return@mapNotNull null
                // 从文件名/发布名推断格式；OS 文件以 srt 为主
                val format = normalizeOnlineSubtitleFormat(fileName.substringAfterLast('.', ""))
                    ?: attrs.subFormat?.let { normalizeOnlineSubtitleFormat(it) }
                    ?: "srt"
                OsMapped(entry, file, fileName, format)
            }
            .take(MAX_RESULTS)
            .map { m ->
                OnlineSubtitleResult(
                    providerId = ID,
                    id = m.file.fileId!!,
                    name = m.fileName,
                    language = m.attrs.language,
                    format = m.format,
                    downloadCount = m.attrs.downloadCount?.takeIf { it > 0 },
                    rating = m.attrs.ratings?.takeIf { it > 0f },
                    payload = OsDownloadPayload(
                        fileId = m.file.fileId!!,
                        fileName = m.fileName,
                        format = m.format
                    )
                )
            }
            .toList()
    }

    override suspend fun download(result: OnlineSubtitleResult): OnlineSubtitleFile =
        withContext(Dispatchers.IO) {
            val payload = result.payload as? OsDownloadPayload
                ?: OsDownloadPayload(
                    fileId = result.id,
                    fileName = result.name,
                    format = result.format
                )
            // 1. 换取下载直链
            val body = gson.toJson(OsDownloadRequest(fileId = payload.fileId.toIntOrNull() ?: 0))
                .toRequestBody("application/json".toMediaType())
            val downloadRequest = baseRequest("$API_BASE/download").post(body).build()
            val link = client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP Error: ${response.code}")
                }
                val text = response.body?.string() ?: throw IllegalStateException("empty body")
                gson.fromJson(text, OsDownloadResponse::class.java)?.link
            }
            if (link.isNullOrBlank()) throw IllegalStateException("no download link")

            // 2. 下载字幕内容
            val fileRequest = Request.Builder().url(link).get().build()
            client.newCall(fileRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP Error: ${response.code}")
                }
                val raw = response.body?.bytes() ?: throw IllegalStateException("empty body")
                if (raw.isEmpty()) throw IllegalStateException("empty body")
                val format = normalizeOnlineSubtitleFormat(
                    link.substringAfterLast('.', payload.format)
                ) ?: payload.format
                OnlineSubtitleFile(
                    format = format,
                    fileName = sanitizeFileName(payload.fileName, format),
                    content = SubtitleEncodingConverter.toUtf8(raw)
                )
            }
        }

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Api-Key", apiKey)
            .header("User-Agent", "OpenEmbyTV v1")

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun sanitizeFileName(name: String, format: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(80)
            .ifBlank { "subtitle" }
        return if (cleaned.endsWith(".$format", ignoreCase = true)) cleaned
        else "$cleaned.$format"
    }

    private data class OsMapped(
        val entry: OsSubtitleEntry,
        val file: OsFileEntry,
        val fileName: String,
        val format: String
    ) {
        val attrs get() = entry.attributes!!
    }

    // === API 响应模型 ===

    private data class OsSearchResponse(
        @SerializedName("data") val data: List<OsSubtitleEntry>? = null
    )

    private data class OsSubtitleEntry(
        @SerializedName("attributes") val attributes: OsAttributes? = null
    )

    private data class OsAttributes(
        @SerializedName("language") val language: String? = null,
        @SerializedName("release") val release: String? = null,
        @SerializedName("sub_format") val subFormat: String? = null,
        @SerializedName("ratings") val ratings: Float? = null,
        @SerializedName("download_count") val downloadCount: Int? = null,
        @SerializedName("files") val files: List<OsFileEntry>? = null
    )

    private data class OsFileEntry(
        @SerializedName("file_id") val fileId: String? = null,
        @SerializedName("file_name") val fileName: String? = null
    )

    private data class OsDownloadResponse(
        @SerializedName("link") val link: String? = null,
        @SerializedName("file_name") val fileName: String? = null
    )

    private data class OsDownloadRequest(
        @SerializedName("file_id") val fileId: Int
    )

    private data class OsDownloadPayload(
        val fileId: String,
        val fileName: String,
        val format: String
    )
}
