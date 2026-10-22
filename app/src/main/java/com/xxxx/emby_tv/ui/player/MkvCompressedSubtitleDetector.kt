package com.xxxx.emby_tv.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * MKV 字幕轨压缩检测器
 *
 * 背景：部分 MKV（老式打包工具产出）给字幕轨启用了 EBML ContentEncodings
 * （ContentCompression，算法缺省 = zlib）。media3 的 MatroskaExtractor 不支持
 * MKV 内容压缩，提取出的字幕样本仍是 zlib 原始字节，SsaParser 每行都因
 * “列数与 Format 不符”被跳过 → 选中内嵌字幕轨但整轨零字幕。
 * （真机复现：罗梅莉亚战记 S01E02 内嵌 ASS 即为此情况；同剧 E01 无压缩则正常。）
 *
 * 通过 HTTP Range 拉取 MKV 头部（Tracks 元素通常在前几十 KB），解析字幕轨
 * 是否携带 ContentEncodings。返回“文件内第 n 条字幕轨被压缩”的序号集合，
 * 供上层把该轨改由 Emby 服务器提取的 Stream.srt 外挂轨兜底显示。
 */
object MkvCompressedSubtitleDetector {

    private const val TAG = "MkvSubCompDetector"
    private const val PROBE_BYTES = 256 * 1024
    private const val TIMEOUT_MS = 5000L

    // EBML 元素 ID
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_TRACK_ENTRY = 0xAEL
    private const val ID_TRACK_NUMBER = 0xD7L
    private const val ID_CODEC_ID = 0x86L
    private const val ID_CONTENT_ENCODINGS = 0x6D80L
    private const val ID_CLUSTER = 0x1F43B675L

    /**
     * 探测 MKV 中启用了内容压缩的字幕轨。
     *
     * @return 被压缩的字幕轨序号集合（按文件内字幕轨出现顺序，从 0 计）。
     *         网络/解析异常或非 MKV 结构时返回空集合，调用方按原逻辑播放（无回归）。
     */
    suspend fun detect(videoUrl: String, client: OkHttpClient): Set<Int> =
        withContext(Dispatchers.IO) {
            val timedClient = client.newBuilder()
                .callTimeout(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            try {
                val request = Request.Builder()
                    .url(videoUrl)
                    .header("Range", "bytes=0-${PROBE_BYTES - 1}")
                    .build()
                timedClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptySet()
                    val body = response.body ?: return@withContext emptySet()
                    val buf = ByteArray(PROBE_BYTES)
                    var total = 0
                    body.byteStream().use { input ->
                        while (total < PROBE_BYTES) {
                            val n = input.read(buf, total, PROBE_BYTES - total)
                            if (n < 0) break
                            total += n
                        }
                    }
                    parseCompressedSubtitleOrdinals(buf, total)
                }
            } catch (e: IOException) {
                android.util.Log.w(TAG, "probe failed: ${e.message}")
                emptySet()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "probe error: ${e.message}")
                emptySet()
            }
        }

    /** 解析 EBML，返回带 ContentEncodings 的文本字幕轨序号 */
    private fun parseCompressedSubtitleOrdinals(buf: ByteArray, limit: Int): Set<Int> {
        if (limit < 16) return emptySet()
        val compressed = mutableSetOf<Int>()

        // 顶层：EBML 头之后是 Segment
        var pos = 0
        var segment: Elem? = null
        while (pos < limit - 1 && segment == null) {
            val e = readElem(buf, pos, limit, limit) ?: break
            if (e.id == ID_SEGMENT) segment = e
            pos = if (e.bodyEnd > pos) e.bodyEnd else pos + 1
        }
        val seg = segment ?: return emptySet()

        // Segment 内逐元素前进，找到 Tracks（遇到 Cluster 即止，Tracks 必在其前）
        var p = seg.bodyStart
        var tracks: Elem? = null
        while (p < seg.bodyEnd - 1) {
            val e = readElem(buf, p, seg.bodyEnd, limit) ?: break
            if (e.id == ID_TRACKS) {
                tracks = e
                break
            }
            if (e.id == ID_CLUSTER) break
            p = if (e.bodyEnd > p) e.bodyEnd else p + 1
        }
        val trk = tracks ?: return emptySet()

        // 遍历 TrackEntry，统计文本字幕轨并检查 ContentEncodings
        var q = trk.bodyStart
        var subtitleOrdinal = 0
        while (q < trk.bodyEnd - 1) {
            val entry = readElem(buf, q, trk.bodyEnd, limit) ?: break
            if (entry.id == ID_TRACK_ENTRY) {
                var trackNumber = -1L
                var codecId: String? = null
                var hasContentEncodings = false
                var r = entry.bodyStart
                while (r < entry.bodyEnd - 1) {
                    val c = readElem(buf, r, entry.bodyEnd, limit) ?: break
                    when (c.id) {
                        ID_TRACK_NUMBER -> trackNumber = readUint(buf, c.bodyStart, c.bodyEnd)
                        ID_CODEC_ID -> codecId = String(
                            buf, c.bodyStart,
                            (c.bodyEnd - c.bodyStart).coerceIn(0, 64),
                            StandardCharsets.US_ASCII
                        )
                        ID_CONTENT_ENCODINGS -> hasContentEncodings = true
                    }
                    r = if (c.bodyEnd > r) c.bodyEnd else r + 1
                }
                if (codecId?.startsWith("S_TEXT/") == true) {
                    if (hasContentEncodings) {
                        compressed.add(subtitleOrdinal)
                        android.util.Log.w(
                            TAG,
                            "subtitle track#$trackNumber ($codecId) is compressed -> route via server"
                        )
                    }
                    subtitleOrdinal++
                }
            }
            q = if (entry.bodyEnd > q) entry.bodyEnd else q + 1
        }
        return compressed
    }

    private class Elem(val id: Long, val bodyStart: Int, val bodyEnd: Int)

    /** 读取一个 EBML 元素；越界/损坏返回 null。未知大小按容器末尾截断 */
    private fun readElem(buf: ByteArray, pos: Int, containerEnd: Int, hardLimit: Int): Elem? {
        val (id, idLen) = readVInt(buf, pos, hardLimit, keepMarker = true) ?: return null
        val (size, sizeLen) = readVInt(buf, pos + idLen, hardLimit, keepMarker = false) ?: return null
        val bodyStart = pos + idLen + sizeLen
        if (bodyStart > containerEnd) return null
        // 流式未知大小（0x01FFFFFFFFFFFFFF 等）或越界 → 以容器末尾为界
        val sizeInt = if (size > (containerEnd - bodyStart).toLong()) -1 else size.toInt()
        val bodyEnd = if (sizeInt < 0) containerEnd else minOf(bodyStart + sizeInt, containerEnd)
        if (bodyEnd <= bodyStart && sizeInt > 0) return null
        return Elem(id, bodyStart, bodyEnd)
    }

    /** 读 vint，返回 (值, 长度字节数)；越界或非法返回 null */
    private fun readVInt(buf: ByteArray, pos: Int, limit: Int, keepMarker: Boolean): Pair<Long, Int>? {
        if (pos >= limit) return null
        val b = buf[pos].toInt() and 0xFF
        if (b == 0) return null
        var len = 1
        var mask = 0x80
        while (b and mask == 0) {
            mask = mask shr 1
            len++
            if (len > 8) return null
        }
        var value = if (keepMarker) b.toLong() else (b and (mask - 1)).toLong()
        var i = 1
        while (i < len) {
            if (pos + i >= limit) return null
            value = (value shl 8) or (buf[pos + i].toLong() and 0xFF)
            i++
        }
        return value to len
    }

    private fun readUint(buf: ByteArray, start: Int, end: Int): Long {
        var v = 0L
        for (i in start until minOf(end, start + 8)) {
            v = (v shl 8) or (buf[i].toLong() and 0xFF)
        }
        return v
    }
}
