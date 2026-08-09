package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.brain.data.db.entity.AnalysisSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AnalysisSessionEntity)

    @Update suspend fun update(session: AnalysisSessionEntity)

    @Query("SELECT * FROM analysis_sessions ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<AnalysisSessionEntity>>

    @Query("SELECT * FROM analysis_sessions WHERE id = :id")
    fun observeById(id: String): Flow<AnalysisSessionEntity?>

    @Query("SELECT * FROM analysis_sessions WHERE id = :id")
    suspend fun getById(id: String): AnalysisSessionEntity?

    @Query("DELETE FROM analysis_sessions WHERE id = :id")
    suspend fun delete(id: String)

    /** 记录详情页展示"相关分析"：scope_json 里包含该记录 id 的会话。 */
    @Query("SELECT * FROM analysis_sessions WHERE scope_json LIKE '%' || :recordId || '%' ORDER BY updated_at DESC")
    suspend fun findByScopeContaining(recordId: String): List<AnalysisSessionEntity>
}