package app.brain.ui.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.data.db.dao.SuggestionWithRecord
import app.brain.ui.common.dimensionLabel
import app.brain.ui.components.dimensionBaseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionScreen(
    onBack: () -> Unit,
    viewModel: SuggestionViewModel = hiltViewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("整理建议") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (suggestions.isNotEmpty()) {
                        TextButton(onClick = viewModel::acceptAll) {
                            Text("全部接受")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (suggestions.isEmpty()) {
            Text(
                text = "暂无待处理的分类建议。\n当你手动修改 AI 分类达到一定次数后，这里会提示可能同样需要调整的历史记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(innerPadding).padding(24.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "AI 按你的修改习惯发现 ${suggestions.size} 条可能分类不当的历史记录。接受后才会修改，忽略则不再提示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(suggestions, key = { it.suggestionId }) { item ->
                SuggestionCard(item = item, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun SuggestionCard(item: SuggestionWithRecord, viewModel: SuggestionViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = item.recordTitle?.takeIf { it.isNotBlank() } ?: item.recordContent.lineSequence().firstOrNull().orEmpty().trim(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.recordContent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${dimensionLabel(item.dimension)}：建议把「${item.fromName}」改为「${item.toName}」",
                style = MaterialTheme.typography.bodyMedium,
                color = dimensionBaseColor(item.dimension),
            )
            Text(
                text = item.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { viewModel.ignore(item) }) { Text("忽略") }
                TextButton(onClick = { viewModel.accept(item) }) { Text("接受") }
            }
        }
    }
}