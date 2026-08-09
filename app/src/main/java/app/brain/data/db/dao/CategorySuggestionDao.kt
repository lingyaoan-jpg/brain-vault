package app.brain.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.brain.data.db.entity.CategorySuggestionEntity
import kotlinx.coroutines.flow.Flow

data class SuggestionWithRecord(
    @ColumnInfo(name = "suggestion_id") val suggestionId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "from_category_id") val fromCategoryId: String,
    @ColumnInfo(name = "to_category_id") val toCategoryId: String,
    val reason: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "record_title") val recordTitle: String?,
    @ColumnInfo(name = "record_content") val recordContent: String,
    @ColumnInfo(name = "from_name") val fromName: String,
    @ColumnInfo(name = "to_name") val toName: String,
    @ColumnInfo(name = "dimension") val dimension: String,
)

@Dao
interface CategorySuggestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CategorySuggestionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CategorySuggestionEntity)

    @Query("UPDATE category_suggestions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE category_suggestions SET status = :status WHERE status = 'pending'")
    suspend fun setAllStatus(status: String)

    @Query(
        """
        SELECT s.id AS suggestion_id, s.record_id, s.from_category_id, s.to_category_id, s.reason, s.status, s.created_at,
               r.title AS record_title, r.content AS record_content,
               cf.name AS from_name, ct.name AS to_name, ct.dimension AS dimension
        FROM category_suggestions s
        JOIN records r ON r.id = s.record_id
        JOIN categories cf ON cf.id = s.from_category_id
        JOIN categories ct ON ct.id = s.to_category_id
        WHERE s.status = :status
        ORDER BY s.created_at DESC
        """
    )
    fun observeByStatus(status: String): Flow<List<SuggestionWithRecord>>

    @Query("SELECT COUNT(*) FROM category_suggestions WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM category_suggestions WHERE status = 'pending' ORDER BY created_at DESC")
    suspend fun getPendingSnapshot(): List<CategorySuggestionEntity>

    @Query("SELECT COUNT(*) FROM category_suggestions WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM category_suggestions WHERE record_id = :recordId AND to_category_id = :toCategoryId AND status != 'ignored'")
    suspend fun existsOpen(recordId: String, toCategoryId: String): Int
}
