package com.xxxx.emby_tv.ui

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.xxxx.emby_tv.LocalServer
import com.xxxx.emby_tv.QrCodeUtils
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.remote.HttpClient
import com.xxxx.emby_tv.ui.components.TvInputDialog
import com.xxxx.emby_tv.ui.theme.ThemeColorManager
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val themeColor = ThemeColorManager.getThemeColorById(context, mainViewModel.currentThemeId)

    var proxyEnabled by remember { mutableStateOf(prefs.proxyEnabled) }
    var proxyType by remember { mutableStateOf(prefs.proxyType) }
    var proxyHost by remember { mutableStateOf(prefs.proxyHost) }
    var proxyPort by remember { mutableStateOf(prefs.proxyPort.toString()) }
    var proxyUsername by remember { mutableStateOf(prefs.proxyUsername) }
    var proxyPassword by remember { mutableStateOf(prefs.proxyPassword) }

    var qrCodeBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var localServerAddress by remember { mutableStateOf("") }
    var localServer by remember { mutableStateOf<LocalServer?>(null) }

    val enableFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

    var showHostDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        enableFocusRequester.requestFocus()
    }

    LaunchedEffect(themeColor) {
        withContext(Dispatchers.IO) {
            val server = LocalServer.startProxyServer(
                themeColor.primaryDark,
                themeColor.secondaryLight
            ) { enabled, type, host, port, username, password ->
                proxyEnabled = enabled
                proxyType = type
                proxyHost = host
                proxyPort = port.toString()
                proxyUsername = username
                proxyPassword = password
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(100)
                    saveFocusRequester.requestFocus()
                }
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
        onDispose {
            localServer?.stop()
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(2f))
                if (qrCodeBitmap != null) {
                    Image(
                        bitmap = qrCodeBitmap!!,
                        contentDescription = stringResource(R.string.proxy_scan_hint),
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.proxy_scan_hint),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "${stringResource(R.string.local_server_url)}: $localServerAddress",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.starting_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.weight(2f))

                Text(
                    text = stringResource(R.string.footer_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 48.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                ProxySwitchRow(
                    enabled = proxyEnabled,
                    onClick = {
                        proxyEnabled = !proxyEnabled
                        prefs.proxyEnabled = proxyEnabled
                        HttpClient.rebuildClient(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .focusRequester(enableFocusRequester)
                )

                if (proxyEnabled) {
                    Spacer(modifier = Modifier.height(15.dp))

                    Text(
                        text = stringResource(R.string.proxy_only_emby),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(15.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProxyTypeButton(
                            label = "HTTP",
                            selected = proxyType == "http",
                            onClick = { proxyType = "http" },
                            modifier = Modifier.weight(1f)
                        )
                        ProxyTypeButton(
                            label = "SOCKS5",
                            selected = proxyType == "socks5",
                            onClick = { proxyType = "socks5" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(15.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProxyTvInputButton(
                            value = proxyHost,
                            label = stringResource(R.string.proxy_host),
                            onClick = { showHostDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                        ProxyTvInputButton(
                            value = proxyPort,
                            label = stringResource(R.string.proxy_port),
                            onClick = { showPortDialog = true },
                            modifier = Modifier.width(100.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(15.dp))

                    ProxyTvInputButton(
                        value = proxyUsername,
                        label = stringResource(R.string.proxy_username) + " (${context.getString(R.string.proxy_optional)})",
                        onClick = { showUsernameDialog = true }
                    )

                    Spacer(modifier = Modifier.height(15.dp))

                    ProxyTvInputButton(
                        value = proxyPassword,
                        label = stringResource(R.string.proxy_password) + " (${context.getString(R.string.proxy_optional)})",
                        visualTransformation = PasswordVisualTransformation(),
                        onClick = { showPasswordDialog = true }
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Button(
                        onClick = {
                            prefs.proxyEnabled = proxyEnabled
                            prefs.proxyType = proxyType
                            prefs.proxyHost = proxyHost
                            prefs.proxyPort = proxyPort.toIntOrNull() ?: PreferencesManager.DEFAULT_PROXY_PORT
                            prefs.proxyUsername = proxyUsername
                            prefs.proxyPassword = proxyPassword
                            HttpClient.rebuildClient(context)
                            scope.launch {
                                Toast.makeText(
                                    context.applicationContext,
                                    context.getString(R.string.proxy_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp)
                            .focusRequester(saveFocusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                                    onBack()
                                    true
                                } else false
                            },
                        colors = ButtonDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = ButtonDefaults.shape()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.proxy_save),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
        }
    }

    if (showHostDialog) {
        TvInputDialog(
            title = stringResource(R.string.proxy_host),
            initialValue = proxyHost,
            onConfirm = { proxyHost = it; showHostDialog = false },
            onDismiss = { showHostDialog = false }
        )
    }
    if (showPortDialog) {
        TvInputDialog(
            title = stringResource(R.string.proxy_port),
            initialValue = proxyPort,
            onConfirm = { proxyPort = it.filter { c -> c.isDigit() }; showPortDialog = false },
            onDismiss = { showPortDialog = false },
            isNumber = true
        )
    }
    if (showUsernameDialog) {
        TvInputDialog(
            title = stringResource(R.string.proxy_username),
            initialValue = proxyUsername,
            onConfirm = { proxyUsername = it; showUsernameDialog = false },
            onDismiss = { showUsernameDialog = false }
        )
    }
    if (showPasswordDialog) {
        TvInputDialog(
            title = stringResource(R.string.proxy_password),
            initialValue = proxyPassword,
            onConfirm = { proxyPassword = it; showPasswordDialog = false },
            onDismiss = { showPasswordDialog = false }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProxySwitchRow(
    enabled: Boolean,
    onClick: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ),
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            
        ),
        modifier = modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (enabled) stringResource(R.string.proxy_status_on) else stringResource(R.string.proxy_status_off),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Box(
                modifier = Modifier
                    .size(52.dp, 28.dp)
                    .background(
                        if (enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProxyTypeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ),
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier.height(56.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProxyTvInputButton(
    value: String,
    label: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ),
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth(0.8f)
            .height(64.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
            val transformedText = visualTransformation.filter(AnnotatedString(value)).text.text
            Text(
                text = if (value.isEmpty()) " " else transformedText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
