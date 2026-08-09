package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
/** 历史分类调整建议：只生成、不自动应用，由用户接受或忽略。 */
@Entity(
    tableName = "category_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("record_id"), Index("status")],
)
data class CategorySuggestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "from_category_id") val fromCategoryId: String,
    @ColumnInfo(name = "to_category_id") val toCategoryId: String,
    val reason: String,
    val status: String = STATUS_PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_IGNORED = "ignored"
    }
}
