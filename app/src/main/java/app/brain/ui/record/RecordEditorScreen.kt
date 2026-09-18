package app.brain.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditorScreen(
    onBack: () -> Unit,
    onNewRecordOrganized: (String?) -> Unit = {},
    viewModel: RecordEditorViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val organizing by viewModel.organizing.collectAsStateWithLifecycle()
    val organizedCategoryId by viewModel.organizedCategoryId.collectAsStateWithLifecycle()

    var showExitDialog by remember { mutableStateOf(false) }
    val requestExit: () -> Unit = {
        if (viewModel.hasUnsavedContent()) showExitDialog = true else onBack()
    }

    BackHandler { requestExit() }

    // 编辑已有记录：保存后直接返回。
    LaunchedEffect(saved) {
        if (saved && viewModel.isEdit) onBack()
    }

    // 新记录：等 AI 分类完成后跳转对应卡片；超时/失败时 categoryId 为 null，去「全部记录」。
    LaunchedEffect(saved, organizing, organizedCategoryId) {
        if (saved && !viewModel.isEdit && !organizing) {
            onNewRecordOrganized(organizedCategoryId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (viewModel.isEdit) "编辑记录" else "新建收容") },
                    navigationIcon = {
                        IconButton(onClick = requestExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = viewModel::save,
                            enabled = content.isNotBlank() && !saving,
                        ) {
                            Text(if (saving) "保存中…" else "保存")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .padding(horizontal = 16.dp),
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                text = "给这条收容起个名字",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                BasicTextField(
                    value = content,
                    onValueChange = viewModel::onContentChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp, bottom = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (content.isEmpty()) {
                            Text(
                                text = "开始收容此刻",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
            }
        }

        if (organizing) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在收容…",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(if (viewModel.isEdit) "还没保存" else "还没收容") },
                text = {
                    Text(
                        if (viewModel.isEdit) "这次的修改还没保存，要保存吗？"
                        else "这条内容还没保存，要现在收容吗？"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            viewModel.save()
                        },
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = {
                                showExitDialog = false
                                viewModel.discardDraft()
                                onBack()
                            },
                        ) {
                            Text("不保存")
                        }
                    }
                },
            )
        }
    }
}
