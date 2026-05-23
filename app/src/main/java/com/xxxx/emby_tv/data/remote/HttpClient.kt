package com.xxxx.emby_tv.data.remote

import android.content.Context
import android.util.Log
import com.xxxx.emby_tv.data.local.PreferencesManager
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Route
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

object HttpClient {
    private const val TAG = "HttpClient"

    @Volatile
    private var client: OkHttpClient? = null

    @Volatile
    private var proxyConfig: ProxyConfig? = null

    @Volatile
    private var cache: Cache? = null

    private fun getCache(context: Context): Cache {
        return cache ?: synchronized(this) {
            cache ?: Cache(File(context.cacheDir, "http_cache"), 10 * 1024 * 1024).also { cache = it }
        }
    }

    fun getClient(context: Context): OkHttpClient {
        val currentConfig = loadProxyConfig(context)
        if (client != null && proxyConfig == currentConfig) {
            return client!!
        }
        synchronized(this) {
            if (client != null && proxyConfig == currentConfig) {
                return client!!
            }
            proxyConfig = currentConfig
            client = createClient(context, currentConfig)
            return client!!
        }
    }

    private fun loadProxyConfig(context: Context): ProxyConfig {
        val prefs = PreferencesManager(context)
        return if (prefs.proxyEnabled && prefs.proxyHost.isNotEmpty()) {
            ProxyConfig(
                enabled = true,
                type = prefs.proxyType,
                host = prefs.proxyHost,
                port = prefs.proxyPort,
                username = prefs.proxyUsername,
                password = prefs.proxyPassword
            )
        } else {
            ProxyConfig()
        }
    }

    private fun createClient(context: Context, config: ProxyConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(getCache(context))
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 20
            })
            .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (config.enabled && config.host.isNotEmpty()) {
            val proxy = if (config.type == "socks5") {
                Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(config.host, config.port))
            } else {
                Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(config.host, config.port))
            }

            val embyHost = extractEmbyHost(context)

            builder.proxySelector(object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> {
                    return try {
                        val host = uri.host ?: return listOf(Proxy.NO_PROXY)
                        if (host == "127.0.0.1" || host == "localhost" || host == "::1") {
                            return listOf(Proxy.NO_PROXY)
                        }
                        if (embyHost.isNotEmpty() && host.equals(embyHost, ignoreCase = true)) {
                            return listOf(proxy)
                        }
                        listOf(Proxy.NO_PROXY)
                    } catch (e: Exception) {
                        Log.e(TAG, "ProxySelector.select error", e)
                        listOf(Proxy.NO_PROXY)
                    }
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {
                    Log.e(TAG, "Proxy connectFailed: $uri", ioe)
                }
            })

            if (config.username.isNotEmpty()) {
                val credential = Credentials.basic(config.username, config.password)
                builder.proxyAuthenticator(object : Authenticator {
                    override fun authenticate(route: Route?, response: okhttp3.Response): okhttp3.Request? {
                        if (response.request.header("Proxy-Authorization") != null) {
                            return null
                        }
                        return response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                })
            }

            Log.i(TAG, "代理已启用: ${config.type}://${config.host}:${config.port}")
        }

        return builder.build()
    }

    private fun extractEmbyHost(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences("emby_tv", Context.MODE_PRIVATE)
            val serverUrl = prefs.getString("serverUrl", "") ?: ""
            if (serverUrl.isNotEmpty()) {
                URL(serverUrl).host ?: ""
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun rebuildClient(context: Context) {
        try {
            synchronized(this) {
                val oldClient = client
                client = null
                proxyConfig = null
                oldClient?.dispatcher?.cancelAll()
            }
            getClient(context)
            Log.i(TAG, "HttpClient 已重建")
        } catch (e: Exception) {
            Log.e(TAG, "重建 HttpClient 失败", e)
            synchronized(this) {
                client = null
                proxyConfig = null
            }
        }
    }

    private data class ProxyConfig(
        val enabled: Boolean = false,
        val type: String = "http",
        val host: String = "",
        val port: Int = 1080,
        val username: String = "",
        val password: String = ""
    )
}
