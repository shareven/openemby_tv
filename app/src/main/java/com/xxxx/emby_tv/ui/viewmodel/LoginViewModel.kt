package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.session.AccountInfo
import com.xxxx.emby_tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录 ViewModel
 * 
 * 职责：
 * - 处理登录流程
 * - 管理登录状态和错误
 * - 提供账号切换功能
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    // === 状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // === 回显用（从 Repository 获取）===
    val savedServerUrl: String? get() = repository.savedServerUrl
    val savedUsername: String get() = repository.savedUsername
    val savedPassword: String get() = repository.savedPassword

    // === 多账号支持 ===
    val savedAccounts: List<AccountInfo> get() = repository.savedAccounts
    val currentAccountId: String? get() = repository.currentAccountId

    /**
     * 登录
     */
    fun login(
        serverUrl: String,
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (isLoading) return

        // 验证输入
        if (serverUrl.isBlank()) {
            errorMessage = "请输入服务器地址"
            onError(errorMessage!!)
            return
        }
        if (username.isBlank()) {
            errorMessage = "请输入用户名"
            onError(errorMessage!!)
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                repository.login(serverUrl, username, password)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    val prefs = PreferencesManager(getApplication())
                    val msg = e.message ?: getApplication<Application>().getString(R.string.error_network_error)
                    errorMessage = if (prefs.proxyEnabled && !msg.contains(
                            getApplication<Application>().getString(R.string.error_proxy_connection)
                    )) {
                        "${msg} (${getApplication<Application>().getString(R.string.error_proxy_connection)})"
                    } else {
                        msg
                    }
                    onError(errorMessage!!)
                }
            }
        }
    }

    /**
     * 退出登录
     */
    fun logout() {
        repository.logout()
    }

    /**
     * 切换账号
     */
    fun switchAccount(accountId: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        if (repository.switchAccount(accountId)) {
            onSuccess()
        } else {
            onError("切换账号失败")
        }
    }

    /**
     * 删除账号
     */
    fun removeAccount(accountId: String) {
        repository.removeAccount(accountId)
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        errorMessage = null
    }
}
