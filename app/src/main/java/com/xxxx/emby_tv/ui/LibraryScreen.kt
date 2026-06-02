package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.components.BuildItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.components.NoData
import com.xxxx.emby_tv.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    parentId: String,
    title: String,
    type: String,
    libraryViewModel: LibraryViewModel,
    onNavigateToSeries: (String) -> Unit,
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""

    val libraryItems = libraryViewModel.libraryItems
    val isLoadingMore = libraryViewModel.isLoadingMore
    val hasMoreData = libraryViewModel.hasMoreData
    val totalCount = libraryViewModel.totalCount

    val currentSortBy = libraryViewModel.currentSortBy
    val currentSortOrder = libraryViewModel.currentSortOrder
    val currentFilter = libraryViewModel.currentFilter

    var showSortDialog by remember { mutableStateOf(false) }

    val sortByLabel = stringResource(
        LibraryViewModel.SORT_OPTIONS.find { it.first == currentSortBy }?.second
            ?: R.string.sort_name
    )
    val sortIcon = if (currentSortOrder == "Ascending") Icons.Default.ArrowUpward else Icons.Default.ArrowDownward

    LaunchedEffect(parentId, type) {
        libraryViewModel.loadItems(parentId, type)
    }

    LaunchedEffect(libraryItems) {
        if (libraryItems != null && libraryItems.isNotEmpty()) {
            gridState.scrollToItem(0)
            firstItemFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && hasMoreData && !isLoadingMore) {
                libraryViewModel.loadMore()
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            currentSortBy = currentSortBy,
            currentSortOrder = currentSortOrder,
            onSortSelected = { sortBy, sortOrder ->
                libraryViewModel.updateSortAndFilter(sortBy, sortOrder, currentFilter)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (title.isNotEmpty()) title else stringResource(R.string.my_libraries),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            if (libraryItems != null && totalCount > 0) {
                Text(
                    text = "${libraryItems.size}/$totalCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showSortDialog = true },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                    focusedContainerColor = MaterialTheme.colorScheme.secondary,
                    focusedContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.sort)}: $sortByLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = sortIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
            }

            LibraryViewModel.FILTER_OPTIONS.forEach { (filterValue, labelRes) ->
                val label = stringResource(labelRes)
                val isSelected = currentFilter == filterValue

                Surface(
                    onClick = {
                        if (!isSelected) {
                            libraryViewModel.updateSortAndFilter(
                                currentSortBy, currentSortOrder, filterValue
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            Color.White.copy(alpha = 0.7f)
                        },
                        focusedContainerColor = MaterialTheme.colorScheme.secondary,
                        focusedContentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = if (!isSelected) Border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) else Border(BorderStroke(0.dp, Color.Transparent)),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    )
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        if (libraryItems == null) {
            Loading()
        } else if (libraryItems.isEmpty()) {
            NoData()
        } else {
            val items = libraryItems

            val maxLength = 220.dp

            val (aspectRatioOver1List, aspectRatioUnder1List) = items
                .mapNotNull { it.primaryImageAspectRatio?.toFloat() }
                .partition { it > 1.0f }

            val maxAspectRatio = if (aspectRatioOver1List.size >= aspectRatioUnder1List.size) {
                aspectRatioOver1List.maxOrNull() ?: 1.777f
            } else {
                aspectRatioUnder1List.maxOrNull() ?: 0.666f
            }

            val imgWidth = if (maxAspectRatio >= 1.0f) {
                maxLength
            } else {
                maxLength * maxAspectRatio
            }

            val num = if (maxAspectRatio > 1.0f) 4 else 6

            LazyVerticalGrid(
                columns = GridCells.Fixed(num),
                state = gridState,
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items.size, key = { items[it].id ?: it.hashCode() }) { index ->
                    val item = items[index]
                    val id = item.id ?: ""
                    if (id.isNotEmpty()) {
                        val itemModifier = if (index == 0) {
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BuildItem(
                                item = item,
                                imgWidth = imgWidth,
                                aspectRatio = maxAspectRatio,
                                modifier = itemModifier,
                                isMyLibrary = false,
                                serverUrl = serverUrl,
                                onItemClick = {
                                    onNavigateToSeries(id)
                                }
                            )
                        }
                    }
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SortDialog(
    currentSortBy: String,
    currentSortOrder: String,
    onSortSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val focusRequesters = remember { LibraryViewModel.SORT_OPTIONS.map { FocusRequester() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .heightIn(max = 480.dp),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = Color(0xFF1A1A1A).copy(alpha = 0.95f),
                    contentColor = Color.White
                ),
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sort),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            LibraryViewModel.SORT_OPTIONS,
                            key = { _, opt -> opt.first }
                        ) { index, (sortByValue, labelRes) ->
                            val label = stringResource(labelRes)
                            val isSelected = currentSortBy == sortByValue

                            Surface(
                                onClick = {
                                    val newOrder = if (sortByValue == currentSortBy) {
                                        if (currentSortOrder == "Ascending") "Descending" else "Ascending"
                                    } else {
                                        currentSortOrder
                                    }
                                    onSortSelected(sortByValue, newOrder)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[index]),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                    } else {
                                        Color.Transparent
                                    },
                                    contentColor = Color.White,
                                    focusedContainerColor = MaterialTheme.colorScheme.secondary,
                                    focusedContentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = if (currentSortOrder == "Ascending") Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            val currentSortIndex = LibraryViewModel.SORT_OPTIONS.indexOfFirst { it.first == currentSortBy }
            LaunchedEffect(Unit) {
                val targetIndex = if (currentSortIndex >= 0) currentSortIndex else 0
                if (targetIndex < focusRequesters.size) {
                    focusRequesters[targetIndex].requestFocus()
                }
            }
        }
    }
}
