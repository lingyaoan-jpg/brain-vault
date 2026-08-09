package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.brain.data.db.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert suspend fun insert(record: RecordEntity)

    @Update suspend fun update(record: RecordEntity)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: String): RecordEntity?

    @Query("SELECT * FROM records WHERE id = :id")
    fun observeById(id: String): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeActive(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE deleted_at IS NULL ORDER BY is_pinned DESC, created_at DESC")
    fun observeActiveOrdered(): Flow<List<RecordEntity>>

    @Query(
        """
        SELECT r.* FROM records r
        JOIN record_categories rc ON rc.record_id = r.id
        WHERE rc.category_id = :categoryId AND r.deleted_at IS NULL
        ORDER BY r.is_pinned DESC, r.created_at DESC
        """
    )
    fun observeActiveByCategory(categoryId: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE deleted_at IS NULL AND is_favorite = 1 ORDER BY is_pinned DESC, created_at DESC")
    fun observeFavorites(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE deleted_at IS NULL AND created_at BETWEEN :from AND :to ORDER BY created_at DESC")
    suspend fun getActiveBetween(from: Long, to: Long): List<RecordEntity>

    @Query("SELECT * FROM records WHERE deleted_at IS NULL AND id IN (:ids)")
    suspend fun getActiveByIds(ids: List<String>): List<RecordEntity>

    @Query("UPDATE records SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE records SET is_pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("SELECT COUNT(*) FROM records WHERE deleted_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeTrashed(): Flow<List<RecordEntity>>

    /** 等待 AI 整理（或整理中）且未被删除的记录。 */
    @Query(
        "SELECT * FROM records WHERE deleted_at IS NULL AND status IN ('pending', 'processing')"
    )
    suspend fun getUntreated(): List<RecordEntity>

    @Query("UPDATE records SET deleted_at = :now WHERE id = :id")
    suspend fun trash(id: String, now: Long)

    @Query("UPDATE records SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deletePermanently(id: String)

    /** 批量修改标题：视为人工标题，后续 AI 不再覆盖。 */
    @Query("UPDATE records SET title = :title, title_source = 'manual', updated_at = :now WHERE id IN (:ids) AND deleted_at IS NULL")
    suspend fun batchSetTitle(ids: List<String>, title: String, now: Long)

    /** 批量修改首次记录时间。 */
    @Query("UPDATE records SET created_at = :time, updated_at = :now WHERE id IN (:ids) AND deleted_at IS NULL")
    suspend fun batchSetTime(ids: List<String>, time: Long, now: Long)

    /**
     * instr 精确子串搜索：FTS 短词（1-2 字符）无法匹配时兜底。
     * 命中范围：原文、标题、评论、分类名。
     */
    @Query(
        """
        SELECT r.* FROM records r
        WHERE r.deleted_at IS NULL AND (
            instr(r.content, :query) > 0
            OR instr(COALESCE(r.title, ''), :query) > 0
            OR EXISTS(
                SELECT 1 FROM comments cm
                WHERE cm.record_id = r.id AND instr(cm.content, :query) > 0
            )
            OR EXISTS(
                SELECT 1 FROM record_categories rc
                JOIN categories c ON c.id = rc.category_id
                WHERE rc.record_id = r.id AND instr(c.name, :query) > 0
            )
        )
        ORDER BY r.is_pinned DESC, r.created_at DESC
        """
    )
    fun searchSubstring(query: String): Flow<List<RecordEntity>>
}