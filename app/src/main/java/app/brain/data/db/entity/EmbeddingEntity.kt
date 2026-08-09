package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class EmbeddingEntity(
    @PrimaryKey @ColumnInfo(name = "record_id") val recordId: String,
    val model: String,
    val vector: ByteArray,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)