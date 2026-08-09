package app.brain.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 深度分析会话：保存标题与当时授权的分析范围（scope_json）。 */
@Entity(tableName = "analysis_sessions")
data class AnalysisSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "scope_json") val scopeJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)