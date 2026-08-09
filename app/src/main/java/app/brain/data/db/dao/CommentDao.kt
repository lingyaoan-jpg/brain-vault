package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.brain.data.db.entity.CommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {
    @Insert suspend fun insert(comment: CommentEntity)

    @Query("SELECT * FROM comments WHERE record_id = :recordId ORDER BY created_at ASC")
    fun observeByRecord(recordId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE record_id = :recordId ORDER BY created_at ASC")
    suspend fun getByRecord(recordId: String): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE record_id IN (:recordIds) ORDER BY created_at ASC")
    suspend fun getByRecords(recordIds: List<String>): List<CommentEntity>
}