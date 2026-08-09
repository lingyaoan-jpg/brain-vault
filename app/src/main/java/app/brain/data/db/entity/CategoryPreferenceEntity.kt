package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户分类偏好信号：AI 曾把记录归为 fromName，用户改成了 toName。
 * 规则只影响未来的 AI 判断（count>=2 时视为有效偏好），不自动改历史。
 */
@Entity(
    tableName = "category_preferences",
    indices = [Index(value = ["dimension", "from_name", "to_name"], unique = true)],
)
data class CategoryPreferenceEntity(
    @PrimaryKey val id: String,
    val dimension: String,
    @ColumnInfo(name = "from_name") val fromName: String,
    @ColumnInfo(name = "to_name") val toName: String,
    @ColumnInfo(name = "to_category_id") val toCategoryId: String? = null,
    val count: Int = 1,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
