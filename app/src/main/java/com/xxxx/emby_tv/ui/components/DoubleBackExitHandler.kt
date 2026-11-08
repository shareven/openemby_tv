package com.xxxx.emby_tv.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.xxxx.emby_tv.R
import kotlinx.coroutines.delay

/**
 * 「按两次返回退出APP」处理器（含底部提示浮层）
 *
 * enabled=false 时不拦截返回键，保持系统默认行为（按一次返回直接退出）；
 * enabled=true 时第一次按返回在屏幕底部弹出「再按一次退出APP」小字提示，
 * 2 秒内再按一次才真正退出，超时后重新计时。
 *
 * 使用方式：放在页面根 Box 布局的末尾，保证提示浮层盖在页面内容之上。
 */
@Composable
fun DoubleBackExitHandler(enabled: Boolean) {
    if (!enabled) return
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var showPrompt by remember { mutableStateOf(false) }

    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime <= 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = now
            showPrompt = true
        }
    }

    // 提示 2 秒后自动消失
    LaunchedEffect(showPrompt) {
        if (showPrompt) {
            delay(2000)
            showPrompt = false
        }
    }

    if (showPrompt) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.press_back_again_to_exit),
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}
