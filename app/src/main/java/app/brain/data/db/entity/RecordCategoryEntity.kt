package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "record_categories",
    primaryKeys = ["record_id", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class RecordCategoryEntity(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val source: String = SOURCE_AI,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val SOURCE_AI = "ai"
        const val SOURCE_USER = "user"
    }
}