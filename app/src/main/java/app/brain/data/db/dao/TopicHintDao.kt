package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.brain.data.db.entity.TopicHintEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicHintDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(hint: TopicHintEntity)

    @Query("SELECT * FROM topic_hints WHERE status = 'pending' ORDER BY created_at DESC LIMIT :limit")
    fun observePending(limit: Int): Flow<List<TopicHintEntity>>

    @Query("SELECT * FROM topic_hints WHERE status = 'pending' ORDER BY created_at DESC LIMIT :limit")
    suspend fun getPending(limit: Int): List<TopicHintEntity>

    @Query("SELECT COUNT(*) FROM topic_hints WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM topic_hints WHERE topic = :topic AND status = 'pending')")
    suspend fun existsPendingForTopic(topic: String): Boolean

    @Query("UPDATE topic_hints SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)
}