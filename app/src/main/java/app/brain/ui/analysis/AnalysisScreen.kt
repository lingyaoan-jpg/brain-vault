package app.brain.ui.analysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.data.analysis.AnalysisScope
import app.brain.ui.common.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    initialScope: AnalysisScope = AnalysisScope(),
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var showNewDialog by remember { mutableStateOf(initialScope.hasCriterion()) }
    var titleInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("深度分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            Button(onClick = { showNewDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("新建会话", modifier = Modifier.padding(start = 6.dp))
            }
        },
    ) { innerPadding ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("还没有深度分析会话。", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "新建会话时选择范围，AI 只读取范围内的记录。分析对话与普通记录分开存放。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions, key = { it.id }) { session ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(session.id) }
                        .padding(vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "更新于 ${formatTime(session.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.deleteSession(session.id) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("操作失败") },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("知道了") }
            },
        )
    }

    if (showNewDialog) {
        ScopePickerDialog(
            categories = categories,
            records = records,
            initial = initialScope,
            onConfirm = { scope ->
                showNewDialog = false
                if (titleInput.isBlank()) {
                    titleInput = scope.autoTitle()
                }
                viewModel.createSession(titleInput, scope) { id ->
                    onOpenChat(id)
                }
            },
            onDismiss = { showNewDialog = false },
        )
    }
}

private fun AnalysisScope.hasCriterion(): Boolean =
    topics.isNotEmpty() || categoryIds.isNotEmpty() ||
        dateFrom != null || dateTo != null || recordIds.isNotEmpty()

private fun AnalysisScope.autoTitle(): String {
    val parts = buildList {
        if (topics.isNotEmpty()) add(topics.joinToString("、"))
        if (dateFrom != null && dateTo != null) add("时间范围")
        if (recordIds.isNotEmpty()) add("${recordIds.size} 条记录")
    }
    return if (parts.isEmpty()) "深度分析" else parts.joinToString(" · ")
}