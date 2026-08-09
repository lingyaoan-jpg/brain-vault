package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.brain.data.db.entity.CategoryPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(preference: CategoryPreferenceEntity)

    /** 命中同一规则时次数 +1 并刷新目标分类。 */
    @Query(
        """
        UPDATE category_preferences
        SET count = count + 1, to_category_id = :toCategoryId, updated_at = :updatedAt
        WHERE dimension = :dimension AND from_name = :fromName AND to_name = :toName
        """
    )
    suspend fun bump(dimension: String, fromName: String, toName: String, toCategoryId: String?, updatedAt: Long): Int

    @Query("SELECT * FROM category_preferences WHERE count >= :minCount ORDER BY count DESC, updated_at DESC")
    suspend fun getStrong(minCount: Int): List<CategoryPreferenceEntity>

    @Query("SELECT * FROM category_preferences ORDER BY count DESC, updated_at DESC")
    fun observeAll(): Flow<List<CategoryPreferenceEntity>>
}