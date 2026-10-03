package com.xxxx.emby_tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import com.xxxx.emby_tv.R


@Composable
fun BufferSettingsTab(
    minBufferMs: Int,
    onMinBufferMsChange: (Int) -> Unit,
    maxBufferMs: Int,
    onMaxBufferMsChange: (Int) -> Unit,
    playbackBufferMs: Int,
    onPlaybackBufferMsChange: (Int) -> Unit,
    rebufferMs: Int,
    onRebufferMsChange: (Int) -> Unit,
    bufferSizeBytes: Int,
    onBufferSizeBytesChange: (Int) -> Unit,
    onResetDefault: () -> Unit,
    onReplay: () -> Unit,
) {


    LazyColumn(
        modifier = Modifier
            .padding(horizontal = 50.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 150.dp)
    ) {
        item {
            Surface(
                onClick = onReplay,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Text(
                            text = stringResource(R.string.apply_to_playback),
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            BufferSettingRow(
                nameResId = R.string.min_buffer,
                descResId = R.string.min_buffer_desc,
                recommendResId = R.string.min_buffer_recommend,
                options = listOf(15_000, 20_000, 30_000, 45_000, 60_000, 90_000, 120_000),
                currentValue = minBufferMs,
                onValueChange = onMinBufferMsChange,
                formatResId = R.string.seconds
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            BufferSettingRow(
                nameResId = R.string.max_buffer,
                descResId = R.string.max_buffer_desc,
                recommendResId = R.string.max_buffer_recommend,
                options = listOf(30_000, 60_000, 90_000, 120_000, 180_000, 300_000),
                currentValue = maxBufferMs,
                onValueChange = onMaxBufferMsChange,
                formatResId = R.string.seconds
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            BufferSettingRow(
                nameResId = R.string.playback_buffer,
                descResId = R.string.playback_buffer_desc,
                recommendResId = R.string.playback_buffer_recommend,
                options = listOf(1_000, 2_000, 3_000, 5_000, 8_000, 10_000),
                currentValue = playbackBufferMs,
                onValueChange = onPlaybackBufferMsChange,
                formatResId = R.string.seconds
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            BufferSettingRow(
                nameResId = R.string.rebuffer,
                descResId = R.string.rebuffer_desc,
                recommendResId = R.string.rebuffer_recommend,
                options = listOf(3_000, 5_000, 8_000, 10_000, 15_000, 20_000),
                currentValue = rebufferMs,
                onValueChange = onRebufferMsChange,
                formatResId = R.string.seconds
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            BufferSettingRow(
                nameResId = R.string.buffer_size,
                descResId = R.string.buffer_size_desc,
                recommendResId = R.string.buffer_size_recommend,
                options = listOf(32_768_000, 65_536_000, 134_217_728, 268_435_456, 536_870_912),
                currentValue = bufferSizeBytes,
                onValueChange = onBufferSizeBytesChange,
                formatResId = R.string.mega_bytes
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }


        item {
            Surface(
                onClick = onReplay,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSecondary
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.apply_to_playback),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            Surface(
                onClick = onResetDefault,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.secondary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onSecondary,
                    containerColor = Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSecondary
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reset_default),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun BufferSettingRow(
    nameResId: Int,
    descResId: Int,
    recommendResId: Int,
    options: List<Int>,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    formatResId: Int,
) {
    var currentIndex by remember(currentValue, options) {
        mutableIntStateOf(options.indexOf(currentValue).coerceAtLeast(0))
    }

    fun decrementValue() {
        if (currentIndex > 0) {
            currentIndex--
            onValueChange(options[currentIndex])
        }
    }

    fun incrementValue() {
        if (currentIndex < options.size - 1) {
            currentIndex++
            onValueChange(options[currentIndex])
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(nameResId),
                style = TvMaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "<",
                    color = if (currentIndex > 0) Color.White else Color.Gray,
                    fontSize = 20.sp
                )


                Spacer(modifier = Modifier.width(16.dp))

                Surface(
                    onClick = {},
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                    scale = ClickableSurfaceDefaults
                        .scale(focusedScale = 1.1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        pressedContentColor = MaterialTheme.colorScheme.onSecondary,
                        pressedContainerColor = MaterialTheme.colorScheme.secondary,
                        focusedContentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .onKeyEvent { keyEvent ->
                            when {
                                keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                                    decrementValue()
                                    true
                                }

                                keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                                    incrementValue()
                                    true
                                }

                                else -> false
                            }
                        }
                ) {
                    Text(
                        text = stringResource(
                            formatResId,
                            options[currentIndex] / if (formatResId == R.string.mega_bytes) 1_048_576 else 1000
                        ),
                        style = TvMaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))


                Text(
                    text = ">",
                    color = if (currentIndex < options.size - 1) Color.White else Color.Gray,
                    fontSize = 20.sp
                )

            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(descResId),
            style = TvMaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(recommendResId),
            style = TvMaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}
