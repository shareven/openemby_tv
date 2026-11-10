package com.xxxx.emby_tv.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.repository.EmbyRepository
import kotlinx.coroutines.launch

/** 标记片头区块的加载状态（设置页与播放页菜单共用） */
enum class MarkerLoadStatus { Loading, Denied, Failed, NoLibraries, Loaded }

/** 单个电视剧媒体库的标记片头状态（设置页与播放页菜单共用） */
data class MarkerLibraryState(
    val itemId: String,
    val name: String,
    /** 服务端已保存的模式 */
    val savedMode: Int,
    /** 当前界面选中的模式 */
    val selectedMode: Int = savedMode,
    /** 服务端返回的完整 LibraryOptions，修改时仅改标记片头字段后回传 */
    val libraryOptions: JsonObject,
    /** 服务端 LibraryOptions 是否为数组包装（回传时保持一致结构） */
    val wrapInArray: Boolean = false
)

/**
 * 从 VirtualFolderInfo 列表提取电视剧媒体库的标记片头状态
 */
fun toMarkerLibraries(
    folders: List<com.xxxx.emby_tv.data.model.VirtualFolderInfoDto>
): List<MarkerLibraryState> {
    return folders
        .filter { it.collectionType.equals("tvshows", ignoreCase = true) && it.libraryOptions != null }
        .map {
            MarkerLibraryState(
                itemId = it.effectiveItemId,
                name = it.name,
                savedMode = markerModeOf(it.libraryOptions),
                libraryOptions = it.libraryOptions!!,
                wrapInArray = it.libraryOptionsIsArray
            )
        }
}

/**
 * 从 LibraryOptions 读取标记片头模式：
 * 0 从不 / 1 定时任务扫描 / 2 定时任务 + 添加新媒体时
 */
fun markerModeOf(options: JsonObject?): Int {
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
fun applyMarkerMode(options: JsonObject, mode: Int) {
    options.addProperty("EnableMarkerDetection", mode != 0)
    options.addProperty("EnableMarkerDetectionDuringLibraryScan", mode == 2)
}

/**
 * 标记片头（服务端）设置区块：按媒体库显示三档选项。
 * 切换时先乐观更新界面，写入服务端成功后确认；失败（如未开通 Emby
 * Premiere / 权限不足）时提示错误并回滚到服务端已保存的模式。
 *
 * 状态更新通过 [onUpdateLibrary] 按 itemId 就地修改，避免异步回调
 * 引用旧列表把界面选中的值覆盖回去（修复切换后界面不刷新的问题）。
 * 选项行的样式通过 [optionRow] 注入，以适配设置页和播放页菜单两种外观
 */
@Composable
fun IntroMarkerSection(
    libraries: List<MarkerLibraryState>,
    onUpdateLibrary: (itemId: String, update: (MarkerLibraryState) -> MarkerLibraryState) -> Unit,
    showDescription: Boolean,
    libraryNameLeading: androidx.compose.ui.graphics.Color? = null,
    /** 选项行之间的间距；选项行自带内边距的调用方（播放页菜单）传 0 */
    rowSpacing: Dp = 0.dp,
    optionRow: @Composable (label: String, selected: Boolean, onClick: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column {
        if (showDescription) {
            Text(
                text = stringResource(R.string.intro_marker_desc),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        libraries.forEach { lib ->
            Column {
                Text(
                    text = stringResource(R.string.intro_marker_library_name, lib.name),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = libraryNameLeading ?: androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)
                )
                MarkerModeOptions(
                    labels = listOf(
                        stringResource(R.string.intro_marker_mode_never),
                        stringResource(R.string.intro_marker_mode_scheduled),
                        stringResource(R.string.intro_marker_mode_on_scan)
                    ),
                    selectedMode = lib.selectedMode,
                    rowSpacing = rowSpacing,
                    optionRow = optionRow
                ) { newMode ->
                    if (lib.selectedMode == newMode) return@MarkerModeOptions
                    // 先乐观更新界面
                    onUpdateLibrary(lib.itemId) { it.copy(selectedMode = newMode) }
                    scope.launch {
                        try {
                            applyMarkerMode(lib.libraryOptions, newMode)
                            EmbyRepository.getInstance(context).updateLibraryMarkerDetection(
                                lib.itemId, lib.libraryOptions, lib.wrapInArray
                            )
                            // 成功：确认服务端已保存的模式
                            onUpdateLibrary(lib.itemId) {
                                it.copy(savedMode = newMode, selectedMode = newMode)
                            }
                        } catch (e: Exception) {
                            // 失败：回滚界面选择，并还原回传对象避免残留脏数据
                            applyMarkerMode(lib.libraryOptions, lib.savedMode)
                            onUpdateLibrary(lib.itemId) { it.copy(selectedMode = it.savedMode) }
                            val msg = if (e.message?.contains("HTTP Error") == true) {
                                context.getString(R.string.intro_marker_update_denied)
                            } else {
                                context.getString(R.string.intro_marker_update_failed)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerModeOptions(
    labels: List<String>,
    selectedMode: Int,
    rowSpacing: Dp,
    optionRow: @Composable (label: String, selected: Boolean, onClick: () -> Unit) -> Unit,
    onModeChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        labels.forEachIndexed { index, label ->
            Box(modifier = Modifier.fillMaxWidth()) {
                optionRow(label, selectedMode == index) { onModeChange(index) }
            }
        }
    }
}
