package app.brain.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.ui.components.RecordCard

/** 收容所卡片对应的记录列表：全部记录或单个内容类型。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardRecordsScreen(
    categoryId: String,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenEditor: () -> Unit,
    viewModel: CardRecordsViewModel = hiltViewModel(),
) {
    val cardName by viewModel.cardName.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isAll = categoryId == "all"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cardName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
                    showTypeTag = isAll,
                )
            }
        }
    }
}
