package app.brain.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.RecordEntity
import app.brain.ui.common.RecordListItem
import app.brain.ui.common.statusLabel
import app.brain.ui.common.formatTime

/**
 * 帖子体记录条目（微博/朋友圈风格）：
 * 彩色圆角标签在题目上方；日期右上角、收藏最右上角；正文为主；细线分隔。
 */
@Composable
fun RecordCard(
    item: RecordListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTypeTag: Boolean = true,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 第一行：内容类型（左）｜置顶｜日期｜收藏（最右上，小号黄星）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (showTypeTag) {
                    item.categories.firstOrNull { it.dimension == CategoryEntity.DIM_TYPE }?.let { cat ->
                        TagChip(text = cat.name, dimension = cat.dimension, categoryId = cat.categoryId)
                    }
                }
            }
            if (item.record.isPinned) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "已置顶",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp).size(16.dp),
                )
            }
            Text(
                text = formatTime(item.record.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.record.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "已收藏",
                    tint = FavoriteYellow,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        item.record.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = item.record.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        if (item.record.status != RecordEntity.STATUS_ORGANIZED) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(statusColor(item.record.status), CircleShape),
                )
                Text(
                    text = statusLabel(item.record.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(item.record.status),
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

private fun statusColor(status: String): Color = when (status) {
    RecordEntity.STATUS_PROCESSING -> Color(0xFF7C5BA8)
    RecordEntity.STATUS_FAILED -> Color(0xFFC0392B)
    else -> Color(0xFFA49CB3)
}
