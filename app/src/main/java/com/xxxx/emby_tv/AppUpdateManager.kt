package com.xxxx.emby_tv

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.azhon.appupdate.manager.DownloadManager
import com.azhon.appupdate.listener.OnDownloadListener
import com.xxxx.emby_tv.R
import java.io.File

/**
 * 应用更新管理器
 * 使用 io.github.azhon:appupdate:4.3.6 库实现下载和安装功能
 */
class AppUpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val UPDATE_URL = "https://example.com/update" // Placeholder or from Config if available
    }
    
    private var downloadManager: DownloadManager? = null
    
    /**
     * 开始更新应用
     */
    fun startUpdate(
        downloadUrl: String,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            // Check context type
            if (context !is Activity) {
                Log.e(TAG, "Context must be an Activity")
                onError?.invoke(context.getString(R.string.need_activity_context))
                return
            }
            
            // 创建下载监听器
            val downloadListener = object : OnDownloadListener {
                override fun start() {
                    Log.d(TAG, "开始下载")
                    onProgress?.invoke(0)
                }
                
                override fun downloading(max: Int, progress: Int) {
                    val percentage = if (max > 0) (progress * 100 / max) else 0
                    Log.d(TAG, "下载进度: $progress/$max ($percentage%)")
                    onProgress?.invoke(percentage)
                }
                
                override fun done(apk: File) {
                    Log.d(TAG, "下载完成: ${apk.absolutePath}")
                    onProgress?.invoke(100)
                    onSuccess?.invoke()
                }
                
                override fun cancel() {
                    Log.d(TAG, "下载取消")
                }
                
                override fun error(e: Throwable) {
                    Log.e(TAG, "下载错误", e)
                    onError?.invoke("下载失败: ${e.message}")
                }
            }
            
            // 创建下载管理器，配置下载监听器
            downloadManager = DownloadManager.Builder(context)
                .apkUrl(downloadUrl)
                .apkName("openemby_tv.apk")
                .smallIcon(R.mipmap.ic_launcher)
                .showNotification(true)
                .jumpInstallPage(true)
                .showBgdToast(true)
                .onDownloadListener(downloadListener)
                .build()
            
            // 开始下载
            downloadManager?.download()
            
        } catch (e: Exception) {
            Log.e(TAG, "启动更新失败", e)
            onError?.invoke(context.getString(R.string.update_start_failed, e.message))
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelUpdate() {
        try {
            downloadManager?.cancel()
            Log.d(TAG, "取消下载")
        } catch (e: Exception) {
            Log.e(TAG, "取消下载失败", e)
        }
    }
    
    /**
     * 获取当前应用版本名称
     */
    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "未知版本"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "获取版本信息失败", e)
            "未知版本"
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        downloadManager = null
    }
}
    