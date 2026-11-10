package com.xxxx.emby_tv.util

import android.content.Context
import android.net.wifi.WifiManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** 发现的服务器 */
data class DiscoveredServer(
    val name: String,
    /** 归一化后的完整地址，如 http://192.168.1.10:8096 */
    val address: String,
    val id: String
)

/**
 * 局域网 Emby 服务器自动发现，两级策略：
 *
 * 1. UDP 广播（Emby 官方发现协议，端口 7359，"who is EmbyServer?"）——快，
 *    但部分服务器关闭了自动发现时不响应（实测存在此情况）
 * 2. TCP 扫描兜底——遍历本机所有 IPv4 接口网段，探测常见端口
 *    （8096/8920/80/443），再用 /System/Info/Public 验证是否 Emby，
 *    不依赖服务器任何开关
 *
 * 纯系统 API 实现，不引入第三方库
 */
object EmbyServerDiscovery {

    private const val UDP_PORT = 7359
    private const val QUERY_MESSAGE = "who is EmbyServer?"
    private const val UDP_TIMEOUT_MS = 600L
    /** 优先扫描 Emby 默认端口，命中即返回；未命中再扫其余常见端口 */
    private val TCP_PORTS_FAST = listOf(8096)
    private val TCP_PORTS_SLOW = listOf(8920, 80, 443)
    private const val TCP_CONNECT_TIMEOUT_MS = 250
    private const val HTTP_TIMEOUT_MS = 800
    private const val TCP_CONCURRENCY = 128

    private val gson = Gson()

    /**
     * 发现局域网内的 Emby 服务器（阻塞式，请在 IO 线程调用）
     */
    suspend fun discover(context: Context): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, DiscoveredServer>()

        // 第一级：UDP 广播（含 MulticastLock）
        val udpResults = udpDiscover(context)
        udpResults.forEach { results[it.id] = it }

        // 第二级：UDP 无结果时 TCP 扫描兜底
        if (results.isEmpty()) {
            tcpDiscover().forEach { results[it.id] = it }
        }
        results.values.toList()
    }

    // ==================== 第一级：UDP 广播 ====================

    private suspend fun udpDiscover(context: Context): List<DiscoveredServer> {
        val results = mutableListOf<DiscoveredServer>()
        val seenIds = mutableSetOf<String>()
        val multicastLock = acquireMulticastLock(context)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 500
            }

            val targets = broadcastTargets()
            if (targets.isEmpty()) return emptyList()
            val payload = QUERY_MESSAGE.toByteArray(Charsets.UTF_8)
            targets.forEach { target ->
                try {
                    socket.send(DatagramPacket(payload, payload.size, target, UDP_PORT))
                } catch (_: Exception) {
                    // 单个目标发送失败不影响其他目标
                }
            }

            val deadline = System.currentTimeMillis() + UDP_TIMEOUT_MS
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                parseUdpResponse(text)?.let { server ->
                    if (seenIds.add(server.id)) {
                        results.add(server)
                    }
                }
            }
        } catch (_: Exception) {
            // 发现失败不影响登录流程
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            try {
                multicastLock?.release()
            } catch (_: Exception) {
            }
        }
        return results
    }

    /** 解析 UDP 回应 JSON：{"Name":"...","Address":"http://x:8096","Id":"..."} */
    private fun parseUdpResponse(text: String): DiscoveredServer? {
        return try {
            val obj = gson.fromJson(text, JsonObject::class.java) ?: return null
            val name = obj.get("Name")?.takeIf { !it.isJsonNull }?.asString ?: "Emby Server"
            val rawAddress = obj.get("Address")?.takeIf { !it.isJsonNull }?.asString ?: return null
            val id = obj.get("Id")?.takeIf { !it.isJsonNull }?.asString ?: rawAddress
            val address = if (rawAddress.startsWith("http://", true) ||
                rawAddress.startsWith("https://", true)
            ) rawAddress else "http://$rawAddress"
            DiscoveredServer(name = name, address = address, id = id)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 收集所有需要广播的目标：受限广播 255.255.255.255 +
     * 每个活跃 IPv4 接口的子网定向广播。
     * 通过 NetworkInterface 枚举（有线/无线通吃，不依赖 WifiManager）
     */
    private fun broadcastTargets(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return targets
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.interfaceAddresses) {
                    val broadcast = addr.broadcast ?: continue
                    if (addr.address is java.net.Inet4Address) {
                        try {
                            targets.add(broadcast)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return targets.distinctBy { it.hostAddress }
    }

    private fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? {
        return try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            wifi.createMulticastLock("emby_server_discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 第二级：TCP 扫描兜底 ====================

    /**
     * 扫描本机所有 IPv4 网段上的 Emby 常见端口并验证。
     * 优先只扫 Emby 默认端口 8096（一轮即可命中绝大多数服务器），
     * 未命中再扫其余常见端口；任一服务器验证成功后其余探测立即退出
     */
    private suspend fun tcpDiscover(): List<DiscoveredServer> = coroutineScope {
        val subnets = ipv4Subnets()
        val found = java.util.concurrent.ConcurrentLinkedQueue<DiscoveredServer>()
        val stopScanning = java.util.concurrent.atomic.AtomicBoolean(false)
        val semaphore = kotlinx.coroutines.sync.Semaphore(TCP_CONCURRENCY)

        suspend fun scanRound(ports: List<Int>) {
            val jobs = subnets.flatMap { subnet ->
                (1..254).map { host ->
                    async(Dispatchers.IO) {
                        if (stopScanning.get()) return@async
                        semaphore.withPermit {
                            if (stopScanning.get()) return@withPermit
                            val hostIp = subnetPrefixToHost(subnet, host) ?: return@withPermit
                            ports.forEach { port ->
                                if (stopScanning.get()) return@forEach
                                if (tcpPortOpen(hostIp, port)) {
                                    verifyEmbyServer(hostIp, port)?.let { server ->
                                        found.add(server)
                                        // 已有结果，让其余探测尽快退出
                                        stopScanning.set(true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        scanRound(TCP_PORTS_FAST)
        if (found.isEmpty() && !stopScanning.get()) {
            scanRound(TCP_PORTS_SLOW)
        }

        // 按服务器 Id 去重（同机多端口命中时优先 8096）
        found.groupBy { it.id }
            .map { (_, servers) -> servers.firstOrNull { it.address.endsWith(":8096") } ?: servers.first() }
    }

    /** 本机所有活跃 IPv4 接口的网段前缀（如 192.168.2），排除环回和大网段 */
    private fun ipv4Subnets(): List<String> {
        val subnets = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return subnets
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.interfaceAddresses) {
                    val inet = addr.address as? java.net.Inet4Address ?: continue
                    val maskBits = addr.networkPrefixLength
                    // 只扫 /16 到 /24 的常规家庭/办公网段，避免超大网段耗时过长
                    if (maskBits < 16 || maskBits > 24) continue
                    val ip = inet.hostAddress ?: continue
                    val prefix = when {
                        maskBits > 24 -> ip.substringBeforeLast('.')
                        else -> {
                            // /16~/23 取网络前缀：按前缀长度截断
                            val keepOctets = maskBits / 8
                            if (maskBits % 8 == 0) {
                                ip.split(".").take(keepOctets).joinToString(".")
                            } else {
                                // 非整字节掩码（如 /22）退化为按 /24 扫当前 C 段
                                ip.substringBeforeLast('.')
                            }
                        }
                    }
                    if (prefix.isNotEmpty()) subnets.add(prefix)
                }
            }
        } catch (_: Exception) {
        }
        return subnets.distinct()
    }

    private fun subnetPrefixToHost(prefix: String, host: Int): String? {
        return if (prefix.count { it == '.' } == 2) "$prefix.$host" else null
    }

    private fun tcpPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT_MS)
                socket.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 请求 /System/Info/Public 验证是否 Emby 服务器 */
    private fun verifyEmbyServer(host: String, port: Int): DiscoveredServer? {
        val useHttps = port == 443 || port == 8920
        val base = "${if (useHttps) "https" else "http"}://$host:$port"
        return try {
            val connection = URL("$base/emby/System/Info/Public").openConnection() as HttpURLConnection
            if (useHttps) {
                trustAllCerts(connection as HttpsURLConnection)
            }
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            try {
                if (connection.responseCode != 200) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = gson.fromJson(body, JsonObject::class.java) ?: return null
                val id = obj.get("Id")?.takeIf { !it.isJsonNull }?.asString ?: return null
                val name = obj.get("ServerName")?.takeIf { !it.isJsonNull }?.asString ?: "Emby Server"
                DiscoveredServer(name = name, address = base, id = id)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Emby 自签名证书场景：发现阶段放宽证书校验 */
    private fun trustAllCerts(connection: HttpsURLConnection) {
        try {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustAll), null)
            connection.sslSocketFactory = sslContext.socketFactory
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (_: Exception) {
        }
    }
}
