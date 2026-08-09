package app.brain.data.db

import app.brain.data.db.entity.CategoryEntity

object SeedCategories {
    data class Seed(val dimension: String, val name: String, val sortOrder: Int)

    private val types = listOf("灵感想法", "分析观点", "感受", "事件记录", "文章", "待处理事项", "碎碎念")
    private val topics = listOf("亲密关系", "自我认知", "自我成长", "原生家庭")
    private val urgencies = listOf("立即", "近期", "稍后", "无时限")
    private val importances = listOf("核心", "重要", "一般", "随想")
    private val emotions = listOf("开心", "焦虑", "愤怒", "委屈", "疲惫", "平静", "矛盾")

    val all: List<Seed> = buildList {
        types.forEachIndexed { i, n -> add(Seed(CategoryEntity.DIM_TYPE, n, i)) }
        topics.forEachIndexed { i, n -> add(Seed(CategoryEntity.DIM_TOPIC, n, i)) }
        urgencies.forEachIndexed { i, n -> add(Seed(CategoryEntity.DIM_URGENCY, n, i)) }
        importances.forEachIndexed { i, n -> add(Seed(CategoryEntity.DIM_IMPORTANCE, n, i)) }
        emotions.forEachIndexed { i, n -> add(Seed(CategoryEntity.DIM_EMOTION, n, i)) }
    }

    fun seedId(dimension: String, index: Int): String = "seed_${dimension}_$index"
}
