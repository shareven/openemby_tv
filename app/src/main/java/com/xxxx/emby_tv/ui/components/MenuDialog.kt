package com.xxxx.emby_tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.xxxx.emby_tv.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xxxx.emby_tv.ui.theme.ThemeColorManager
import com.xxxx.emby_tv.ui.theme.ThemeColor


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MenuDialog(
    needUpdate: Boolean,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onUpdate: () -> Unit,
    onThemeChange: (ThemeColor) -> Unit,
    onSwitchAccount: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onProxySettings: (() -> Unit)? = null,
    isShowLogout: Boolean = true
) {
    val showThemeSelection = remember { mutableStateOf(false) }
    val isMenuVisible = remember { mutableStateOf(true) }
    val firstItemFocusRequester = remember { FocusRequester() }

    // 获取当前应用的主题色，用于焦点高亮
    val currentPrimaryColor = MaterialTheme.colorScheme.secondary

    if (showThemeSelection.value) {
        ThemeSelectionDialog(
            onDismiss = { showThemeSelection.value = false },
            onThemeSelected = { themeColor ->
                onThemeChange(themeColor)
                showThemeSelection.value = false
                onDismiss()
            }
        )
    } else {
        Dialog(
            onDismissRequest = {
                isMenuVisible.value = false
                onDismiss()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false) // 撑满全屏以实现渐变背景
        ) {
            // 1. 全屏沉浸式渐变背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                ,
                contentAlignment = Alignment.Center
            ) {
                // 2. 菜单容器：玻璃拟态卡片
                Surface(
                    modifier = Modifier
                        .width(500.dp) // 菜单不需要太宽，窄一点更精致
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // 列表项：带颜色联动的焦点效果
                        val menuItems = mutableListOf<@Composable () -> Unit>()

                        // 返回项
//                        menuItems.add {
//                            MenuListItem(
//                                text = stringResource(R.string.back),
//                                icon = Icons.AutoMirrored.Filled.ArrowBack,
//                                onClick = onDismiss,
//                                modifier = Modifier.focusRequester(firstItemFocusRequester),
//                                primaryColor = currentPrimaryColor
//                            )
//                        }

                        // 搜索项
                        if (onSearch != null) {
                            menuItems.add {
                                MenuListItem(
                                    text = stringResource(R.string.search),
                                    icon = Icons.Filled.Search,
                                    onClick = { onSearch(); onDismiss() },
                                    primaryColor = currentPrimaryColor
                                )
                            }
                        }

                        // 切换账号项
                        if (onSwitchAccount != null) {
                            menuItems.add {
                                MenuListItem(
                                    text = stringResource(R.string.switch_account),
                                    icon = Icons.Filled.SwitchAccount,
                                    onClick = { onSwitchAccount(); onDismiss() },
                                    primaryColor = currentPrimaryColor
                                )
                            }
                        }

                        // 登出项
                        if (isShowLogout) {
                            menuItems.add {
                                MenuListItem(
                                    text = stringResource(R.string.logout),
                                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                                    onClick = { onLogout(); onDismiss() },
                                    primaryColor = currentPrimaryColor
                                )
                            }
                        }

                        // 主题选择项
                        menuItems.add {
                            MenuListItem(
                                text = stringResource(R.string.theme_color),
                                icon = Icons.Filled.Palette,
                                onClick = { showThemeSelection.value = true },
                                primaryColor = currentPrimaryColor
                            )
                        }

                        // 代理设置项
                        if (onProxySettings != null) {
                            menuItems.add {
                                MenuListItem(
                                    text = stringResource(R.string.proxy_settings),
                                    icon = Icons.Filled.Settings,
                                    onClick = { onProxySettings(); onDismiss() },
                                    primaryColor = currentPrimaryColor
                                )
                            }
                        }

                        // 更新项
                        if (needUpdate) {
                            menuItems.add {
                                MenuListItem(
                                    text = stringResource(R.string.download_latest_version),
                                    icon = Icons.Filled.SystemUpdate,
                                    onClick = { onUpdate(); onDismiss() },
                                    primaryColor = currentPrimaryColor
                                )
                            }
                        }

                        // 渲染列表并添加间距
                        menuItems.forEachIndexed { index, item ->
                            item()
                            if (index < menuItems.size - 1) Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(showThemeSelection.value) {
        if (!showThemeSelection.value && isMenuVisible.value) {
            kotlinx.coroutines.delay(150)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isMenuVisible.value = false
        }
    }
}

/**
 * 封装一个漂亮的菜单项
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MenuListItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        scale = ListItemDefaults.scale(focusedScale = 1.05f),
        shape = ListItemDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ListItemDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White.copy(alpha = 0.8f),
            focusedContainerColor = primaryColor, // 聚焦时使用当前选中的主题色！
            focusedContentColor = Color.White
        ),
        headlineContent = {
            Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    )
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeColor) -> Unit
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val themeColors = ThemeColorManager.getThemeColors(context)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // 撑满全屏
    ) {
        // 1. 全屏沉浸式背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2B2B2B), Color(0xFF0A0A0A)),
                        center = Offset(0.5f, 0.5f)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 80.dp, vertical = 20.dp) // TV 端需要极大的页边距
            ) {


                //  颜色网格：使用更大的间距和更精致的卡片
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4), // 4列在 16:9 屏幕上视觉最平衡
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {

                    items(themeColors.size) { index ->
                        val theme = themeColors[index]

                        // 每一个主题色选项
                        Surface(
                            onClick = { onThemeSelected(theme) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .focusRequester(if (index == 0) firstItemFocusRequester else FocusRequester.Default),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f), // 稍微加大缩放感
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                            glow = ClickableSurfaceDefaults.glow(
                                focusedGlow = Glow(
                                    elevationColor = theme.secondary.copy(alpha = 0.5f),
                                    elevation = 20.dp
                                )
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(3.dp, Color.White))
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // 渐变背景叠加层
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                0.0f to theme.primaryDark,
                                                1.0f to theme.secondaryLight,
                                                start = Offset.Zero,
                                                end = Offset.Infinite
                                            )
                                        )
                                        .alpha(0.85f) // 让背景透出一点底部的深色，更有质感
                                )

                                Text(
                                    text = theme.name,
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }


            }
        }
    }

}