package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** AI 重复主题提示：低打扰横幅，用户可忽略或跳转深度分析。 */
@Entity(tableName = "topic_hints", indices = [Index("status")])
data class TopicHintEntity(
    @PrimaryKey val id: String,
    val topic: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    val count: Int,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    val status: String = STATUS_PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DISMISSED = "dismissed"
        const val STATUS_OPENED = "opened"
    }
}