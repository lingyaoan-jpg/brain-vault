package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.brain.data.db.entity.AnalysisMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AnalysisMessageEntity)

    @Query("SELECT * FROM analysis_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun observeBySession(sessionId: String): Flow<List<AnalysisMessageEntity>>

    @Query("SELECT * FROM analysis_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getBySession(sessionId: String): List<AnalysisMessageEntity>

    @Query("DELETE FROM analysis_messages WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}