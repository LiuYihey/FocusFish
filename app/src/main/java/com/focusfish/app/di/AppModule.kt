package com.focusfish.app.di

import android.content.Context
import com.focusfish.app.ai.AiConfigStore
import com.focusfish.app.ai.WorldContextProvider
import com.focusfish.app.data.local.AppDatabase
import com.focusfish.app.data.local.dao.AffinityAchievementDao
import com.focusfish.app.data.local.dao.BlockedAppDao
import com.focusfish.app.data.local.dao.BookDao
import com.focusfish.app.data.local.dao.ChatDao
import com.focusfish.app.data.local.dao.FishCatalogDao
import com.focusfish.app.data.local.dao.FishMemoryDao
import com.focusfish.app.data.local.dao.FishStateDao
import com.focusfish.app.data.local.dao.FoodCatalogDao
import com.focusfish.app.data.local.dao.FoodInventoryDao
import com.focusfish.app.data.local.dao.FocusSessionDao
import com.focusfish.app.data.local.dao.PersonaDao
import com.focusfish.app.data.local.dao.QuizHistoryDao
import com.focusfish.app.data.local.dao.QuizQuestionDao
import com.focusfish.app.data.local.dao.QuoteDao
import com.focusfish.app.data.local.dao.ReflectionAnswerDao
import com.focusfish.app.data.local.dao.ReflectionQuestionDao
import com.focusfish.app.data.local.dao.UserFishDao
import com.focusfish.app.data.repository.AppRepositoryImpl
import com.focusfish.app.data.repository.FishRepository
import com.focusfish.app.data.repository.FoodRepository
import com.focusfish.app.data.repository.QuizRepositoryImpl
import com.focusfish.app.data.repository.ReflectionRepository
import com.focusfish.app.data.repository.StatsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 * 提供数据库实例、各表 DAO 和各 Repository 的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供 AppDatabase 单例
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    // ===== 原有 DAO =====

    @Provides
    fun provideBlockedAppDao(db: AppDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    fun provideQuizQuestionDao(db: AppDatabase): QuizQuestionDao = db.quizQuestionDao()

    @Provides
    fun provideQuizHistoryDao(db: AppDatabase): QuizHistoryDao = db.quizHistoryDao()

    @Provides
    fun provideFocusSessionDao(db: AppDatabase): FocusSessionDao = db.focusSessionDao()

    // ===== FocusFish 新增 DAO =====

    @Provides
    fun provideFishCatalogDao(db: AppDatabase): FishCatalogDao = db.fishCatalogDao()

    @Provides
    fun provideUserFishDao(db: AppDatabase): UserFishDao = db.userFishDao()

    @Provides
    fun provideFoodCatalogDao(db: AppDatabase): FoodCatalogDao = db.foodCatalogDao()

    @Provides
    fun provideFoodInventoryDao(db: AppDatabase): FoodInventoryDao = db.foodInventoryDao()

    @Provides
    fun provideReflectionQuestionDao(db: AppDatabase): ReflectionQuestionDao =
        db.reflectionQuestionDao()

    @Provides
    fun provideReflectionAnswerDao(db: AppDatabase): ReflectionAnswerDao =
        db.reflectionAnswerDao()

    @Provides
    fun provideAffinityAchievementDao(db: AppDatabase): AffinityAchievementDao =
        db.affinityAchievementDao()

    @Provides
    fun provideQuoteDao(db: AppDatabase): QuoteDao = db.quoteDao()

    // ===== AI 对话相关 DAO =====

    @Provides
    fun providePersonaDao(db: AppDatabase): PersonaDao = db.personaDao()

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideFishStateDao(db: AppDatabase): FishStateDao = db.fishStateDao()

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun provideFishMemoryDao(db: AppDatabase): FishMemoryDao = db.fishMemoryDao()

    // ===== AI 引擎组件 =====

    /**
     * AI 配置存储：用户自填的 API 地址 / 密钥 / 模型，长期保存在本机
     */
    @Provides
    @Singleton
    fun provideAiConfigStore(@ApplicationContext context: Context): AiConfigStore =
        AiConfigStore(context)

    /**
     * 世界感知提供器：把应用使用时长、专注时长、状态汇总给大肥鱼
     */
    @Provides
    @Singleton
    fun provideWorldContextProvider(@ApplicationContext context: Context): WorldContextProvider =
        WorldContextProvider(context)

    // ===== 原有 Repository =====

    @Provides
    @Singleton
    fun provideAppRepository(dao: BlockedAppDao): AppRepositoryImpl = AppRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideQuizRepository(
        db: AppDatabase,
        questionDao: QuizQuestionDao,
        historyDao: QuizHistoryDao
    ): QuizRepositoryImpl = QuizRepositoryImpl(db, questionDao, historyDao)

    @Provides
    @Singleton
    fun provideStatsRepository(dao: FocusSessionDao): StatsRepositoryImpl = StatsRepositoryImpl(dao)

    // ===== FocusFish 新增 Repository =====

    /**
     * 提供 FishRepository，注入喂食频率限制用的 SharedPreferences（独立文件名，避免与设置项混淆）
     */
    @Provides
    @Singleton
    fun provideFishRepository(
        @ApplicationContext context: Context,
        db: AppDatabase,
        fishCatalogDao: FishCatalogDao,
        userFishDao: UserFishDao,
        achievementDao: AffinityAchievementDao,
        foodInventoryDao: FoodInventoryDao
    ): FishRepository = FishRepository(
        db = db,
        fishCatalogDao = fishCatalogDao,
        userFishDao = userFishDao,
        achievementDao = achievementDao,
        foodInventoryDao = foodInventoryDao,
        feedLimitPrefs = context.getSharedPreferences("focus_fish_feed_limit", Context.MODE_PRIVATE)
    )

    @Provides
    @Singleton
    fun provideFoodRepository(
        foodCatalogDao: FoodCatalogDao,
        foodInventoryDao: FoodInventoryDao
    ): FoodRepository = FoodRepository(foodCatalogDao, foodInventoryDao)

    @Provides
    @Singleton
    fun provideReflectionRepository(
        questionDao: ReflectionQuestionDao,
        answerDao: ReflectionAnswerDao
    ): ReflectionRepository = ReflectionRepository(questionDao, answerDao)
}
