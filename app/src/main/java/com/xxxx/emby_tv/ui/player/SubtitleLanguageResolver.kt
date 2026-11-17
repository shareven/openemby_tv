package com.xxxx.emby_tv.ui.player

import android.content.Context
import android.os.Build
import com.xxxx.emby_tv.data.repository.EmbyRepository
import java.util.Locale

/**
 * 在线字幕搜索的语言解析工具。
 *
 * 默认语言优先级：
 * 1. 当前媒体所属媒体库的 LibraryOptions.SubtitleDownloadLanguages 第一项
 * 2. 系统语言
 *
 * 注意：中文必须区分简体/繁体；Emby 中常见写法有 chi、zho、zh、zh-CN、
 * zh-Hans、zh-TW、zh-Hant 等，这里统一归一到 zh-CN / zh-TW。
 */
object SubtitleLanguageResolver {

    /** 常用字幕语言（顺序即界面展示顺序） */
    val commonLanguages: List<String> = listOf(
        "zh-CN", "zh-TW", "eng", "jpn", "kor", "fra", "deu", "spa", "rus", "por"
    )

    private val genericChineseCodes = setOf("zh", "chi", "zho", "chinese", "中文")

    private val aliases: Map<String, String> = mapOf(
        // 简体中文
        "zh-cn" to "zh-CN",
        "zh-hans" to "zh-CN",
        "zh-sg" to "zh-CN",
        "zh-chs" to "zh-CN",
        "chs" to "zh-CN",
        "simplified chinese" to "zh-CN",
        "简体中文" to "zh-CN",
        // 繁体中文
        "zh-tw" to "zh-TW",
        "zh-hk" to "zh-TW",
        "zh-mo" to "zh-TW",
        "zh-hant" to "zh-TW",
        "zh-cht" to "zh-TW",
        "cht" to "zh-TW",
        "traditional chinese" to "zh-TW",
        "繁体中文" to "zh-TW",
        // 其他常用语言
        "en" to "eng", "eng" to "eng", "english" to "eng", "英语" to "eng",
        "ja" to "jpn", "jpn" to "jpn", "japanese" to "jpn", "日语" to "jpn",
        "ko" to "kor", "kor" to "kor", "korean" to "kor", "韩语" to "kor",
        "fr" to "fra", "fra" to "fra", "fre" to "fra", "french" to "fra", "法语" to "fra",
        "de" to "deu", "deu" to "deu", "ger" to "deu", "german" to "deu", "德语" to "deu",
        "es" to "spa", "spa" to "spa", "spanish" to "spa", "西班牙语" to "spa",
        "ru" to "rus", "rus" to "rus", "russian" to "rus", "俄语" to "rus",
        "pt" to "por", "por" to "por", "portuguese" to "por", "葡萄牙语" to "por",
        "it" to "ita", "ita" to "ita", "italian" to "ita", "意大利语" to "ita",
        "th" to "tha", "tha" to "tha", "thai" to "tha", "泰语" to "tha",
        "vi" to "vie", "vie" to "vie", "vietnamese" to "vie", "越南语" to "vie",
        "ar" to "ara", "ara" to "ara", "arabic" to "ara", "阿拉伯语" to "ara",
        "hi" to "hin", "hin" to "hin", "hindi" to "hin", "印地语" to "hin",
        "nl" to "nld", "nld" to "nld", "dut" to "nld", "dutch" to "nld", "荷兰语" to "nld",
        "pl" to "pol", "pol" to "pol", "polish" to "pol", "波兰语" to "pol",
        "tr" to "tur", "tur" to "tur", "turkish" to "tur", "土耳其语" to "tur",
        "sv" to "swe", "swe" to "swe", "swedish" to "swe", "瑞典语" to "swe",
        "da" to "dan", "dan" to "dan", "danish" to "dan", "丹麦语" to "dan",
        "fi" to "fin", "fin" to "fin", "finnish" to "fin", "芬兰语" to "fin",
        "no" to "nor", "nb" to "nor", "nor" to "nor", "norwegian" to "nor", "挪威语" to "nor",
        "cs" to "ces", "ces" to "ces", "cze" to "ces", "czech" to "ces", "捷克语" to "ces",
        "hu" to "hun", "hun" to "hun", "hungarian" to "hun", "匈牙利语" to "hun",
        "el" to "ell", "ell" to "ell", "gre" to "ell", "greek" to "ell", "希腊语" to "ell",
        "he" to "heb", "heb" to "heb", "hebrew" to "heb", "希伯来语" to "heb",
        "id" to "ind", "ind" to "ind", "indonesian" to "ind", "印度尼西亚语" to "ind",
        "ms" to "msa", "msa" to "msa", "may" to "msa", "malay" to "msa", "马来语" to "msa",
        "uk" to "ukr", "ukr" to "ukr", "ukrainian" to "ukr", "乌克兰语" to "ukr",
        "ro" to "ron", "ron" to "ron", "rum" to "ron", "romanian" to "ron", "罗马尼亚语" to "ron",
        "bg" to "bul", "bul" to "bul", "bulgarian" to "bul", "保加利亚语" to "bul"
    )

    /**
     * 读取系统语言，中文按地区区分简繁。
     */
    fun systemLanguage(context: Context): String {
        val locale = currentLocale(context)
        if (locale.language.equals("zh", ignoreCase = true)) {
            return if (isTraditionalChineseRegion(locale.country)) "zh-TW" else "zh-CN"
        }
        return normalizeLanguage(locale.language, "eng")
    }

    /**
     * 将服务端返回的语言代码/名称归一为在线字幕搜索使用的代码。
     *
     * @param fallback 泛化中文（chi/zh/zho）无法判断简繁时，按 fallback 的简繁取向处理
     */
    fun normalizeLanguage(raw: String?, fallback: String = "eng"): String {
        val value = raw?.trim()?.lowercase()?.replace('_', '-')
        if (value.isNullOrEmpty()) return fallback

        if (value in genericChineseCodes || value.startsWith("zh-")) {
            // 先判断明确带 script/region 的写法
            return when {
                value.contains("hant") || value.contains("tw") ||
                        value.contains("hk") || value.contains("mo") -> "zh-TW"
                value.contains("hans") || value.contains("cn") ||
                        value.contains("sg") || value.contains("chs") -> "zh-CN"
                // zh / chi / zho：沿用 fallback 的简繁
                fallback == "zh-TW" -> "zh-TW"
                else -> "zh-CN"
            }
        }

        aliases[value]?.let { return it }

        // 带地区/脚本的未知语言（如 en-US、fr-FR）：优先按主语言归一
        val base = value.substringBefore('-')
        if (base != value) {
            aliases[base]?.let { return it }
        }

        // 已经是无法识别的 2/3 字母代码时原样返回，交给服务端判断
        return value
    }

    /**
     * 解析在线字幕搜索的默认语言。
     *
     * 读取媒体库的 SubtitleDownloadLanguages 需要用户能看到该库的 LibraryOptions；
     * 非管理员或接口不可用时静默回退系统语言，不阻塞搜索。
     */
    suspend fun resolveDefaultLanguage(
        context: Context,
        repository: EmbyRepository,
        itemId: String
    ): String {
        val system = systemLanguage(context)
        if (itemId.isBlank()) return system

        return try {
            val ancestorIds = repository.getItemAncestors(itemId)
                .mapNotNull { it.id }
                .toSet()
            val folders = repository.getVirtualFolders()
            val folder = folders.firstOrNull {
                it.effectiveItemId.isNotBlank() && ancestorIds.contains(it.effectiveItemId)
            } ?: folders.firstOrNull {
                it.id.isNotBlank() && ancestorIds.contains(it.id)
            } ?: folders.singleOrNull()

            normalizeLanguage(folder?.firstSubtitleDownloadLanguage(), system)
        } catch (_: Exception) {
            system
        }
    }

    private fun currentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    private fun isTraditionalChineseRegion(country: String): Boolean {
        return country.uppercase(Locale.US) in setOf("TW", "HK", "MO")
    }

    private fun com.xxxx.emby_tv.data.model.VirtualFolderInfoDto.firstSubtitleDownloadLanguage(): String? {
        val element = libraryOptions?.get("SubtitleDownloadLanguages") ?: return null
        return when {
            element.isJsonArray -> element.asJsonArray
                .firstOrNull { !it.isJsonNull }
                ?.asString
            element.isJsonPrimitive -> element.asString
            else -> null
        }
    }
}
