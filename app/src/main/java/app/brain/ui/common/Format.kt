package app.brain.ui.common

import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.RecordEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))

fun displayTitle(record: RecordEntity): String {
    record.title?.takeIf { it.isNotBlank() }?.let { return it }
    val firstLine = record.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
    return if (firstLine.isEmpty()) "（无标题）" else firstLine.take(24)
}

fun dimensionLabel(dimension: String): String = when (dimension) {
    CategoryEntity.DIM_TYPE -> "内容类型"
    CategoryEntity.DIM_TOPIC -> "人生课题"
    CategoryEntity.DIM_URGENCY -> "紧急程度"
    CategoryEntity.DIM_IMPORTANCE -> "重要程度"
    CategoryEntity.DIM_EMOTION -> "情绪"
    CategoryEntity.DIM_TAG -> "通用标签"
    else -> dimension
}

fun statusLabel(status: String): String = when (status) {
    RecordEntity.STATUS_PENDING -> "待整理"
    RecordEntity.STATUS_PROCESSING -> "整理中"
    RecordEntity.STATUS_ORGANIZED -> "已整理"
    RecordEntity.STATUS_FAILED -> "整理失败"
    else -> status
}