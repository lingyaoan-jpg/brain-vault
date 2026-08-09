package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val id: String,
    val content: String,
    val title: String? = null,
    @ColumnInfo(name = "title_source") val titleSource: String = TITLE_SOURCE_AI,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val status: String = STATUS_PENDING,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "ai_attempts") val aiAttempts: Int = 0,
    @ColumnInfo(name = "ai_error") val aiError: String? = null,
    @ColumnInfo(name = "ai_processed_at") val aiProcessedAt: Long? = null,
) {
    companion object {
        const val TITLE_SOURCE_AI = "ai"
        const val TITLE_SOURCE_MANUAL = "manual"
        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_ORGANIZED = "organized"
        const val STATUS_FAILED = "failed"
    }
}