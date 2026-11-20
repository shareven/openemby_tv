package com.xxxx.emby_tv.data.repository

import android.content.Context
import android.util.Log
import android.webkit.URLUtil
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.remote.HttpClient
import com.xxxx.emby_tv.data.remote.subtitle.OnlineSubtitleFile
import com.xxxx.emby_tv.data.remote.subtitle.OnlineSubtitleProvider
import com.xxxx.emby_tv.data.remote.subtitle.OnlineSubtitleQuery
import com.xxxx.emby_tv.data.remote.subtitle.OnlineSubtitleResult
import com.xxxx.emby_tv.data.remote.subtitle.OpenSubtitlesProvider
import com.xxxx.emby_tv.data.remote.subtitle.ThunderFileHasher
import com.xxxx.emby_tv.data.remote.subtitle.ThunderSubtitleProvider
import com.xxxx.emby_tv.data.remote.subtitle.normalizeOnlineSubtitleFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 客户端在线字幕仓库。
 *
 * 与 EmbyRepository 的服务器字幕搜索并存：
 * - 由 App 直连迅雷 / OpenSubtitles 等站点，不依赖服务器插件
 * - 下载的字幕保存在应用私有目录 files/subtitles/{itemId}/，
 *   以本地外挂轨方式交给 media3 加载（见 SubtitleConfigBuilder）
 */
class OnlineSubtitleRepository private constructor(
    private val context: Context,
    private val prefs: PreferencesManager
) {

    companion object {
        private const val TAG = "OnlineSubRepo"
        private const val SUBTITLE_DIR = "subtitles"
        /** 本地外挂字幕使用的轨道索引基数，避免与服务器媒体流索引冲突 */
        const val LOCAL_TRACK_INDEX_BASE = 9000

        @Volatile
        private var instance: OnlineSubtitleRepository? = null

        fun getInstance(context: Context): OnlineSubtitleRepository {
            return instance ?: synchronized(this) {
                instance ?: OnlineSubtitleRepository(
                    context.applicationContext,
                    PreferencesManager(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    /** 已启用的站点列表（顺序即搜索结果合并顺序） */
    fun enabledProviders(): List<OnlineSubtitleProvider> {
        val list = mutableListOf<OnlineSubtitleProvider>()
        if (prefs.thunderSubtitleEnabled) {
            list.add(ThunderSubtitleProvider.create(context))
        }
        val osKey = prefs.openSubtitlesApiKey
        if (prefs.openSubtitlesEnabled && osKey.isNotBlank()) {
            list.add(
                OpenSubtitlesProvider.create(
                    context,
                    osKey,
                    prefs.openSubtitlesUsername,
                    prefs.openSubtitlesPassword
                )
            )
        }
        return list
    }

    /**
     * 并行搜索所有已启用站点并合并结果。
     *
     * @param providers 参与搜索的站点（空则取全部启用站点）
     * @param cid 迅雷 CID（调用方对播放流预计算，可为 null）
     * @param keyword 用户搜索词；空时用 query.filename/title
     */
    suspend fun search(
        query: OnlineSubtitleQuery,
        providers: List<OnlineSubtitleProvider> = enabledProviders(),
        cid: String? = null,
        keyword: String? = null
    ): List<OnlineSubtitleResult> = coroutineScope {
        val activeKeyword = keyword?.takeIf { it.isNotBlank() }
        val effective = OnlineSubtitleQuery(
            filename = activeKeyword ?: query.filename,
            title = activeKeyword ?: query.title,
            language = query.language,
            cid = cid,
            imdbId = query.imdbId,
            tmdbId = query.tmdbId,
            season = query.season,
            episode = query.episode
        )
        val supported = providers.filter { it.supportsLanguage(query.language) }
        if (supported.isEmpty()) return@coroutineScope emptyList()

        // 并行搜索；单站点失败不影响其他站点（唯一站点时保留错误上抛给 UI 提示）
        val results = supported.map { provider ->
            async(Dispatchers.IO) {
                try {
                    provider.search(effective)
                } catch (e: Exception) {
                    Log.w(TAG, "[${provider.id}] search failed: ${e.message}")
                    if (e.message?.startsWith("opensubtitles_") == true &&
                        supported.size == 1
                    ) throw e
                    emptyList()
                }
            }
        }.flatMap { deferred ->
            try {
                deferred.await()
            } catch (e: Exception) {
                Log.w(TAG, "provider await failed: ${e.message}")
                if (e.message?.startsWith("opensubtitles_") == true &&
                    supported.size == 1
                ) throw e
                emptyList()
            }
        }

        // 合并排序：哈希命中 > 匹配所选语言 > 下载数
        // 语言权重跟随用户选择（选英语时英语字幕排前，而不是固定简体优先）
        val targetLanguage = query.language
        results.sortedWith(
            compareByDescending<OnlineSubtitleResult> { it.isHashMatch }
                .thenByDescending { languageMatchScore(it.language, targetLanguage) }
                .thenByDescending { it.downloadCount ?: 0 }
        )
    }

    /**
     * 结果语言与所选语言的匹配度打分。
     *
     * 各站返回的语言写法不一（迅雷是“简体/繁体/英文”，OpenSubtitles 是
     * "zh-cn"/"en" 等代码），用关键词集合做宽松匹配。
     */
    private fun languageMatchScore(raw: String?, target: String): Int {
        if (raw.isNullOrBlank()) return 0
        val value = raw.lowercase()
        val keywords: List<String> = when (target) {
            "zh-CN" -> listOf("简体", "简中", "zh-cn", "zh-hans", "simplified", "chs")
            "zh-TW" -> listOf("繁体", "繁中", "zh-tw", "zh-hant", "traditional", "cht")
            "eng" -> listOf("english", "eng", "英文", "英语")
            "jpn" -> listOf("japanese", "jpn", "日语")
            "kor" -> listOf("korean", "kor", "韩语")
            "fra" -> listOf("french", "fra", "fre", "法语")
            "deu" -> listOf("german", "deu", "ger", "德语")
            "spa" -> listOf("spanish", "spa", "西班牙语")
            "rus" -> listOf("russian", "rus", "俄语")
            "por" -> listOf("portuguese", "por", "葡萄牙语")
            else -> listOf(target.lowercase())
        }
        return if (keywords.any { value.contains(it) }) 2 else 0
    }

    /**
     * 下载字幕并保存到本地。
     *
     * 下载后按实际内容嗅探一次格式（个别源扩展名与内容不符，如 .ass 实为 srt），
     * 纠正后再落盘，避免 media3 按错误 MIME 解析导致字幕不显示。
     *
     * @return 保存后的文件
     */
    suspend fun downloadAndSave(
        result: OnlineSubtitleResult,
        itemId: String
    ): SavedLocalSubtitle = withContext(Dispatchers.IO) {
        val provider = enabledProviders().firstOrNull { it.id == result.providerId }
            ?: throw IllegalStateException("provider not enabled: ${result.providerId}")
        var file = provider.download(result)
        val sniffed = sniffSubtitleFormat(file.content)
        if (sniffed != null && sniffed != file.format) {
            Log.i(TAG, "format corrected by content sniff: ${file.format} -> $sniffed")
            file = file.copy(
                format = sniffed,
                fileName = file.fileName.substringBeforeLast('.') + "." + sniffed
            )
        }
        saveLocalSubtitle(itemId, file)
    }

    /** 保存字幕文件到 files/subtitles/{itemId}/，返回落盘信息 */
    suspend fun saveLocalSubtitle(
        itemId: String,
        file: OnlineSubtitleFile
    ): SavedLocalSubtitle = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "$SUBTITLE_DIR/${sanitizeSegment(itemId)}")
        if (!dir.exists()) dir.mkdirs()
        val target = uniqueFile(dir, file.fileName)
        target.writeBytes(file.content)
        Log.i(TAG, "subtitle saved: ${target.absolutePath} (${file.content.size} bytes)")
        SavedLocalSubtitle(
            file = target,
            fileName = target.name,
            format = file.format,
            index = -1 // 由播放页分配
        )
    }

    /** 列出某媒体已下载的本地字幕（重启播放后自动挂载用） */
    fun listCachedSubtitles(itemId: String): List<SavedLocalSubtitle> {
        val dir = File(context.filesDir, "$SUBTITLE_DIR/${sanitizeSegment(itemId)}")
        // 只挂载内置播放器可显示的格式，避免异常文件混入
        val files = dir.listFiles { f ->
            f.isFile && normalizeOnlineSubtitleFormat(f.extension) != null
        }?.sortedBy { it.name } ?: return emptyList()
        return files.mapIndexed { i, f ->
            SavedLocalSubtitle(
                file = f,
                fileName = f.name,
                format = f.extension.lowercase(),
                index = LOCAL_TRACK_INDEX_BASE + i
            )
        }
    }

    /** 对播放地址预计算迅雷 CID（失败/不支持 Range 返回 null） */
    suspend fun computeCid(videoUrl: String?): String? {
        if (videoUrl.isNullOrBlank() || !URLUtil.isHttpUrl(videoUrl) && !URLUtil.isHttpsUrl(videoUrl)) {
            return null
        }
        // HLS 清单不是原始文件，取样哈希无意义，直接跳过
        if (videoUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            return null
        }
        return ThunderFileHasher.computeCid(videoUrl, HttpClient.getClient(context))
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val safe = fileName.replace(Regex("[\\\\/]"), "_")
        var candidate = File(dir, safe)
        var n = 1
        while (candidate.exists()) {
            val name = safe.substringBeforeLast('.')
            val ext = safe.substringAfterLast('.', "srt")
            candidate = File(dir, "${name}_${n++}.$ext")
        }
        return candidate
    }

    private fun sanitizeSegment(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64).ifBlank { "default" }

    /**
     * 按内容嗅探字幕格式（只看开头，区分 ASS/VTT/SRT）。
     * 无法识别时返回 null，沿用提供方声明的格式。
     */
    private fun sniffSubtitleFormat(content: ByteArray): String? {
        if (content.isEmpty()) return null
        // 去掉可能的 BOM 后取头部文本
        val offset = when {
            content.size >= 3 && content[0] == 0xEF.toByte() &&
                content[1] == 0xBB.toByte() && content[2] == 0xBF.toByte() -> 3
            else -> 0
        }
        val head = String(
            content, offset,
            minOf(2048, content.size - offset), Charsets.UTF_8
        ).trimStart()
        return when {
            head.startsWith("[Script Info]") -> "ass"
            head.startsWith("WEBVTT") -> "vtt"
            Regex("^\\d+\\s*\\r?\\n\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}\\s*-->")
                .containsMatchIn(head) -> "srt"
            else -> null
        }
    }

    data class SavedLocalSubtitle(
        val file: File,
        val fileName: String,
        val format: String,
        /** 播放页分配的轨道索引（本地轨从 LOCAL_TRACK_INDEX_BASE 起） */
        var index: Int
    )
}
