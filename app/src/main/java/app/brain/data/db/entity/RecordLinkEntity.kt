package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "record_links",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("to_record_id")],
)
data class RecordLinkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "from_record_id") val fromRecordId: String,
    @ColumnInfo(name = "to_record_id") val toRecordId: String,
    val reason: String? = null,
    val source: String = SOURCE_AI,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val SOURCE_AI = "ai"
        const val SOURCE_USER = "user"
    }
}