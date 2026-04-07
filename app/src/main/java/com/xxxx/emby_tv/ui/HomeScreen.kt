package com.xxxx.emby_tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.*
import com.xxxx.emby_tv.data.model.BaseItemDto
import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.components.BuildItem
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.components.MenuDialog
import com.xxxx.emby_tv.ui.components.NoData
import com.xxxx.emby_tv.ui.components.TopStatusBar
import com.xxxx.emby_tv.ui.viewmodel.HomeViewModel
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
    onSwitchAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    // 获取 serverUrl
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""

    // 从 HomeViewModel 获取数据
    val resumeItems = homeViewModel.resumeItems
    val libraryLatestItems = homeViewModel.libraryLatestItems
    val favoriteItems = homeViewModel.favoriteItems
    val isLoading = homeViewModel.isLoading

    // 检查更新
    LaunchedEffect(Unit) {
        mainViewModel.checkUpdate()
    }

    // 菜单对话框
    if (showMenu) {
        MenuDialog(
            needUpdate = mainViewModel.needUpdate,
            onDismiss = { showMenu = false },
            onLogout = {
                mainViewModel.logout()
                showMenu = false
            },
            onUpdate = {
                mainViewModel.checkUpdate()
                showMenu = false
                navController.navigate("update")
            },
            onThemeChange = { themeColor ->
                mainViewModel.saveThemeId(themeColor.id)
            },
            onSwitchAccount = {
                showMenu = false
                onSwitchAccount()
            },
            onSearch = {
                showMenu = false
                navController.navigate("search")
            }
        )
    }

    fun goPlay(item: BaseItemDto) {
        val id = item.id ?: ""
        val userData = item.userData
        val position = userData?.playbackPositionTicks ?: 0L
        navController.navigate("player/$id?position=$position")
    }

    // Calculate User Info
    val currentAccountId = repository.currentAccountId
    val currentAccount = repository.savedAccounts.find { it.id == currentAccountId }
    val userInfo = if (currentAccount != null) {
        val domain = try {
            val uri = java.net.URI(currentAccount.serverUrl)
            uri.host ?: currentAccount.serverUrl
        } catch (e: Exception) {
            currentAccount.serverUrl
        }
        "${currentAccount.username}@$domain"
    } else {
        null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部状态栏
        TopStatusBar(
            currentVersion = mainViewModel.currentVersion,
            newVersion = mainViewModel.newVersion,
            needUpdate = mainViewModel.needUpdate,
            showSearchButton = true,
            userInfo = userInfo,
            onMenuClick = { showMenu = true },
            onSearchClick = {
                navController.navigate("search")
            },
            onUserInfoClick = {
                navController.navigate("account")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 数据未加载完成时显示 Loading 组件
//        if(isLoading){
//            Loading()
//        }
        if (libraryLatestItems != null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // 我的媒体库
                item {
                    MediaSection(
                        title = stringResource(R.string.my_libraries),
                        items = libraryLatestItems,
                        isMyLibrary = true,
                        serverUrl = serverUrl,
                        onItemSelected = { item ->
                            val firstItem = item.latestItems?.firstOrNull()
                            val type = firstItem?.type ?: ""
                            val id = item.id ?: ""
                            val title = item.name ?: ""
                            navController.navigate("library/$id?libraryName=$title&type=$type")
                        },
                        onMenuPressed = { showMenu = true }
                    )
                }

                // 继续观看
                if (resumeItems != null && resumeItems.isNotEmpty()) {
                    item {
                        MediaSection(
                            title = stringResource(R.string.continue_watching),
                            items = resumeItems,
                            isShowImg17 = true,
                            isContinueWatching = true,
                            serverUrl = serverUrl,
                            onItemSelected = { item -> goPlay(item) },
                            onMenuPressed = { showMenu = true }
                        )
                    }
                }

                // 收藏
                if (favoriteItems != null && favoriteItems.isNotEmpty()) {
                    item {
                        MediaSection(
                            title = stringResource(R.string.favorite),
                            items = favoriteItems,
                            isShowImg17 = true,
                            serverUrl = serverUrl,
                            onItemSelected = { item -> goPlay(item) },
                            onMenuPressed = { showMenu = true }
                        )
                    }
                }

                // 各库最新内容
                itemsIndexed(
                    libraryLatestItems ?: emptyList(),
                    key = { _, library -> library.id ?: library.hashCode() }
                ) { _, library ->
                    MediaSection(
                        title = library.name ?: "",
                        items = library.latestItems ?: emptyList(),
                        serverUrl = serverUrl,
                        onItemSelected = { item ->
                            if (item.isSeries) {
                                val seriesId = item.id
                                if (!seriesId.isNullOrEmpty()) {
                                    homeViewModel.playNextUp(seriesId) { nextItem: BaseItemDto ->
                                        goPlay(nextItem)
                                    }
                                } else {
                                    goPlay(item)
                                }
                            } else {
                                goPlay(item)
                            }
                        },
                        onMenuPressed = { showMenu = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaSection(
    title: String,
    items: List<BaseItemDto>,
    isMyLibrary: Boolean = false,
    isShowImg17: Boolean = false,
    isContinueWatching: Boolean = false,
    serverUrl: String,
    onItemSelected: (BaseItemDto) -> Unit,
    onMenuPressed: () -> Unit,
) {
    val maxLength = when {
        isMyLibrary -> 194.dp
        else -> 214.dp
    }

    val maxAspectRatio = items.mapNotNull {
        val ratio = it.primaryImageAspectRatio?.toFloat()
        if (ratio == null || ratio == 1.0f) null else ratio
    }.maxOrNull() ?: 0.666f

    val imgWidth = if (maxAspectRatio >= 1f) {
        maxLength
    } else {
        (maxLength.value * maxAspectRatio).dp
    }
    val focusRequester = remember { FocusRequester() }

    // 当继续观看的 items 变化时，重新聚焦到第一个项目
    LaunchedEffect(Unit) {
        if (isContinueWatching && items.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Column {
        Text(
            text = title,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 16.dp)
        )

        if (items.isEmpty()) {
            NoData(modifier = Modifier.height(maxLength))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(
                    items,
                    key = { _, item -> item.id ?: item.hashCode() }
                ) { index, item ->

                    val modifier = if ((isContinueWatching) && index == 0) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }



                    BuildItem(
                        modifier = modifier,
                        item = item,
                        aspectRatio = maxAspectRatio,
                        imgWidth = imgWidth,
                        isShowImg17 = isShowImg17,
                        isMyLibrary = isMyLibrary,
                        serverUrl = serverUrl,
                        onItemClick = { onItemSelected(item) },
                        onMenuClick = { onMenuPressed() },
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))
}
