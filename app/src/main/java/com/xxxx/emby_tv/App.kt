package com.xxxx.emby_tv

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.xxxx.emby_tv.data.remote.HttpClient

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("App", "Uncaught exception on ${thread.name}", throwable)
            val msg = throwable.message ?: throwable.cause?.message ?: ""
            val prefs = com.xxxx.emby_tv.data.local.PreferencesManager(this)
            val proxyOn = prefs.proxyEnabled && prefs.proxyHost.isNotEmpty()
            val isProxyError = proxyOn || msg.contains("SOCKS", ignoreCase = true) ||
                    msg.contains("Proxy", ignoreCase = true) ||
                    msg.contains("proxy", ignoreCase = true) ||
                    msg.contains("Connection refused", ignoreCase = true) ||
                    msg.contains("Malformed reply", ignoreCase = true) ||
                    msg.contains("407") ||
                    msg.contains(getString(R.string.error_proxy_connection))
            if (isProxyError) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, getString(R.string.error_proxy_connection), Toast.LENGTH_LONG).show()
                }
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { HttpClient.getClient(context) }))
                }
                .build()
        }
    }
}
