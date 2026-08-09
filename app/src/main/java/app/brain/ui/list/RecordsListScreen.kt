package app.brain.ui.list

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.export.ExportFormat
import app.brain.ui.common.dimensionLabel
import app.brain.ui.components.RecordCard
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsListScreen(
    mode: RecordsListMode,
    title: String,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: RecordsListViewModel = hiltViewModel(),
) {
    val items by when (mode) {
        RecordsListMode.TIMELINE -> viewModel.timeline
        RecordsListMode.FAVORITES -> viewModel.favorites
    }.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val exportedFile by viewModel.exportedFile.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selecting by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showBatchEditDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(ExportFormat.TEXT) }
    var exportMeta by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(exportedFile) {
        exportedFile?.let { file ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = file.mime
                putExtra(Intent.EXTRA_STREAM, file.uri)
                putExtra(Intent.EXTRA_SUBJECT, file.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(send, "导出 ${file.fileName}"))
            }
            viewModel.clearExport()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (selecting) {
                        Text("已选 ${selectedIds.size} 条")
                    } else {
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = {
                            selecting = false
                            viewModel.clearSelection()
                        }) { Text("取消") }
                    } else {
                        TextButton(onClick = { selecting = true }) { Text("选择") }
                    }
                },
            )
        },
        bottomBar = {
            if (selecting) {
                Surface {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val all = items.map { it.record.id }.toSet()
                                if (selectedIds == all) viewModel.clearSelection()
                                else items.forEach { viewModel.toggleSelect(it.record.id) }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("全选/全不选") }
                        OutlinedButton(
                            onClick = { showBatchEditDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("修改") }
                        Button(
                            onClick = { showExportDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("导出所选") }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        text = if (mode == RecordsListMode.FAVORITES) "还没有收藏的记录。" else "还没有记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.record.id }) { item ->
                RecordCard(
                    item = item,
                    selected = selecting && item.record.id in selectedIds,
                    onClick = {
                        if (selecting) {
                            viewModel.toggleSelect(item.record.id)
                        } else {
                            onOpenDetail(item.record.id)
                        }
                    },
                )
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("批量导出") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = exportFormat == ExportFormat.TEXT, onClick = { exportFormat = ExportFormat.TEXT })
                        Text("纯文本 .txt")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = exportFormat == ExportFormat.WORD, onClick = { exportFormat = ExportFormat.WORD })
                        Text("Word 文档 .docx")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { exportMeta = !exportMeta }.padding(vertical = 2.dp),
                    ) {
                        Checkbox(checked = exportMeta, onCheckedChange = { exportMeta = it })
                        Text("包含时间、标签和评论")
                    }
                    Text(
                        text = "将导出选中的 ${selectedIds.size} 条记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    viewModel.exportSelected(exportFormat, exportMeta)
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("取消") }
            },
        )
    }

    if (showBatchEditDialog) {
        BatchEditDialog(
            selectedCount = selectedIds.size,
            onDismiss = { showBatchEditDialog = false },
            onApplyTitle = { viewModel.batchSetTitle(it) },
            onApplyTime = { viewModel.batchSetTime(it) },
            onApplyDimension = { dimension, value -> viewModel.batchSetDimension(dimension, value) },
        )
    }
}

@Composable
private fun BatchEditDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onApplyTitle: (String) -> Unit,
    onApplyTime: (Long) -> Unit,
    onApplyDimension: (String, String) -> Unit,
) {
    var titleInput by remember { mutableStateOf("") }
    var dateInput by remember { mutableStateOf("") }
    var timeInput by remember { mutableStateOf("") }
    var dimensionExpanded by remember { mutableStateOf(false) }
    var dimension by remember { mutableStateOf(CategoryEntity.DIM_TYPE) }
    var valueInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量修改（已选 $selectedCount 条）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("标题", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("新标题") },
                        singleLine = true,
                    )
                    TextButton(onClick = {
                        onApplyTitle(titleInput)
                        titleInput = ""
                    }) { Text("应用") }
                }

                Text("首次记录时间", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("日期 yyyy-MM-dd") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("时间 HH:mm") },
                        singleLine = true,
                    )
                    TextButton(onClick = {
                        parseDateTime(dateInput, timeInput)?.let(onApplyTime)
                        dateInput = ""
                        timeInput = ""
                    }) { Text("应用") }
                }

                Text("分类（覆盖该维度）", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        OutlinedButton(onClick = { dimensionExpanded = true }) {
                            Text(dimensionLabel(dimension))
                        }
                        DropdownMenu(
                            expanded = dimensionExpanded,
                            onDismissRequest = { dimensionExpanded = false },
                        ) {
                            listOf(
                                CategoryEntity.DIM_TYPE,
                                CategoryEntity.DIM_TOPIC,
                                CategoryEntity.DIM_URGENCY,
                                CategoryEntity.DIM_IMPORTANCE,
                                CategoryEntity.DIM_EMOTION,
                            ).forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(dimensionLabel(d)) },
                                    onClick = {
                                        dimension = d
                                        dimensionExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("新分类名") },
                        singleLine = true,
                    )
                    TextButton(onClick = {
                        onApplyDimension(dimension, valueInput)
                        valueInput = ""
                    }) { Text("应用") }
                }
                Text(
                    text = "批量修改不会改写原文内容，仅更新标题、分类或时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

private fun parseDateTime(date: String, time: String): Long? {
    return try {
        val day = if (date.isBlank()) LocalDate.now() else LocalDate.parse(date.trim())
        val t = if (time.isBlank()) LocalTime.MIDNIGHT else LocalTime.parse(time.trim())
        LocalDateTime.of(day, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}