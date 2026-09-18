package app.brain.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.RecordEntity
import app.brain.data.db.dao.RecordCategoryWithCategory
import app.brain.data.export.ExportFormat
import app.brain.ui.common.displayTitle
import app.brain.ui.common.formatTime
import app.brain.ui.common.dimensionLabel
import app.brain.ui.common.statusLabel
import app.brain.ui.components.FavoriteYellow
import app.brain.ui.components.TagChip
import app.brain.ui.components.dimensionBaseColor

// 详情页下半部分的三个抽屉：分类 / 后续感想 / AI深度分析
private const val TAB_CATEGORY = 0
private const val TAB_COMMENT = 1
private const val TAB_ANALYSIS = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordDetailScreen(
    recordId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenAnalysis: (String) -> Unit = {},
    viewModel: RecordDetailViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val currentType = categories.firstOrNull { it.dimension == CategoryEntity.DIM_TYPE }
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentInput by viewModel.commentInput.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val exportedFile by viewModel.exportedFile.collectAsStateWithLifecycle()
    val relatedSessions by viewModel.relatedSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showCardPicker by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(ExportFormat.TEXT) }
    var exportMeta by remember { mutableStateOf(true) }
    // 默认停在「后续感想」：打开一条记录，最想看的往往是后来补了什么
    var detailTab by rememberSaveable { mutableStateOf(TAB_COMMENT) }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = record?.let { displayTitle(it) } ?: "",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(recordId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑正文")
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "导出")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                },
            )
        },
        bottomBar = {
            if (detailTab == TAB_COMMENT) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = commentInput,
                                onValueChange = viewModel::onCommentChange,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("写下此刻的补充想法…") },
                                minLines = 1,
                                maxLines = 3,
                                shape = RoundedCornerShape(20.dp),
                            )
                            TextButton(
                                onClick = viewModel::addComment,
                                enabled = commentInput.isNotBlank(),
                            ) {
                                Text("添加")
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (record == null) {
            Text(
                text = "记录不存在或已被删除。",
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }

        val item = record!!
        // 抽屉空着时列表没有内容可滚，底部留半屏空白，tab 才拉得上来
        val tabScrollRoom = (LocalConfiguration.current.screenHeightDp / 2).dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = tabScrollRoom),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // 最上方标注所属内容类型卡片，点一下就能把这条挪到别的卡片
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCardPicker = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "所属卡片：${currentType?.name ?: "未归类"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "换一张卡片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            item {
                // 整条记录 = 一张卡片（跟收容所列表里那张同款），点进来只是把它展开了
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                // 右上角一行：置顶、日期、收藏（标签搬去「分类」tab 了）
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = viewModel::togglePinned,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = if (item.isPinned) "取消置顶" else "置顶",
                                tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = formatTime(item.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            onClick = viewModel::toggleFavorite,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = if (item.isFavorite) "取消收藏" else "收藏",
                                tint = if (item.isFavorite) FavoriteYellow else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (item.status != RecordEntity.STATUS_ORGANIZED) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(detailStatusColor(item.status), CircleShape),
                            )
                            Text(
                                text = statusLabel(item.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = detailStatusColor(item.status),
                            )
                            if (item.status == RecordEntity.STATUS_FAILED) {
                                TextButton(
                                    onClick = viewModel::retryOrganize,
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    Text("重试", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                // 正文直接展示，无“原文”标题、无分隔线
                SelectionContainer {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                }
                }
            }

            item {
                TabRow(selectedTabIndex = detailTab, containerColor = Color.Transparent) {
                    Tab(
                        selected = detailTab == TAB_CATEGORY,
                        onClick = { detailTab = TAB_CATEGORY },
                        text = { Text("分类") },
                    )
                    Tab(
                        selected = detailTab == TAB_COMMENT,
                        onClick = { detailTab = TAB_COMMENT },
                        text = { Text("后续感想") },
                    )
                    Tab(
                        selected = detailTab == TAB_ANALYSIS,
                        onClick = { detailTab = TAB_ANALYSIS },
                        text = { Text("AI深度分析") },
                    )
                }
            }

            when (detailTab) {
                TAB_CATEGORY -> item {
                    CategoryTabContent(
                        categories = categories,
                        onEdit = {
                            viewModel.startEditCategories()
                            showCategoryDialog = true
                        },
                    )
                }

                TAB_ANALYSIS -> if (relatedSessions.isEmpty()) {
                    item { TabEmptyText("还没有分析") }
                } else {
                    items(relatedSessions, key = { it.id }) { session ->
                        Surface(
                            onClick = { onOpenAnalysis(session.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                    }
                }

                else -> if (comments.isEmpty()) {
                    // 空着就好，下面那个输入框本身就是入口
                } else {
                    itemsIndexed(comments, key = { _, comment -> comment.id }) { _, comment ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = comment.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = formatTime(comment.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出这条记录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        ExportFormat.TEXT to "纯文本 .txt",
                        ExportFormat.WORD to "Word 文档 .docx",
                        ExportFormat.IMAGE to "图片 .png",
                    ).forEach { (format, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { exportFormat = format }
                                .padding(vertical = 2.dp),
                        ) {
                            RadioButton(selected = exportFormat == format, onClick = { exportFormat = format })
                            Text(label)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportMeta = !exportMeta }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(checked = exportMeta, onCheckedChange = { exportMeta = it })
                        Text("包含时间、标签和评论")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        viewModel.export(exportFormat, exportMeta)
                    }
                ) {
                    Text("导出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这条记录？") },
            text = { Text("将移入回收站，30 天内可以恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
    if (showCategoryDialog) {
        val editSelection by viewModel.editSelection.collectAsStateWithLifecycle()
        val allCats by viewModel.allCategories.collectAsStateWithLifecycle()
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showCategoryDialog = false
                viewModel.cancelEditCategories()
            },
            title = { Text("编辑分类") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EDIT_DIMENSIONS.forEach { dimension ->
                        val selected = editSelection[dimension].orEmpty()
                        Text(
                            text = dimensionLabel(dimension),
                            style = MaterialTheme.typography.labelLarge,
                            color = dimensionBaseColor(dimension),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            allCats.filter { it.dimension == dimension }.forEach { cat ->
                                FilterChip(
                                    selected = cat.id in selected,
                                    onClick = { viewModel.toggleEditCategory(dimension, cat.id) },
                                    label = { Text(cat.name) },
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                placeholder = { Text("新建分类名") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(
                                onClick = {
                                    if (newName.isNotBlank()) {
                                        viewModel.addEditCategory(dimension, newName)
                                        newName = ""
                                    }
                                }
                            ) { Text("添加") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCategoryDialog = false
                        viewModel.saveEditCategories()
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCategoryDialog = false
                        viewModel.cancelEditCategories()
                    }
                ) { Text("取消") }
            },
        )
    }

    if (showCardPicker) {
        val cardOptions by viewModel.allCategories.collectAsStateWithLifecycle()
        var newCardName by remember { mutableStateOf("") }
        var cardError by remember { mutableStateOf<String?>(null) }
        val cards = cardOptions.filter { it.dimension == CategoryEntity.DIM_TYPE }
        AlertDialog(
            onDismissRequest = { showCardPicker = false },
            title = { Text("选择一个收容所") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    cards.forEach { card ->
                        val isCurrent = card.id == currentType?.categoryId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCardPicker = false
                                    if (!isCurrent) viewModel.moveToCard(card.id)
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "现在就放在这张卡片",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        OutlinedTextField(
                            value = newCardName,
                            onValueChange = {
                                newCardName = it
                                cardError = null
                            },
                            placeholder = { Text("新建一张卡片") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            isError = cardError != null,
                        )
                        TextButton(
                            onClick = {
                                val name = newCardName.trim()
                                when {
                                    name.isEmpty() -> cardError = "卡片名不能为空"
                                    name.length > 12 -> cardError = "12 个字以内就好"
                                    cards.any { it.name == name } -> cardError = "已经有这张卡片了"
                                    else -> {
                                        viewModel.createCardAndMove(name)
                                        showCardPicker = false
                                    }
                                }
                            }
                        ) { Text("新建") }
                    }
                    cardError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCardPicker = false }) { Text("取消") }
            },
        )
    }
}


/**
 * 「分类」抽屉：只列辅助维度（内容类型在顶部标题旁已经标过了）。
 * 纯列表 + 细线分隔，不做卡片；铅笔跟第一行齐平，随时能改。
 * 没有辅助分类时也要保留铅笔，否则就没入口了。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryTabContent(
    categories: List<RecordCategoryWithCategory>,
    onEdit: () -> Unit,
) {
    val helpers = categories.filter { it.dimension != CategoryEntity.DIM_TYPE }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (helpers.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CATEGORY_ROW_HEIGHT),
            ) {
                TabEmptyText("还没有分类", modifier = Modifier.weight(1f))
                EditCategoriesButton(onEdit)
            }
        } else {
            helpers.forEachIndexed { index, cat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CATEGORY_ROW_HEIGHT),
                ) {
                    TagChip(
                        text = cat.name,
                        dimension = cat.dimension,
                        categoryId = cat.categoryId,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (index == 0) EditCategoriesButton(onEdit)
                }
                if (index != helpers.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCategoriesButton(onEdit: () -> Unit) {
    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "编辑分类",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 分类列表每行都按这个高度走，第一行有铅笔按钮也不会比别的行高。 */
private val CATEGORY_ROW_HEIGHT = 44.dp

/** 抽屉里空着的时候只留一句灰字，不摆别的东西。 */
@Composable
private fun TabEmptyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
    )
}

private fun detailStatusColor(status: String): Color = when (status) {
    RecordEntity.STATUS_PROCESSING -> Color(0xFF7C5BA8)
    RecordEntity.STATUS_FAILED -> Color(0xFFC0392B)
    else -> Color(0xFFA49CB3)
}

private val EDIT_DIMENSIONS = listOf(
    CategoryEntity.DIM_TOPIC,
    CategoryEntity.DIM_URGENCY,
    CategoryEntity.DIM_IMPORTANCE,
    CategoryEntity.DIM_EMOTION,
)
