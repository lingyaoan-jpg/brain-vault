package app.brain.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.brain.data.db.entity.RecordCategoryEntity
import kotlinx.coroutines.flow.Flow

data class RecordCategoryWithCategory(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val source: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val dimension: String,
    val name: String,
)

data class TopicCount(
    @ColumnInfo(name = "category_id") val categoryId: String,
    val topic: String,
    val cnt: Int,
)

@Dao
interface RecordCategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rc: RecordCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rcs: List<RecordCategoryEntity>)

    @Query("DELETE FROM record_categories WHERE record_id = :recordId AND category_id = :categoryId")
    suspend fun remove(recordId: String, categoryId: String)

    @Query("DELETE FROM record_categories WHERE record_id = :recordId")
    suspend fun removeAllForRecord(recordId: String)

    /** 只清除 AI 生成的分类，保留用户手动修改的。 */
    @Query("DELETE FROM record_categories WHERE record_id = :recordId AND source != 'user'")
    suspend fun removeAllAiForRecord(recordId: String)

    /** 批量清除所选记录在某一个维度上的全部分类关联（用于"覆盖"该维度）。 */
    @Query(
        "DELETE FROM record_categories WHERE record_id IN (:recordIds) AND category_id IN (SELECT id FROM categories WHERE dimension = :dimension)"
    )
    suspend fun removeDimensionForRecords(recordIds: List<String>, dimension: String)

    /** 近一段时间内，各人生课题下的记录数（用于重复主题提示）。 */
    @Query(
        """
        SELECT c.id AS category_id, c.name AS topic, COUNT(DISTINCT rc.record_id) AS cnt
        FROM record_categories rc
        JOIN categories c ON c.id = rc.category_id
        JOIN records r ON r.id = rc.record_id
        WHERE c.dimension = 'topic' AND c.is_active = 1 AND r.deleted_at IS NULL AND r.created_at >= :windowStart
        GROUP BY c.id, c.name
        HAVING cnt >= :minCount
        """
    )
    suspend fun topicCountsSince(windowStart: Long, minCount: Int): List<TopicCount>

    @Query("SELECT * FROM record_categories WHERE category_id = :categoryId")
    suspend fun linksByCategory(categoryId: String): List<RecordCategoryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM record_categories WHERE record_id = :recordId AND category_id = :categoryId)")
    suspend fun exists(recordId: String, categoryId: String): Boolean

    @Query("UPDATE record_categories SET category_id = :newCategoryId WHERE record_id = :recordId AND category_id = :oldCategoryId")
    suspend fun repoint(recordId: String, oldCategoryId: String, newCategoryId: String)

    @Query(
        """
        SELECT rc.record_id, rc.category_id, rc.source, rc.created_at, c.dimension, c.name
        FROM record_categories rc
        JOIN categories c ON c.id = rc.category_id
        WHERE rc.record_id = :recordId
        ORDER BY c.dimension, c.sort_order
        """
    )
    fun observeByRecord(recordId: String): Flow<List<RecordCategoryWithCategory>>

    @Query(
        """
        SELECT rc.record_id, rc.category_id, rc.source, rc.created_at, c.dimension, c.name
        FROM record_categories rc
        JOIN categories c ON c.id = rc.category_id
        WHERE rc.record_id = :recordId
        """
    )
    suspend fun getByRecord(recordId: String): List<RecordCategoryWithCategory>

    @Query(
        """
        SELECT rc.record_id, rc.category_id, rc.source, rc.created_at, c.dimension, c.name
        FROM record_categories rc
        JOIN categories c ON c.id = rc.category_id
        ORDER BY rc.record_id, c.dimension, c.sort_order
        """
    )
    fun observeAllWithCategories(): Flow<List<RecordCategoryWithCategory>>

    @Query(
        """
        SELECT rc.record_id, rc.category_id, rc.source, rc.created_at, c.dimension, c.name
        FROM record_categories rc
        JOIN categories c ON c.id = rc.category_id
        JOIN records r ON r.id = rc.record_id
        WHERE r.deleted_at IS NULL
        ORDER BY rc.record_id, c.dimension, c.sort_order
        """
    )
    fun observeAllWithCategoriesActive(): Flow<List<RecordCategoryWithCategory>>

    @Query(
        """
        SELECT rc.record_id, rc.category_id, rc.source, rc.created_at, c.dimension, c.name
        FROM record_categories rc
        JOIN categories c ON c.id = rc.category_id
        ORDER BY rc.record_id, c.dimension, c.sort_order
        """
    )
    suspend fun getAllWithCategories(): List<RecordCategoryWithCategory>
}