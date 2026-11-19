package com.xxxx.emby_tv.data.remote.subtitle

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 迅雷 CID 文件哈希计算器（客户端版）。
 *
 * 迅雷字幕接口的精准匹配依赖视频文件 CID：SHA1(头部 20KB + 1/3 处 20KB + 尾部 20KB)。
 * 服务端插件读本地文件计算；播放端视频是网络流，这里用 HTTP Range 请求
 * 只取这 3 段共 60KB（思路同 MkvCompressedSubtitleDetector 的探测）。
 *
 * 直链/网盘 302 目标普遍支持 Range；转码流或不支持 Range 的服务器会失败，
 * 此时返回 null，调用方退化为纯文件名匹配。
 */
object ThunderFileHasher {

    private const val TAG = "ThunderFileHasher"
    private const val BLOCK = 0x5000          // 20480 字节
    private const val TOTAL = BLOCK * 3       // 61440 字节
    private const val TIMEOUT_MS = 15_000L

    /**
     * 计算播放流的迅雷 CID。
     *
     * @param videoUrl 正在播放的直链/转码地址
     * @return 大写十六进制 SHA1；文件过小、Range 不支持或网络异常时返回 null
     */
    suspend fun computeCid(videoUrl: String, client: OkHttpClient): String? =
        withContext(Dispatchers.IO) {
            try {
                val timedClient = client.newBuilder()
                    .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()

                val fileSize = probeFileSize(videoUrl, timedClient)
                if (fileSize == null || fileSize <= 0) return@withContext null

                val cid: String? = if (fileSize < TOTAL) {
                    val data = fetchRange(videoUrl, 0L, fileSize - 1, timedClient)
                        ?: return@withContext null
                    if (data.size != fileSize.toInt()) return@withContext null
                    sha1Hex(data)
                } else {
                    val head = fetchRange(videoUrl, 0L, BLOCK - 1L, timedClient)
                    val middle = fetchRange(videoUrl, fileSize / 3, fileSize / 3 + BLOCK - 1L, timedClient)
                    val tail = fetchRange(videoUrl, fileSize - BLOCK, fileSize - 1, timedClient)
                    if (head == null || middle == null || tail == null) return@withContext null
                    if (head.size != BLOCK || middle.size != BLOCK || tail.size != BLOCK) {
                        return@withContext null
                    }
                    val buf = ByteArray(TOTAL)
                    System.arraycopy(head, 0, buf, 0, BLOCK)
                    System.arraycopy(middle, 0, buf, BLOCK, BLOCK)
                    System.arraycopy(tail, 0, buf, BLOCK * 2, BLOCK)
                    sha1Hex(buf)
                }
                if (cid != null) Log.i(TAG, "CID computed: $cid (size=$fileSize)")
                cid
            } catch (e: Exception) {
                Log.w(TAG, "compute CID failed: ${e.message}")
                null
            }
        }

    /** 用 1 字节 Range 探测文件总长（206 + Content-Range: bytes 0-0/12345） */
    private fun probeFileSize(url: String, client: OkHttpClient): Long? {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 206) {
                // 服务器忽略 Range 返回 200：无法分段读取，放弃哈希
                Log.w(TAG, "Range not supported (HTTP ${response.code}), skip CID")
                return null
            }
            val contentRange = response.header("Content-Range") ?: return null
            // 形如 "bytes 0-0/123456789"
            val total = contentRange.substringAfterLast('/').toLongOrNull()
            return total?.takeIf { it > 0 }
        }
    }

    /** 读取 [start, end] 闭区间字节；非 206 或长度不符返回 null */
    private fun fetchRange(url: String, start: Long, end: Long, client: OkHttpClient): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 206) return null
            val body = response.body ?: return null
            val expected = (end - start + 1).toInt()
            val buf = ByteArray(expected)
            var total = 0
            body.byteStream().use { input ->
                while (total < expected) {
                    val n = input.read(buf, total, expected - total)
                    if (n < 0) break
                    total += n
                }
            }
            return if (total == expected) buf else null
        }
    }

    private fun sha1Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(data)
        return digest.joinToString("") { "%02X".format(it) }
    }
}
