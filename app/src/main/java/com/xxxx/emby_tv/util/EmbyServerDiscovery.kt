package com.xxxx.emby_tv.util

import android.content.Context
import android.net.wifi.WifiManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** 发现的服务器 */
data class DiscoveredServer(
    val name: String,
    /** 归一化后的完整地址，如 http://192.168.1.10:8096 */
    val address: String,
    val id: String
)

/**
 * 局域网 Emby 服务器自动发现
 *
 * 使用 Emby 服务端内置的发现协议：向 UDP 7359 端口广播 "who is EmbyServer?"，
 * 服务器回应 JSON：{"Name":"...","Address":"http://192.168.1.10:8096","Id":"..."}
 * 纯系统 API 实现（DatagramSocket），不引入任何第三方库
 */
object EmbyServerDiscovery {

    private const val DISCOVERY_PORT = 7359
    private const val QUERY_MESSAGE = "who is EmbyServer?"
    private val gson = Gson()

    /**
     * 广播发现局域网内的 Emby 服务器（阻塞式，请在 IO 线程调用）
     *
     * @param timeoutMs 总等待时长，超时后返回已收到的结果
     */
    suspend fun discover(context: Context, timeoutMs: Long = 1500L): List<DiscoveredServer> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DiscoveredServer>()
            val seenIds = mutableSetOf<String>()

            // 部分电视盒子会过滤广播包，需要 MulticastLock 才能收到回应
            val multicastLock = acquireMulticastLock(context)
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 500
                }

                // 同时向受限广播和子网定向广播发包（部分路由器不转发 255.255.255.255）
                val targets = mutableListOf(InetAddress.getByName("255.255.255.255"))
                subnetBroadcastAddress(context)?.let { targets.add(it) }
                val payload = QUERY_MESSAGE.toByteArray(Charsets.UTF_8)
                targets.forEach { target ->
                    try {
                        socket.send(DatagramPacket(payload, payload.size, target, DISCOVERY_PORT))
                    } catch (_: Exception) {
                        // 单个目标发送失败不影响其他目标
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    parseResponse(text)?.let { server ->
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
            results
        }

    /** 解析服务器回应的 JSON，失败返回 null */
    private fun parseResponse(text: String): DiscoveredServer? {
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

    /** 根据 DHCP 信息计算子网定向广播地址（如 192.168.1.255） */
    private fun subnetBroadcastAddress(context: Context): InetAddress? {
        return try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val dhcp = wifi.dhcpInfo ?: return null
            // DhcpInfo 的 int 是小端序存储，需要字节反转
            val ip = Integer.reverseBytes(dhcp.ipAddress)
            val netmask = Integer.reverseBytes(dhcp.netmask)
            val broadcast = (ip and netmask) or netmask.inv()
            InetAddress.getByAddress(
                byteArrayOf(
                    (broadcast shr 24 and 0xFF).toByte(),
                    (broadcast shr 16 and 0xFF).toByte(),
                    (broadcast shr 8 and 0xFF).toByte(),
                    (broadcast and 0xFF).toByte()
                )
            )
        } catch (_: Exception) {
            null
        }
    }
}
