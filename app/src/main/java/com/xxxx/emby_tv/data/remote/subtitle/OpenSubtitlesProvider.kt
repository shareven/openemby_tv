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
 *   登录: POST /api/v1/login {username,password} → {token}（可选，走账号配额）
 *   下载: POST /api/v1/download {file_id} → {link} → GET link
 *
 * 账号密码可选：填写后登录换 token，下载走账号自己的每日配额；
 * 不填则以匿名方式下载（配额按应用级 Key 限流）。
 * 账号密码只保存在本机，仅用于向 OpenSubtitles 官方 API 登录。
 *
 * 仅返回 srt/ass/ssa/vtt 等内置播放器可显示的格式，smi/sub 等直接过滤。
 */
class OpenSubtitlesProvider private constructor(
    private val apiKey: String,
    private val username: String,
    private val password: String,
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

        fun create(
            context: Context,
            apiKey: String,
            username: String = "",
            password: String = ""
        ): OpenSubtitlesProvider =
            OpenSubtitlesProvider(
                apiKey,
                username.trim(),
                password,
                HttpClient.getClient(context).newBuilder()
                    // OS 为海外 API（RTT 200ms+，限流时排队更久），全局 6s 读超时太紧，
                    // 这里放宽到 20s，避免响应稍慢就被判 timeout
                    .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()
            )
    }

    /** 登录换来的用户 token；null 表示未登录（未配置账号或尚未需要） */
    @Volatile
    private var authToken: String? = null

    override val id = ID

    override fun supportsLanguage(language: String): Boolean =
        languageCodes.containsKey(language)

    override suspend fun search(query: OnlineSubtitleQuery): List<OnlineSubtitleResult> =
        withContext(Dispatchers.IO) {
            val langCode = languageCodes[query.language] ?: return@withContext emptyList()
            val hasExternalIds = query.hasExternalIds
            val isEpisode = query.season != null || query.episode != null

            if (hasExternalIds) {
                // 第一层：外部 ID + 季/集（Emby 存剧级 ID 时精确）
                val primary = executeSearch(buildSearchUrl(query, langCode, withSeasonEpisode = true))
                // 剧集 0 结果：Emby 的 Episode 可能存的是「单集级」IMDb/TMDB，
                // OS 的 imdb_id+season 组合只认剧级 ID，去掉季集用单集 ID 直接匹配
                if (primary.isNotEmpty() || !isEpisode) return@withContext primary
                Log.i(TAG, "id+season empty, retry without season/episode")
                return@withContext executeSearch(
                    buildSearchUrl(query, langCode, withSeasonEpisode = false)
                )
            }

            // 无外部 ID：关键词兜底（无关键词直接放弃，避免纯语言搜索返回海量无关结果）
            val hasKeyword = !query.filename.isNullOrBlank() || !query.title.isNullOrBlank()
            if (!hasKeyword) return@withContext emptyList()
            executeSearch(buildSearchUrl(query, langCode, withSeasonEpisode = false))
        }

    /**
     * 组装搜索 URL。OS 网关对 query 参数要求字母序（x-os-rule: canonical）：
     * 乱序请求会 301 重定向，跟随时可能被 Cloudflare 拖住导致超时。
     * 用 sortedMapOf 自动按 key 字母序组装，直连 canonical 形式。
     */
    private fun buildSearchUrl(
        query: OnlineSubtitleQuery,
        langCode: String,
        withSeasonEpisode: Boolean
    ): String {
        val params = sortedMapOf<String, String>()
        if (withSeasonEpisode) {
            query.episode?.let { params["episode_number"] = it.toString() }
        }
        query.imdbId?.takeIf { it.isNotBlank() }?.let { params["imdb_id"] = it }
        params["languages"] = langCode
        query.tmdbId?.takeIf { it.isNotBlank() }?.let { params["tmdb_id"] = it }
        if ("imdb_id" !in params && "tmdb_id" !in params) {
            val keyword = query.filename?.takeIf { it.isNotBlank() }
                ?: query.title?.takeIf { it.isNotBlank() }
                ?: ""
            if (keyword.isNotEmpty()) params["query"] = encode(keyword)
        }
        if (withSeasonEpisode) {
            query.season?.let { params["season_number"] = it.toString() }
        }
        return "$API_BASE/subtitles?" +
            params.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    private fun executeSearch(url: String): List<OnlineSubtitleResult> {
        if (url.endsWith("?")) return emptyList()
        return try {
            val startMs = android.os.SystemClock.elapsedRealtime()
            val request = baseRequest(url).get().build()
            client.newCall(request).execute().use { response ->
                Log.i(
                    TAG,
                    "search http=${response.code} cost=${android.os.SystemClock.elapsedRealtime() - startMs}ms url=$url"
                )
                if (!response.isSuccessful) {
                    Log.w(TAG, "search failed: HTTP ${response.code}")
                    // 401/403：API Key 无效/无权限消费，上抛给 UI 提示
                    if (response.code == 401 || response.code == 403) {
                        throw IllegalStateException("opensubtitles_key_invalid")
                    }
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                val root = gson.fromJson(body, OsSearchResponse::class.java)
                mapResults(root?.data.orEmpty())
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "search error: ${e.javaClass.simpleName}: ${e.message}")
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
            // 1. 配置了账号则先登录备用（失败不阻断）
            ensureTokenOrNull()
            Log.i(TAG, "download start: fileId=${payload.fileId} (asInt=${payload.fileId.toIntOrNull()})")
            // 2. 换取下载直链。优先带账号 token：下载计入用户账号的记录与配额（5 次/24h）；
            //    未配置账号、登录失败或账号配额尽（406）时，匿名兜底（走 Key 自身配额，
            //    如 Under dev 级 Key 的 100 次/天）。带 token 401 视为过期，重登一次再试
            val body = gson.toJson(OsDownloadRequest(fileId = payload.fileId.toIntOrNull() ?: 0))
                .toRequestBody("application/json".toMediaType())
            var link: String? = null
            if (authToken != null) {
                link = requestDownloadLink(body, withToken = true)
                    ?: run {
                        ensureTokenOrNull(forceRefresh = true)
                        requestDownloadLink(body, withToken = true)
                    }
            }
            if (link == null) {
                link = requestDownloadLink(body, withToken = false)
            }
            // 账号与匿名池都拿不到直链：每日下载配额已用尽（OS 官方用 406 表示）
            if (link.isNullOrBlank()) throw IllegalStateException("opensubtitles_download_quota")

            // 3. 下载字幕内容
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

    /**
     * 换取下载直链。401（token 过期）/406（每日配额用尽）返回 null 交给上层换通道重试，
     * 429（请求过频）与其余错误上抛。
     */
    private fun requestDownloadLink(body: okhttp3.RequestBody, withToken: Boolean): String? {
        // 不走 baseRequest：token 的携带与否是配额池选择的关键，必须显式控制
        // Accept 头必须带：OS 的 WAF 对无 Accept 的 POST 一律返回 503
        val builder = Request.Builder()
            .url("$API_BASE/download")
            .header("Api-Key", apiKey)
            .header("User-Agent", "OpenEmbyTV v1")
            .header("Accept", "application/json")
        if (withToken) {
            authToken?.let { builder.header("Authorization", "Bearer $it") }
        }
        client.newCall(builder.post(body).build()).execute().use { response ->
            when (response.code) {
                // 401：token 过期，交给上层重登或换匿名通道
                401 -> {
                    Log.w(TAG, "download 401 (withToken=$withToken): token expired")
                    return null
                }
                // 406：每日下载配额用完（官方语义），body 含 requests/remaining/reset_time；
                // 返回 null 交给上层换通道（账号池尽 → Key 匿名池）
                406 -> {
                    val body = runCatching { response.body?.string() }.getOrNull()
                    Log.w(TAG, "download 406 (withToken=$withToken) quota: $body")
                    return null
                }
                // 429：请求过于频繁
                429 -> throw IllegalStateException("opensubtitles_rate_limited")
            }
            if (!response.isSuccessful) {
                val errBody = runCatching { response.body?.string() }.getOrNull()
                    ?.replace(Regex("\\s+"), " ")?.take(300)
                Log.w(TAG, "download failed: HTTP ${response.code} body=$errBody")
                throw IllegalStateException("HTTP Error: ${response.code}")
            }
            val text = response.body?.string() ?: throw IllegalStateException("empty body")
            return gson.fromJson(text, OsDownloadResponse::class.java)?.link
        }
    }

    /**
     * 账号密码已配置且无缓存 token 时登录。失败只记日志不上抛：
     * 匿名下载依然可用，避免账号输错导致整个字幕源不可用。
     */
    private fun ensureTokenOrNull(forceRefresh: Boolean = false) {
        if (username.isBlank() || password.isBlank()) return
        if (forceRefresh) authToken = null
        authToken?.let { return }
        try {
            val body = gson.toJson(OsLoginRequest(username = username, password = password))
                .toRequestBody("application/json".toMediaType())
            val request = baseRequest("$API_BASE/login").post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                val token = gson.fromJson(text, OsLoginResponse::class.java)?.token
                if (!response.isSuccessful || token.isNullOrBlank()) {
                    Log.w(TAG, "login failed: HTTP ${response.code}, using anonymous download")
                    return
                }
                authToken = token
                Log.i(TAG, "login ok, token cached (${username.length} chars user)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "login error: ${e.message}, using anonymous download")
        }
    }

    private fun baseRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Api-Key", apiKey)
            .header("User-Agent", "OpenEmbyTV v1")
            // Accept 头必须带：OS 的 WAF 对无 Accept 的请求可能返回 503
            .header("Accept", "application/json")
        authToken?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

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

    private data class OsLoginRequest(
        @SerializedName("username") val username: String,
        @SerializedName("password") val password: String
    )

    private data class OsLoginResponse(
        @SerializedName("token") val token: String? = null
    )

    private data class OsDownloadPayload(
        val fileId: String,
        val fileName: String,
        val format: String
    )
}
