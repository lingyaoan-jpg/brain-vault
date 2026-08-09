package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_feedback",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("record_id")],
)
data class AiFeedbackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    val action: String,
    @ColumnInfo(name = "old_value") val oldValue: String? = null,
    @ColumnInfo(name = "new_value") val newValue: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val ACTION_ADD_CATEGORY = "add_category"
        const val ACTION_REMOVE_CATEGORY = "remove_category"
        const val ACTION_RETITLE = "retitle"
        const val ACTION_CHANGE_CATEGORY = "change_category"
    }
}