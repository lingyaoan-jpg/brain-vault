package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.brain.data.db.entity.AiJobEntity

@Dao
interface AiJobDao {
    @Insert suspend fun insert(job: AiJobEntity)

    @Insert
    suspend fun insertAll(jobs: List<AiJobEntity>)

    @Update suspend fun update(job: AiJobEntity)

    @Query(
        """
        SELECT * FROM ai_jobs
        WHERE status = 'queued' AND (next_retry_at IS NULL OR next_retry_at <= :now)
        ORDER BY created_at ASC LIMIT :limit
        """
    )
    suspend fun nextDue(now: Long, limit: Int): List<AiJobEntity>

    @Query(
        """
        SELECT * FROM ai_jobs
        WHERE record_id = :recordId AND job_type = :jobType AND status IN ('queued', 'running')
        """
    )
    suspend fun findActive(recordId: String, jobType: String): AiJobEntity?

    @Query("DELETE FROM ai_jobs WHERE record_id = :recordId")
    suspend fun deleteForRecord(recordId: String)
}