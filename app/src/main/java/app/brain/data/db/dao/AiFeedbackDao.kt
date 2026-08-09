package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.brain.data.db.entity.AiFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiFeedbackDao {
    @Insert suspend fun insert(feedback: AiFeedbackEntity)

    @Query("SELECT * FROM ai_feedback ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AiFeedbackEntity>>
}