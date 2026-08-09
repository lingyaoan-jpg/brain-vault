package app.brain.di

import android.content.Context
import app.brain.data.ai.AiTransport
import app.brain.data.ai.DeepSeekTransport
import app.brain.data.db.BrainDatabase
import app.brain.data.db.dao.AiFeedbackDao
import app.brain.data.db.dao.AiJobDao
import app.brain.data.db.dao.AnalysisMessageDao
import app.brain.data.db.dao.AnalysisSessionDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.CategoryPreferenceDao
import app.brain.data.db.dao.CategorySuggestionDao
import app.brain.data.db.dao.CommentDao
import app.brain.data.db.dao.DraftDao
import app.brain.data.db.dao.EmbeddingDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.dao.RecordLinkDao
import app.brain.data.db.dao.TopicHintDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BrainDatabase =
        BrainDatabase.build(context)

    @Provides
    fun provideRecordDao(db: BrainDatabase): RecordDao = db.recordDao()

    @Provides
    fun provideDraftDao(db: BrainDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideCommentDao(db: BrainDatabase): CommentDao = db.commentDao()

    @Provides
    fun provideCategoryDao(db: BrainDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCategoryPreferenceDao(db: BrainDatabase): CategoryPreferenceDao = db.categoryPreferenceDao()

    @Provides
    fun provideCategorySuggestionDao(db: BrainDatabase): CategorySuggestionDao = db.categorySuggestionDao()

    @Provides
    fun provideRecordCategoryDao(db: BrainDatabase): RecordCategoryDao = db.recordCategoryDao()

    @Provides
    fun provideAiFeedbackDao(db: BrainDatabase): AiFeedbackDao = db.aiFeedbackDao()

    @Provides
    fun provideRecordLinkDao(db: BrainDatabase): RecordLinkDao = db.recordLinkDao()

    @Provides
    fun provideAiJobDao(db: BrainDatabase): AiJobDao = db.aiJobDao()

    @Provides
    fun provideEmbeddingDao(db: BrainDatabase): EmbeddingDao = db.embeddingDao()

    @Provides
    fun provideTopicHintDao(db: BrainDatabase): TopicHintDao = db.topicHintDao()

    @Provides
    fun provideAnalysisSessionDao(db: BrainDatabase): AnalysisSessionDao = db.analysisSessionDao()

    @Provides
    fun provideAnalysisMessageDao(db: BrainDatabase): AnalysisMessageDao = db.analysisMessageDao()

    @Provides
    @Singleton
    fun provideAiTransport(deepSeek: DeepSeekTransport): AiTransport = deepSeek
}