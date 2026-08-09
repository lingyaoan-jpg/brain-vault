package app.brain.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.brain.data.db.entity.CategoryEntity
import kotlin.math.absoluteValue

/**
 * 圆角标签。内容类型无彩色、统一中性样式；其他维度是辅助分类，每个一级分类有自己的浅色背景。
 */
@Composable
fun TagChip(text: String, dimension: String, categoryId: String, modifier: Modifier = Modifier) {
    val isType = dimension == CategoryEntity.DIM_TYPE
    val background = if (isType) {
        MaterialTheme.colorScheme.surface
    } else {
        dimensionBaseColor(dimension).copy(alpha = 0.16f)
    }
    val textColor = if (isType) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(color = background, shape = RoundedCornerShape(50))
            .then(
                if (isType) {
                    Modifier.border(
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(50),
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 一级分类的代表色（用于筛选菜单色点、维度标识）。 */
fun dimensionBaseColor(dimension: String): Color = when (dimension) {
    CategoryEntity.DIM_TYPE -> Color(0xFF7FA6D8)
    CategoryEntity.DIM_TOPIC -> Color(0xFF7FB89A)
    CategoryEntity.DIM_URGENCY -> Color(0xFFE8A867)
    CategoryEntity.DIM_IMPORTANCE -> Color(0xFFA78FD4)
    CategoryEntity.DIM_EMOTION -> Color(0xFFE08FA8)
    CategoryEntity.DIM_TAG -> Color(0xFF45AEB8)
    else -> extraBaseColors[dimension.hashCode().absoluteValue % extraBaseColors.size]
}

private val extraBaseColors = listOf(
    Color(0xFFD8A84E),
    Color(0xFFD98C8C),
    Color(0xFF8A9BB0),
    Color(0xFFC98A5E),
)
