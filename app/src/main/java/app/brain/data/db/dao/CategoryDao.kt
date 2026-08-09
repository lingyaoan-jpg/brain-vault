package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.brain.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE is_active = 1 ORDER BY sort_order, name")
    fun observeAllActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_active = 1 ORDER BY sort_order, name")
    suspend fun getAllActive(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE dimension = :dimension AND is_active = 1 ORDER BY sort_order, name")
    fun observeByDimension(dimension: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE dimension = :dimension AND name = :name AND is_active = 1 LIMIT 1")
    suspend fun getByName(dimension: String, name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM categories WHERE dimension = :dimension AND created_by = :createdBy")
    suspend fun deleteByDimensionAndCreator(dimension: String, createdBy: String)
}