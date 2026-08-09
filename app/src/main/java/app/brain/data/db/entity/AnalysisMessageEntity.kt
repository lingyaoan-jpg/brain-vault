package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 深度分析对话消息，独立于普通记录与评论存放。 */
@Entity(
    tableName = "analysis_messages",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("session_id")],
)
data class AnalysisMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val role: String,
    val content: String,
    @ColumnInfo(name = "citations_json") val citationsJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}