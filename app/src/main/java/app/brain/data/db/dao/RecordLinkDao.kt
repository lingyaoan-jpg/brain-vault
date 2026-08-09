package app.brain.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.brain.data.db.entity.RecordLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordLinkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<RecordLinkEntity>)

    @Query("SELECT * FROM record_links WHERE from_record_id = :recordId OR to_record_id = :recordId")
    fun observeForRecord(recordId: String): Flow<List<RecordLinkEntity>>

    @Query("DELETE FROM record_links WHERE from_record_id = :recordId OR to_record_id = :recordId")
    suspend fun deleteForRecord(recordId: String)
}