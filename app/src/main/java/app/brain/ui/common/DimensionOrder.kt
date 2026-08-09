package app.brain.ui.common

import app.brain.data.db.entity.CategoryEntity

/** 筛选/编辑菜单里一级分类的固定展示顺序。 */
object DimensionOrder {
    val FILTER_ORDER = listOf(
        CategoryEntity.DIM_TOPIC,
        CategoryEntity.DIM_EMOTION,
        CategoryEntity.DIM_IMPORTANCE,
        CategoryEntity.DIM_TYPE,
        CategoryEntity.DIM_URGENCY,
    )
}
