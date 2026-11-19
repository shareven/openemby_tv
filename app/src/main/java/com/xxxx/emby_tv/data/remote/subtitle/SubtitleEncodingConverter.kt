package com.xxxx.emby_tv.data.remote.subtitle

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 字幕编码转换器：任意编码 → UTF-8。
 *
 * 字幕站下载的中文字幕常为 GBK/GB18030 编码，而 media3 的文本解析器
 * 只认 UTF-8（可带 BOM），直接加载会乱码或解析失败。
 * 策略（参考 MeiamSubtitles 的 SubtitleContentValidator）：
 * 1. UTF-8/UTF-16 BOM → 按 BOM 解码（去 BOM 后输出 UTF-8）
 * 2. 无 BOM → 先严格 UTF-8 校验，通过则原样
 * 3. 失败 → GB18030 兜底解码（覆盖 GBK/GB2312）
 */
object SubtitleEncodingConverter {

    private const val BOM_UTF8 = 0xEFBBBF
    private const val BOM_UTF16LE = 0xFFFE   // 文件字节序 FF FE
    private const val BOM_UTF16BE = 0xFEFF

    /** 转为 UTF-8 字节；输入为空或全部策略失败时原样返回 */
    fun toUtf8(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes

        // 1. BOM 检测
        val bom = readBom(bytes)
        when (bom) {
            BOM_UTF8 -> return stripAndReencode(bytes, 3, StandardCharsets.UTF_8)
            BOM_UTF16LE -> return stripAndReencode(bytes, 2, StandardCharsets.UTF_16LE)
            BOM_UTF16BE -> return stripAndReencode(bytes, 2, StandardCharsets.UTF_16BE)
        }

        // 2. 严格 UTF-8 校验（无 BOM 的合法 UTF-8 直接通过）
        if (isValidUtf8(bytes)) return bytes

        // 3. GB18030 兜底（GB18030 向下兼容 GBK/GB2312）
        return try {
            val text = String(bytes, Charset.forName("GB18030"))
            text.toByteArray(StandardCharsets.UTF_8)
        } catch (_: Exception) {
            bytes
        }
    }

    /** 快速校验整个字节数组是否为合法 UTF-8 */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        val n = bytes.size
        while (i < n) {
            val b0 = bytes[i].toInt() and 0xFF
            val len = when {
                b0 < 0x80 -> 1
                b0 < 0xC2 -> return false           // 连续字节/过长编码
                b0 < 0xE0 -> 2
                b0 < 0xF0 -> 3
                b0 < 0xF5 -> 4
                else -> return false
            }
            if (i + len > n) return false
            for (j in 1 until len) {
                if (bytes[i + j].toInt() and 0xC0 != 0x80) return false
            }
            i += len
        }
        return true
    }

    private fun readBom(bytes: ByteArray): Int {
        if (bytes.size >= 3) {
            val v = ((bytes[0].toInt() and 0xFF) shl 16) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                (bytes[2].toInt() and 0xFF)
            if (v == BOM_UTF8) return BOM_UTF8
        }
        if (bytes.size >= 2) {
            val v = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            if (v == 0xFFFE) return BOM_UTF16LE
            if (v == 0xFEFF) return BOM_UTF16BE
        }
        return -1
    }

    /** 去掉 BOM 后按指定字符集解码，再输出 UTF-8（不带 BOM） */
    private fun stripAndReencode(bytes: ByteArray, bomLength: Int, charset: Charset): ByteArray {
        return try {
            val text = String(bytes, bomLength, bytes.size - bomLength, charset)
            text.toByteArray(StandardCharsets.UTF_8)
        } catch (_: Exception) {
            bytes
        }
    }
}
