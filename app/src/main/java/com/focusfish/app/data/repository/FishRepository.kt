package com.focusfish.app.data.repository

import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.focusfish.app.data.local.dao.AffinityAchievementDao
import com.focusfish.app.data.local.dao.FishCatalogDao
import com.focusfish.app.data.local.dao.FoodInventoryDao
import com.focusfish.app.data.local.dao.UserFishDao
import com.focusfish.app.data.local.entity.AffinityAchievementEntity
import com.focusfish.app.data.local.entity.FishCatalogEntity
import com.focusfish.app.data.local.entity.UserFishEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 大肥鱼仓库
 * 封装大肥鱼形态目录、用户大肥鱼、好感度成就的数据访问
 *
 * 喂食频率限制：每个自然日（0:00-24:00）最多 [MAX_FEED_PER_DAY] 次，
 * 超出后 [canFeedNow] 返回 false，由 UI 提示"大肥鱼已经吃饱啦，明天再喂吧~"。
 * 跨天自动重置（以本地时区 0 点为界）。
 *
 * 按大肥鱼独立计数：每只大肥鱼有独立的每日喂食次数上限，切换大肥鱼时互不影响。
 */
class FishRepository(
    private val db: RoomDatabase,
    private val fishCatalogDao: FishCatalogDao,
    private val userFishDao: UserFishDao,
    private val achievementDao: AffinityAchievementDao,
    private val foodInventoryDao: FoodInventoryDao,
    private val feedLimitPrefs: SharedPreferences
) {

    companion object {
        /** 每个自然日最多喂食次数 */
        private const val MAX_FEED_PER_DAY = 5
        /** SharedPreferences key 前缀：存储"当天喂食次数"（按 fishId 命名空间分隔） */
        private const val KEY_FEED_COUNT_TODAY_PREFIX = "feed_count_today_"
        /** SharedPreferences key 前缀：存储"上次喂食所属的自然日（yyyyMMdd）" */
        private const val KEY_FEED_DAY_KEY_PREFIX = "feed_day_key_"
    }

    /**
     * 是否还能继续喂食（今日未超过 [MAX_FEED_PER_DAY] 次）
     * 按大肥鱼独立计数：不同大肥鱼有独立的每日喂食额度。
     *
     * @param fishId 当前大肥鱼 ID，null 时返回 false
     */
    fun canFeedNow(fishId: Int?): Boolean {
        if (fishId == null) return false
        return getFeedCountToday(fishId) < MAX_FEED_PER_DAY
    }

    /**
     * 今日剩余可喂食次数（0 表示已达上限）
     * 按大肥鱼独立计数。
     *
     * @param fishId 当前大肥鱼 ID，null 时返回 0
     */
    fun getRemainingFeedCountToday(fishId: Int?): Int {
        if (fishId == null) return 0
        return (MAX_FEED_PER_DAY - getFeedCountToday(fishId)).coerceAtLeast(0)
    }

    /**
     * 获取今日已喂食次数，跨天时自动归零
     * 按大肥鱼独立计数：每只大肥鱼的次数存储在独立的 SharedPreferences key 中。
     *
     * @param fishId 当前大肥鱼 ID
     */
    private fun getFeedCountToday(fishId: Int): Int {
        val todayKey = todayKey()
        val dayKeyKey = KEY_FEED_DAY_KEY_PREFIX + fishId
        val countKey = KEY_FEED_COUNT_TODAY_PREFIX + fishId
        val savedDayKey = feedLimitPrefs.getString(dayKeyKey, null)
        // 跨天：次数归零，更新日期 key
        if (savedDayKey != todayKey) {
            feedLimitPrefs.edit()
                .putString(dayKeyKey, todayKey)
                .putInt(countKey, 0)
                .apply()
            return 0
        }
        return feedLimitPrefs.getInt(countKey, 0)
    }

    /**
     * 记录一次喂食，今日计数 +1（跨天则从 1 开始）
     * 按大肥鱼独立计数。
     *
     * @param fishId 当前大肥鱼 ID
     */
    private fun recordFeedTimestamp(fishId: Int) {
        val todayKey = todayKey()
        val dayKeyKey = KEY_FEED_DAY_KEY_PREFIX + fishId
        val countKey = KEY_FEED_COUNT_TODAY_PREFIX + fishId
        val savedDayKey = feedLimitPrefs.getString(dayKeyKey, null)
        val newCount = if (savedDayKey == todayKey) {
            feedLimitPrefs.getInt(countKey, 0) + 1
        } else {
            1
        }
        feedLimitPrefs.edit()
            .putString(dayKeyKey, todayKey)
            .putInt(countKey, newCount)
            .apply()
    }

    /** 今日日期 key（yyyyMMdd，本地时区） */
    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return String.format(
            "%04d%02d%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** 获取所有大肥鱼形态 */
    suspend fun getAllBreeds(): List<FishCatalogEntity> = fishCatalogDao.getAllBreeds()

    /** 观察所有大肥鱼形态（Flow），种子数据插入后自动通知 UI */
    fun observeAllBreeds(): Flow<List<FishCatalogEntity>> = fishCatalogDao.observeAllBreeds()

    /** 根据品种 ID 获取品种 */
    suspend fun getBreedById(breedId: String): FishCatalogEntity? = fishCatalogDao.getById(breedId)

    /** 观察用户大肥鱼（Flow） */
    fun observeUserFish(): Flow<UserFishEntity?> = userFishDao.observeUserFish()

    /** 获取用户大肥鱼 */
    suspend fun getUserFish(): UserFishEntity? = userFishDao.getUserFish()

    /** 用户是否已创建过任何大肥鱼 */
    suspend fun hasFish(): Boolean = userFishDao.hasAnyFish()

    /** 观察所有已创建的大肥鱼（用于切换大肥鱼列表） */
    fun observeAllFish(): Flow<List<UserFishEntity>> = userFishDao.observeAllFish()

    /** 获取所有已创建的大肥鱼 */
    suspend fun getAllFish(): List<UserFishEntity> = userFishDao.getAllFish()

    /**
     * 创建新大肥鱼（首次选择或新增大肥鱼）
     *
     * 多实例化设计：每只大肥鱼是独立记录，有独立的 affinityLevel/totalFeedCount。
     * 创建时自动将旧大肥鱼（若有）设为 inactive，新大肥鱼设为 active。
     *
     * @param breedId 品种 ID
     * @param name 大肥鱼名字
     */
    suspend fun createFish(breedId: String, name: String) {
        db.withTransaction {
            userFishDao.deactivateAll()
            userFishDao.insert(
                UserFishEntity(
                    breedId = breedId,
                    name = name,
                    isActive = true
                )
            )
        }
    }

    /**
     * 切换到指定大肥鱼（实例化切换，恢复该大肥鱼的独立数据）
     *
     * 每只大肥鱼有独立的 name/affinityLevel/totalFeedCount，切换即恢复该大肥鱼的完整状态。
     * 类似"切换账号"：不同大肥鱼的养成进度独立保留。
     *
     * @param fishId 目标大肥鱼 ID
     */
    suspend fun switchToFish(fishId: Int) {
        db.withTransaction {
            userFishDao.deactivateAll()
            userFishDao.activateFish(fishId)
        }
    }

    /** 删除指定大肥鱼（不可删除当前激活的大肥鱼） */
    suspend fun deleteFish(fishId: Int) {
        userFishDao.deleteFishById(fishId)
    }

    /**
     * 投喂大肥鱼：增加投喂次数和好感度，并检查成就解锁
     *
     * 修复 B3：用 @Transaction 包裹多步写操作，确保 totalFeedCount+1 与成就解锁原子完成。
     * 原代码无事务，进程在 incrementFeed 后被杀会导致 totalFeedCount 已+1 但成就未解锁，
     * 下次投喂才解锁本应本次解锁的成就，进度错位。
     *
     * 投喂频率限制：事务成功后记录时间戳，[canFeedNow] 据此判断是否已超 24h/5 次上限。
     * 调用方应在调用前先调用 [canFeedNow]，超限时给出"大肥鱼已经吃饱啦"提示。
     *
     * @param affinityBonus 该食物提供的好感度加成
     * @param fishId 当前大肥鱼 ID，用于按大肥鱼独立计数每日喂食次数
     * @return 新解锁的成就列表（可能为空）
     */
    suspend fun feedFish(
        affinityBonus: Int,
        fishId: Int?
    ): List<AffinityAchievementEntity> {
        if (fishId == null) return emptyList()
        val newlyUnlocked = db.withTransaction {
            userFishDao.incrementFeed(affinityBonus)
            val cat = userFishDao.getUserFish() ?: return@withTransaction emptyList()
            // 检查是否有新成就可解锁
            val lockedAchievements = achievementDao.getLockedAchievements()
            val newlyUnlocked = mutableListOf<AffinityAchievementEntity>()
            val now = System.currentTimeMillis()
            for (achievement in lockedAchievements) {
                if (cat.totalFeedCount >= achievement.milestone) {
                    achievementDao.unlock(achievement.milestone, now)
                    newlyUnlocked.add(achievement.copy(unlockedAt = now))
                }
            }
            newlyUnlocked
        }
        // 事务成功后才记录喂食时间戳，失败回滚则不占用每日额度
        recordFeedTimestamp(fishId)
        return newlyUnlocked
    }

    /**
     * 投喂大肥鱼（含消耗食物）：单事务包裹"消耗食物 + 增加好感度 + 解锁成就"
     *
     * 修复迭代5 Bug #3：原 FishViewModel 分两步调用
     * foodRepository.consumeOne(foodId) + fishRepository.feedFish(affinityBonus)，
     * 两个独立写操作无外层事务。若 consumeOne 成功后 feedFish 抛异常，食物被消耗但
     * totalFeedCount/affinityLevel 未增加，成就未解锁，数据不一致。
     *
     * 此方法将三步合并为一个 Room 事务，任一步失败整体回滚。
     *
     * 投喂频率限制：事务成功后记录时间戳，[canFeedNow] 据此判断是否已超 24h/5 次上限。
     * 调用方应在调用前先调用 [canFeedNow]，超限时给出"大肥鱼已经吃饱啦"提示。
     *
     * @param foodId 要消耗的食物 ID
     * @param affinityBonus 该食物提供的好感度加成
     * @param fishId 当前大肥鱼 ID，用于按大肥鱼独立计数每日喂食次数
     * @return 新解锁的成就列表（可能为空）
     */
    suspend fun feedFishWithFood(
        foodId: String,
        affinityBonus: Int,
        fishId: Int?
    ): List<AffinityAchievementEntity> {
        if (fishId == null) return emptyList()
        val newlyUnlocked = db.withTransaction {
            foodInventoryDao.consumeOne(foodId, System.currentTimeMillis())
            userFishDao.incrementFeed(affinityBonus)
            val cat = userFishDao.getUserFish() ?: return@withTransaction emptyList()
            val lockedAchievements = achievementDao.getLockedAchievements()
            val newlyUnlocked = mutableListOf<AffinityAchievementEntity>()
            val now = System.currentTimeMillis()
            for (achievement in lockedAchievements) {
                if (cat.totalFeedCount >= achievement.milestone) {
                    achievementDao.unlock(achievement.milestone, now)
                    newlyUnlocked.add(achievement.copy(unlockedAt = now))
                }
            }
            newlyUnlocked
        }
        // 事务成功后才记录喂食时间戳，失败回滚则不占用每日额度
        recordFeedTimestamp(fishId)
        return newlyUnlocked
    }

    /** 观察所有成就 */
    fun observeAchievements(): Flow<List<AffinityAchievementEntity>> =
        achievementDao.observeAllAchievements()

    /** 获取所有成就 */
    suspend fun getAllAchievements(): List<AffinityAchievementEntity> =
        achievementDao.getAllAchievements()

    /**
     * 检查指定里程碑的成就是否已解锁
     * 用于"解锁新伙伴"前置条件判断：只有解锁"初识之友"(milestone=10) 后才能添加第二个伙伴。
     *
     * @param milestone 成就里程碑值
     * @return true 表示该成就已解锁（unlockedAt 不为 null）
     */
    suspend fun isAchievementUnlocked(milestone: Int): Boolean =
        achievementDao.getUnlockedAt(milestone) != null
}
