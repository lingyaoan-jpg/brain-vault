package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["dimension", "name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val dimension: String,
    val name: String,
    @ColumnInfo(name = "created_by") val createdBy: String = CREATED_BY_SEED,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
) {
    companion object {
        const val DIM_TYPE = "type"
        const val DIM_TOPIC = "topic"
        const val DIM_URGENCY = "urgency"
        const val DIM_IMPORTANCE = "importance"
        const val DIM_EMOTION = "emotion"
        const val DIM_TAG = "tag"

        const val CREATED_BY_SEED = "seed"
        const val CREATED_BY_AI = "ai"
        const val CREATED_BY_USER = "user"
    }
}