package com.xxxx.emby_tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import android.widget.Toast
import com.xxxx.emby_tv.LocalServer
import com.xxxx.emby_tv.QrCodeUtils
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.ui.theme.ThemeColorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在线字幕设置页：字幕站开关 + OpenSubtitles API Key（扫码经手机网页录入）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OnlineSubtitleSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    var thunderEnabled by remember { mutableStateOf(prefs.thunderSubtitleEnabled) }
    var openSubtitlesEnabled by remember { mutableStateOf(prefs.openSubtitlesEnabled) }
    var apiKey by remember { mutableStateOf(prefs.openSubtitlesApiKey) }

    var qrCodeBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var localServerAddress by remember { mutableStateOf("") }
    var localServer by remember { mutableStateOf<LocalServer?>(null) }

    val thunderFocusRequester = remember { FocusRequester() }
    val themeColor = ThemeColorManager.getThemeColorById(context, prefs.themeId)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { thunderFocusRequester.requestFocus() }
    }

    // 手机网页录入 API Key（与代理设置页同一套 LocalServer + 二维码方案）
    LaunchedEffect(themeColor) {
        withContext(Dispatchers.IO) {
            val server = LocalServer.startApiKeyServer(
                themeColor.primaryDark,
                themeColor.secondaryLight
            ) { key, username, password ->
                apiKey = key
                // 收到 Key 后自动开启 OpenSubtitles 源
                if (!openSubtitlesEnabled) {
                    openSubtitlesEnabled = true
                    prefs.openSubtitlesEnabled = true
                }
                prefs.openSubtitlesApiKey = key
                prefs.openSubtitlesUsername = username.trim()
                prefs.openSubtitlesPassword = password
                Toast.makeText(
                    context,
                    context.getString(R.string.opensubtitles_key_saved),
                    Toast.LENGTH_SHORT
                ).show()
            }
            localServer = server
            if (server != null) {
                val ip = QrCodeUtils.getLocalIpAddress()
                if (ip != null) {
                    val address = "http://$ip:${server.listeningPort}"
                    localServerAddress = address
                    val bitmap = QrCodeUtils.generateQrCode(address, 400)
                    qrCodeBitmap = bitmap?.asImageBitmap()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { localServer?.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.online_subtitle_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.online_subtitle_settings_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.6f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 迅雷字幕开关
        OnlineSubtitleToggleRow(
            label = stringResource(R.string.online_subtitle_thunder),
            description = stringResource(R.string.online_subtitle_thunder_desc),
            enabled = thunderEnabled,
            onClick = {
                thunderEnabled = !thunderEnabled
                prefs.thunderSubtitleEnabled = thunderEnabled
            },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .focusRequester(thunderFocusRequester)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                        onBack()
                        true
                    } else false
                }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // OpenSubtitles 开关
        OnlineSubtitleToggleRow(
            label = stringResource(R.string.online_subtitle_opensubtitles),
            description = stringResource(R.string.online_subtitle_opensubtitles_desc),
            enabled = openSubtitlesEnabled && apiKey.isNotBlank(),
            onClick = {
                // 未配置 Key 时开启无意义，提示扫码录入（服务端页面已在本页启动）
                if (apiKey.isBlank()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.opensubtitles_key_missing),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    openSubtitlesEnabled = !openSubtitlesEnabled
                    prefs.openSubtitlesEnabled = openSubtitlesEnabled
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // API Key 状态 + 清除
        Row(
            modifier = Modifier.fillMaxWidth(0.6f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.opensubtitles_api_key) + ": " +
                        if (apiKey.isBlank()) stringResource(R.string.opensubtitles_key_not_configured)
                        else stringResource(R.string.opensubtitles_key_configured) + " (${apiKey.take(4)}****)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            if (apiKey.isNotBlank()) {
                Surface(
                    onClick = {
                        apiKey = ""
                        openSubtitlesEnabled = false
                        prefs.openSubtitlesApiKey = ""
                        prefs.openSubtitlesEnabled = false
                        prefs.openSubtitlesUsername = ""
                        prefs.openSubtitlesPassword = ""
                        Toast.makeText(
                            context,
                            context.getString(R.string.opensubtitles_key_cleared),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White,
                        focusedContainerColor = MaterialTheme.colorScheme.secondary,
                        focusedContentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.clear_key),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 扫码录入 Key
        if (qrCodeBitmap != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = qrCodeBitmap!!,
                    contentDescription = stringResource(R.string.opensubtitles_scan_hint),
                    modifier = Modifier
                        .size(140.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text(
                        text = stringResource(R.string.opensubtitles_scan_hint),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = localServerAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

/** 带描述与状态点的开关行 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OnlineSubtitleToggleRow(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (enabled) 0.12f else 0.05f),
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.secondary,
            focusedContentColor = MaterialTheme.colorScheme.onSecondary
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // 状态指示点（样式与按键设置页一致）
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.secondary
                        else Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
            )
        }
    }
}
