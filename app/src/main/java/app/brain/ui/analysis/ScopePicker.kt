package app.brain.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.brain.data.analysis.AnalysisScope
import app.brain.data.db.entity.CategoryEntity
import app.brain.ui.common.dimensionLabel
import app.brain.ui.common.displayTitle
import app.brain.ui.common.RecordListItem
import app.brain.ui.common.DimensionOrder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 深度分析授权范围选择：分类维度多选 + 时间范围 + 具体记录。
 * 范围只用于"允许 AI 读取哪些记录"，不会修改任何原文。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScopePickerDialog(
    categories: List<CategoryEntity>,
    records: List<RecordListItem>,
    initial: AnalysisScope = AnalysisScope(),
    onConfirm: (AnalysisScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedCategoryIds by remember { mutableStateOf(initial.categoryIds.toSet()) }
    var dateFrom by remember {
        mutableStateOf(initial.dateFrom?.let { epochToDateText(it) } ?: "")
    }
    var dateTo by remember {
        mutableStateOf(initial.dateTo?.let { epochToDateText(it) } ?: "")
    }
    var selectedRecordIds by remember { mutableStateOf(initial.recordIds.toSet()) }
    var showRecords by remember { mutableStateOf(initial.recordIds.isNotEmpty()) }

    val grouped = remember(categories) { categories.groupBy { it.dimension } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分析范围") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "AI 只读取选中范围内的记录，其他内容不会被发送。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DimensionOrder.FILTER_ORDER.forEach { dimension ->
                    val dimCategories = grouped[dimension].orEmpty()
                    if (dimCategories.isEmpty()) return@forEach
                    Text(dimensionLabel(dimension), style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        dimCategories.forEach { category ->
                            FilterChip(
                                selected = category.id in selectedCategoryIds,
                                onClick = {
                                    selectedCategoryIds = if (category.id in selectedCategoryIds) {
                                        selectedCategoryIds - category.id
                                    } else {
                                        selectedCategoryIds + category.id
                                    }
                                },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
                Text("时间范围（可选）", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateFrom,
                        onValueChange = { dateFrom = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("从 yyyy-MM-dd") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = dateTo,
                        onValueChange = { dateTo = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("到 yyyy-MM-dd") },
                        singleLine = true,
                    )
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = showRecords, onCheckedChange = { showRecords = it })
                    Text("选择具体记录（${selectedRecordIds.size} 条）")
                }
                if (showRecords) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    ) {
                        items(records, key = { it.record.id }) { item ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = item.record.id in selectedRecordIds,
                                    onCheckedChange = { checked ->
                                        selectedRecordIds = if (checked) {
                                            selectedRecordIds + item.record.id
                                        } else {
                                            selectedRecordIds - item.record.id
                                        }
                                    },
                                )
                                Text(
                                    text = displayTitle(item.record),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 4.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val topics = categories
                    .filter { it.id in selectedCategoryIds && it.dimension == CategoryEntity.DIM_TOPIC }
                    .map { it.name }
                onConfirm(
                    AnalysisScope(
                        topics = topics,
                        categoryIds = selectedCategoryIds.toList(),
                        dateFrom = dateFrom.takeIf { it.isNotBlank() }?.let { dateTextToEpoch(it) },
                        dateTo = dateTo.takeIf { it.isNotBlank() }?.let { dateTextToEpoch(it) },
                        recordIds = selectedRecordIds.toList(),
                    )
                )
            }) { Text("开始") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun epochToDateText(epochMillis: Long): String {
    val date = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.toString()
}

private fun dateTextToEpoch(text: String): Long? = try {
    LocalDate.parse(text.trim())
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
} catch (e: DateTimeParseException) {
    null
}