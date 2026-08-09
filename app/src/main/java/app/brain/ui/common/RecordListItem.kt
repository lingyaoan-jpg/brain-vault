package app.brain.ui.common

import app.brain.data.db.dao.RecordCategoryWithCategory
import app.brain.data.db.entity.RecordEntity

data class RecordListItem(
    val record: RecordEntity,
    val categories: List<RecordCategoryWithCategory>,
)