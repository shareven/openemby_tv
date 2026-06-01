package com.xxxx.emby_tv.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.Border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp

import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.BaseItemDto

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BuildItem(
    item: BaseItemDto,
    imgWidth: Dp,
    aspectRatio: Float,
    modifier: Modifier,
    isMyLibrary: Boolean,
    isShowImg17: Boolean = false,
    isShowOverview: Boolean = false,
    serverUrl: String, // 直接接受 serverUrl 而不是 AppModel
    accountName: String? = null,
    isPlaying: Boolean = false,
    onItemClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
) {
    val primaryColor = MaterialTheme.colorScheme.secondary
    val isSeries = item.isSeries
    val userData = item.userData
    val isPlayed = userData?.played ?: false
    val itemId = item.id
    val imageTags = item.imageTags
    val primaryTag = imageTags?.get("Primary")

    // Construct Image URL using Utils.getImageUrl
    val imageUrl = Utils.getImageUrl(serverUrl, item, isShowImg17)

    // TV 端核心组件：Surface 自动处理焦点缩放、边框和点击
    Surface(
        onClick = onItemClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.onSurface
                )
            )
        ),
        scale = ClickableSurfaceDefaults
            .scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.2f),
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            pressedContentColor = MaterialTheme.colorScheme.secondary,
            focusedContentColor = MaterialTheme.colorScheme.secondary
        ),

        modifier = modifier
            .width(imgWidth)
            //兼容移动端点击  TODO：移除
            // .clickable(interactionSource = null, onClick = onItemClick)
            .wrapContentHeight()
            .onKeyEvent { keyEvent ->
                if (onMenuClick != null && keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Menu -> {
                            onMenuClick()
                            true
                        }
                        Key.Bookmark -> {
                            onMenuClick()
                            true
                        }

                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 顶部图片区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(Color(0xFF2D2D2D), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {

                // 使用 Coil 加载图片
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = primaryColor, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                )

                // 账号名称显示
                if (!accountName.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = accountName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(primaryColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.playing),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // 播放标记
                if (isSeries || isPlayed) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(primaryColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPlayed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Finished",
                                modifier = Modifier
                                    .size(13.dp)
                                    .align(Alignment.Center),
                                tint = Color.White
                            )
                        }
                        else {
                            Text(
                            text = userData?.unplayedItemCount?.toString() ?: "",
                                color = Color.White,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // 2. 播放进度条
                if (!isSeries && !isMyLibrary) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .height(3.dp)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = item.playbackProgress)
                                .background(primaryColor)
                        )
                    }
                }
            }


            // 3. 文字内容区
            Column(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val title = if (isShowOverview) {
                    item.name
                } else {
                    item.seriesName ?: item.name
                } ?: "Unknown"

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                if (!isMyLibrary) {
                    val subTitle = if (item.parentIndexNumber != null) {
                        "S${item.parentIndexNumber}:E${item.indexNumber} ${item.name}"
                    } else if (item.type == "Actor") {
                        item.role ?: ""
                    } else {
                        item.productionYear?.toString() ?: "--"
                    }
                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }

                if (isShowOverview) {
                    Text(
                        text = item.overview ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
