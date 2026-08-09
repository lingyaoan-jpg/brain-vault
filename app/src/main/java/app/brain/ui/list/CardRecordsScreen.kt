package app.brain.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.data.db.entity.CategoryEntity
import app.brain.ui.components.RecordCard
import app.brain.ui.components.dimensionBaseColor

/** 收容所卡片对应的记录列表：全部记录或单个一级分类，右上角可筛选该分类下的二级分类。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardRecordsScreen(
    dimension: String,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenEditor: () -> Unit,
    viewModel: CardRecordsViewModel = hiltViewModel(),
) {
    val cardName by viewModel.cardName.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val filterOptions by viewModel.filterOptions.collectAsStateWithLifecycle()
    val isAll = dimension == "all"
    val showTypeTag = isAll || dimension != CategoryEntity.DIM_TYPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cardName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    CardFilterButton(
                        filter = filter,
                        options = filterOptions,
                        isAll = isAll,
                        onSelect = viewModel::selectFilter,
                        onClear = viewModel::clearFilter,
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 32.dp),
                    ) {
                        Text(
                            text = if (isAll) "还没有记录。" else "这个卡片下还没有记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenEditor) { Text("去收容") }
                    }
                }
            }
            items(items, key = { it.record.id }) { item ->
                RecordCard(
                    item = item,
                    onClick = { onOpenDetail(item.record.id) },
                    showTypeTag = showTypeTag,
                )
            }
        }
    }
}

@Composable
private fun CardFilterButton(
    filter: CardFilter?,
    options: CardFilterOptions,
    isAll: Boolean,
    onSelect: (String, String?) -> Unit,
    onClear: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var expandedDimension by remember { mutableStateOf<String?>(null) }
    val active = filter != null

    var triggerHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.onSizeChanged { triggerHeightPx = it.height },
    ) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(16.dp),
            color = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (open) {
            val density = LocalDensity.current
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { open = false },
                offset = with(density) { IntOffset(0, triggerHeightPx + 4.dp.roundToPx()) },
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.widthIn(min = 140.dp, max = 200.dp).heightIn(max = 360.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        if (options.isEmpty) {
                            DropdownMenuItemText("暂无分类可筛选")
                        } else {
                            if (filter != null) {
                                FilterActionRow(
                                    label = "清除筛选",
                                    onAction = {
                                        onClear()
                                        open = false
                                    },
                                )
                            }
                            options.dimensions.forEach { dim ->
                                if (isAll) {
                                    FilterDimensionHeader(
                                        dim = dim,
                                        expanded = expandedDimension == dim.dimension,
                                        selected = filter?.dimension == dim.dimension && filter?.categoryId == null,
                                        onSelectDimension = {
                                            onSelect(dim.dimension, null)
                                            open = false
                                        },
                                        onToggleExpand = {
                                            expandedDimension = if (expandedDimension == dim.dimension) null else dim.dimension
                                        },
                                    )
                                }
                                if (expandedDimension == dim.dimension || !isAll) {
                                    dim.categories.forEach { cat ->
                                        FilterCategoryRow(
                                            cat = cat,
                                            color = dimensionBaseColor(dim.dimension),
                                            selected = filter?.categoryId == cat.categoryId,
                                            onClick = {
                                                onSelect(dim.dimension, cat.categoryId)
                                                open = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownMenuItemText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun FilterActionRow(label: String, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FilterDimensionHeader(
    dim: FilterDimensionItem,
    expanded: Boolean,
    selected: Boolean,
    onSelectDimension: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectDimension)
            .padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
    ) {
        ColorDot(color = dimensionBaseColor(dim.dimension), size = 8.dp)
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = dim.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (dim.count > 0) {
            Text(
                text = "${dim.count} 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        IconButton(onClick = onToggleExpand, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun FilterCategoryRow(
    cat: FilterCategoryItem,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 26.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
    ) {
        ColorDot(color = color.copy(alpha = 0.55f), size = 6.dp)
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = cat.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${cat.count} 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
