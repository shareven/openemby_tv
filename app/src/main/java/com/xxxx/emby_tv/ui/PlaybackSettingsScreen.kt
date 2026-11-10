package com.xxxx.emby_tv.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.google.gson.JsonObject
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.components.BufferSettingRow
import com.xxxx.emby_tv.util.ExternalPlayerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放设置页（样式对齐账号管理页：居中卡片 + 白底黑字聚焦）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    // 外部播放器偏好：0 从不 / 1 总是 / 2 仅杜比视界
    var externalMode by remember { mutableIntStateOf(prefs.externalPlayerMode) }
    var preferredPlayerPkg by remember { mutableStateOf(prefs.preferredExternalPlayerPackage) }
    var players by remember { mutableStateOf<List<ExternalPlayerHelper.PlayerOption>>(emptyList()) }

    // 多片源版本优先：0 杜比视界优先 / 1 HDR优先 / 2 流畅优先 / 3 默认排序
    var sourcePref by remember { mutableIntStateOf(prefs.sourcePreference) }

    // 跳过片头
    var autoSkipIntro by remember { mutableStateOf(prefs.autoSkipIntro) }

    // 标记片头（服务端媒体库设置）
    val scope = rememberCoroutineScope()
    var markerLibraries by remember { mutableStateOf<List<MarkerLibraryState>>(emptyList()) }
    var markerStatus by remember { mutableStateOf<MarkerStatus>(MarkerStatus.Loading) }

    LaunchedEffect(Unit) {
        try {
            val libs = EmbyRepository.getInstance(context).getVirtualFolders()
                .filter { it.collectionType.equals("tvshows", ignoreCase = true) && it.libraryOptions != null }
            if (libs.isEmpty()) {
                markerStatus = MarkerStatus.NoLibraries
            } else {
                markerLibraries = libs.map {
                    MarkerLibraryState(
                        itemId = it.itemId,
                        name = it.name,
                        savedMode = markerModeOf(it.libraryOptions),
                        libraryOptions = it.libraryOptions!!
                    )
                }
                markerStatus = MarkerStatus.Loaded
            }
        } catch (e: Exception) {
            // 4xx：权限不足（需管理员）；其他：网络/服务异常
            markerStatus = if (e.message?.contains("HTTP Error: 4") == true) {
                MarkerStatus.Denied
            } else {
                MarkerStatus.Failed
            }
        }
    }

    /**
     * 切换某媒体库的标记片头模式：
     * 先乐观更新界面，写入服务端成功后确认；失败（如未开通 Premiere / 权限不足）
     * 时提示错误并回滚到服务端已保存的模式
     */
    fun changeMarkerMode(lib: MarkerLibraryState, newMode: Int) {
        if (lib.selectedMode == newMode) return
        markerLibraries = markerLibraries.map {
            if (it.itemId == lib.itemId) it.copy(selectedMode = newMode) else it
        }
        scope.launch {
            try {
                applyMarkerMode(lib.libraryOptions, newMode)
                EmbyRepository.getInstance(context)
                    .updateLibraryMarkerDetection(lib.itemId, lib.libraryOptions)
                markerLibraries = markerLibraries.map {
                    if (it.itemId == lib.itemId) it.copy(savedMode = newMode) else it
                }
            } catch (e: Exception) {
                // 失败：回滚界面选择，并还原回传对象避免残留脏数据
                applyMarkerMode(lib.libraryOptions, lib.savedMode)
                markerLibraries = markerLibraries.map {
                    if (it.itemId == lib.itemId) it.copy(selectedMode = lib.savedMode) else it
                }
                val msg = if (e.message?.contains("HTTP Error") == true) {
                    context.getString(R.string.intro_marker_update_denied)
                } else {
                    context.getString(R.string.intro_marker_update_failed)
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 缓冲设置
    var minBufferMs by remember { mutableIntStateOf(prefs.minBufferMs) }
    var maxBufferMs by remember { mutableIntStateOf(prefs.maxBufferMs) }
    var playbackBufferMs by remember { mutableIntStateOf(prefs.playbackBufferMs) }
    var rebufferMs by remember { mutableIntStateOf(prefs.rebufferMs) }
    var bufferSizeBytes by remember { mutableIntStateOf(prefs.bufferSizeBytes) }

    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            firstFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
        players = withContext(Dispatchers.IO) {
            ExternalPlayerHelper.queryPlayers(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        // 主卡片容器（同账号页）
        Surface(
            modifier = Modifier
                .width(700.dp)
                .heightIn(min = 400.dp, max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = Color(0xFF161616).copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.playback_settings),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ===== 外部播放器偏好 =====
                    item { SectionTitle(stringResource(R.string.external_player_preference)) }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.external_player_never),
                            isSelected = externalMode == 0,
                            isFirst = true,
                            focusRequester = firstFocusRequester
                        ) {
                            externalMode = 0
                            prefs.externalPlayerMode = 0
                        }
                    }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.external_player_always),
                            isSelected = externalMode == 1
                        ) {
                            externalMode = 1
                            prefs.externalPlayerMode = 1
                        }
                    }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.external_player_dv_only),
                            isSelected = externalMode == 2
                        ) {
                            externalMode = 2
                            prefs.externalPlayerMode = 2
                        }
                    }

                    // ===== 默认外部播放器 =====
                    item { SectionTitle(stringResource(R.string.default_external_player)) }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.external_player_system),
                            isSelected = preferredPlayerPkg.isEmpty()
                        ) {
                            preferredPlayerPkg = ""
                            prefs.preferredExternalPlayerPackage = ""
                        }
                    }
                    items(players) { player ->
                        SettingOptionRow(
                            title = player.label,
                            isSelected = preferredPlayerPkg == player.packageName
                        ) {
                            preferredPlayerPkg = player.packageName ?: ""
                            prefs.preferredExternalPlayerPackage = preferredPlayerPkg
                        }
                    }

                    // ===== 多片源版本优先 =====
                    item { SectionTitle(stringResource(R.string.source_preference)) }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.source_pref_dv_first),
                            isSelected = sourcePref == 0
                        ) {
                            sourcePref = 0
                            prefs.sourcePreference = 0
                        }
                    }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.source_pref_hdr_first),
                            isSelected = sourcePref == 1
                        ) {
                            sourcePref = 1
                            prefs.sourcePreference = 1
                        }
                    }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.source_pref_smooth),
                            isSelected = sourcePref == 2
                        ) {
                            sourcePref = 2
                            prefs.sourcePreference = 2
                        }
                    }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.source_pref_default),
                            isSelected = sourcePref == 3
                        ) {
                            sourcePref = 3
                            prefs.sourcePreference = 3
                        }
                    }

                    // ===== 跳过片头 =====
                    item { SectionTitle(stringResource(R.string.skip_intro)) }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.manual_skip_intro),
                            isSelected = !autoSkipIntro
                        ) {
                            autoSkipIntro = false
                            prefs.autoSkipIntro = false
                        }
                    }
                    item {
                        SettingOptionRow(
                            title = stringResource(R.string.auto_skip_intro),
                            isSelected = autoSkipIntro
                        ) {
                            autoSkipIntro = true
                            prefs.autoSkipIntro = true
                        }
                    }

                    // ===== 标记片头（服务端） =====
                    item { SectionTitle(stringResource(R.string.intro_marker_section)) }
                    item { MarkerDescText(stringResource(R.string.intro_marker_desc)) }
                    when (markerStatus) {
                        MarkerStatus.Loading -> {
                            item { MarkerDescText(stringResource(R.string.intro_marker_loading)) }
                        }
                        MarkerStatus.Denied -> {
                            item { MarkerDescText(stringResource(R.string.intro_marker_need_admin)) }
                        }
                        MarkerStatus.Failed -> {
                            item { MarkerDescText(stringResource(R.string.intro_marker_load_failed)) }
                        }
                        MarkerStatus.NoLibraries -> {
                            item { MarkerDescText(stringResource(R.string.intro_marker_no_libraries)) }
                        }
                        MarkerStatus.Loaded -> {
                            items(markerLibraries, key = { it.itemId }) { lib ->
                                Column {
                                    Text(
                                        text = stringResource(R.string.intro_marker_library_name, lib.name),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                                    )
                                    SettingOptionRow(
                                        title = stringResource(R.string.intro_marker_mode_never),
                                        isSelected = lib.selectedMode == 0
                                    ) { changeMarkerMode(lib, 0) }
                                    SettingOptionRow(
                                        title = stringResource(R.string.intro_marker_mode_scheduled),
                                        isSelected = lib.selectedMode == 1
                                    ) { changeMarkerMode(lib, 1) }
                                    SettingOptionRow(
                                        title = stringResource(R.string.intro_marker_mode_on_scan),
                                        isSelected = lib.selectedMode == 2
                                    ) { changeMarkerMode(lib, 2) }
                                }
                            }
                        }
                    }

                    // ===== 缓冲设置 =====
                    item { SectionTitle(stringResource(R.string.buffer_settings)) }
                    item {
                        BufferSettingRow(
                            nameResId = R.string.min_buffer,
                            descResId = R.string.min_buffer_desc,
                            recommendResId = R.string.min_buffer_recommend,
                            options = listOf(15_000, 20_000, 30_000, 45_000, 60_000, 90_000, 120_000),
                            currentValue = minBufferMs,
                            onValueChange = {
                                minBufferMs = it
                                prefs.minBufferMs = it
                            },
                            formatResId = R.string.seconds
                        )
                    }
                    item {
                        BufferSettingRow(
                            nameResId = R.string.max_buffer,
                            descResId = R.string.max_buffer_desc,
                            recommendResId = R.string.max_buffer_recommend,
                            options = listOf(30_000, 60_000, 90_000, 120_000, 180_000, 300_000),
                            currentValue = maxBufferMs,
                            onValueChange = {
                                maxBufferMs = it
                                prefs.maxBufferMs = it
                            },
                            formatResId = R.string.seconds
                        )
                    }
                    item {
                        BufferSettingRow(
                            nameResId = R.string.playback_buffer,
                            descResId = R.string.playback_buffer_desc,
                            recommendResId = R.string.playback_buffer_recommend,
                            options = listOf(1_000, 2_000, 3_000, 5_000, 8_000, 10_000),
                            currentValue = playbackBufferMs,
                            onValueChange = {
                                playbackBufferMs = it
                                prefs.playbackBufferMs = it
                            },
                            formatResId = R.string.seconds
                        )
                    }
                    item {
                        BufferSettingRow(
                            nameResId = R.string.rebuffer,
                            descResId = R.string.rebuffer_desc,
                            recommendResId = R.string.rebuffer_recommend,
                            options = listOf(3_000, 5_000, 8_000, 10_000, 15_000, 20_000),
                            currentValue = rebufferMs,
                            onValueChange = {
                                rebufferMs = it
                                prefs.rebufferMs = it
                            },
                            formatResId = R.string.seconds
                        )
                    }
                    item {
                        BufferSettingRow(
                            nameResId = R.string.buffer_size,
                            descResId = R.string.buffer_size_desc,
                            recommendResId = R.string.buffer_size_recommend,
                            options = listOf(32_768_000, 65_536_000, 134_217_728, 268_435_456, 536_870_912),
                            currentValue = bufferSizeBytes,
                            onValueChange = {
                                bufferSizeBytes = it
                                prefs.bufferSizeBytes = it
                            },
                            formatResId = R.string.mega_bytes
                        )
                    }
                    item {
                        // 重置默认（账号页按钮样式：聚焦白底黑字）
                        Surface(
                            onClick = {
                                val defaults = prefs.getBufferDefaults()
                                minBufferMs = defaults.minBufferMs
                                maxBufferMs = defaults.maxBufferMs
                                playbackBufferMs = defaults.playbackBufferMs
                                rebufferMs = defaults.rebufferMs
                                bufferSizeBytes = defaults.bufferSizeBytes
                                prefs.resetBufferDefaults()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color.White.copy(alpha = 0.95f),
                                contentColor = Color.White,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.reset_default),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

/**
 * 设置选项行（账号页样式）：
 * 未聚焦：5% 白底 + 12% 白边框；选中项：主题色边框 + 淡色背景 + 对勾
 * 聚焦：白色背景 + 黑色文字 + 2dp 白边框
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingOptionRow(
    title: String,
    isSelected: Boolean,
    isFirst: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
    if (isFirst && focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = if (isSelected) {
                Border(BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary))
            } else {
                Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)))
            },
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f)
            else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.95f),
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                fontSize = 16.sp,
                maxLines = 1
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.width(22.dp),
                    tint = LocalContentColor.current
                )
            }
        }
    }
}

/** 标记片头区块的加载状态 */
private sealed class MarkerStatus {
    object Loading : MarkerStatus()
    object Denied : MarkerStatus()
    object Failed : MarkerStatus()
    object NoLibraries : MarkerStatus()
    object Loaded : MarkerStatus()
}

/** 单个电视剧媒体库的标记片头状态 */
private data class MarkerLibraryState(
    val itemId: String,
    val name: String,
    /** 服务端已保存的模式 */
    val savedMode: Int,
    /** 当前界面选中的模式 */
    val selectedMode: Int = savedMode,
    /** 服务端返回的完整 LibraryOptions，修改时仅改标记片头字段后整体回传 */
    val libraryOptions: JsonObject
)

/**
 * 从 LibraryOptions 读取标记片头模式：
 * 0 从不 / 1 定时任务扫描 / 2 定时任务 + 添加新媒体时
 */
private fun markerModeOf(options: JsonObject?): Int {
    val enabled = options?.get("EnableMarkerDetection")
        ?.takeIf { !it.isJsonNull }?.asBoolean ?: false
    val duringScan = options?.get("EnableMarkerDetectionDuringLibraryScan")
        ?.takeIf { !it.isJsonNull }?.asBoolean ?: false
    return when {
        !enabled -> 0
        duringScan -> 2
        else -> 1
    }
}

/** 将标记片头模式写回 LibraryOptions（仅改动这两个字段） */
private fun applyMarkerMode(options: JsonObject, mode: Int) {
    options.addProperty("EnableMarkerDetection", mode != 0)
    options.addProperty("EnableMarkerDetectionDuringLibraryScan", mode == 2)
}

/** 标记片头区块的说明/状态文字 */
@Composable
private fun MarkerDescText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.6f),
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
