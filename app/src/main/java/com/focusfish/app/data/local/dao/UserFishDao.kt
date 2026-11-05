package com.focusfish.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusfish.app.data.local.entity.UserFishEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户大肥鱼 DAO
 *
 * 支持多只大肥鱼实例化：每只大肥鱼独立存储，通过 [isActive] 标记当前激活的大肥鱼。
 * 切换鱼 = 旧大肥鱼 isActive=false + 新大肥鱼 isActive=true（事务由 Repository 层保证）。
 */
@Dao
interface UserFishDao {

    /** 观察当前激活的大肥鱼（全局唯一），UI 监听此 Flow 自动响应切换 */
    @Query("SELECT * FROM user_fish WHERE isActive = 1 LIMIT 1")
    fun observeUserFish(): Flow<UserFishEntity?>

    /** 获取当前激活的大肥鱼（单次查询） */
    @Query("SELECT * FROM user_fish WHERE isActive = 1 LIMIT 1")
    suspend fun getUserFish(): UserFishEntity?

    /** 观察所有已创建的大肥鱼（按创建时间升序），用于切换大肥鱼列表展示 */
    @Query("SELECT * FROM user_fish ORDER BY createdAt ASC")
    fun observeAllFish(): Flow<List<UserFishEntity>>

    /** 获取所有已创建的大肥鱼（单次查询） */
    @Query("SELECT * FROM user_fish ORDER BY createdAt ASC")
    suspend fun getAllFish(): List<UserFishEntity>

    /** 根据 ID 获取大肥鱼 */
    @Query("SELECT * FROM user_fish WHERE id = :fishId LIMIT 1")
    suspend fun getFishById(fishId: Int): UserFishEntity?

    /** 插入新大肥鱼（id 自增），返回新 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cat: UserFishEntity): Long

    /** 将所有大肥鱼设为未激活 */
    @Query("UPDATE user_fish SET isActive = 0")
    suspend fun deactivateAll()

    /** 将指定大肥鱼设为激活 */
    @Query("UPDATE user_fish SET isActive = 1 WHERE id = :fishId")
    suspend fun activateFish(fishId: Int)

    /** 增加当前激活大肥鱼的好感度 */
    @Query("UPDATE user_fish SET affinityLevel = affinityLevel + :amount WHERE isActive = 1")
    suspend fun addAffinity(amount: Int)

    /** 增加当前激活大肥鱼的投喂次数和好感度 */
    @Query("UPDATE user_fish SET totalFeedCount = totalFeedCount + 1, affinityLevel = affinityLevel + :affinityBonus WHERE isActive = 1")
    suspend fun incrementFeed(affinityBonus: Int)

    /** 用户是否已创建过任何大肥鱼 */
    @Query("SELECT COUNT(*) > 0 FROM user_fish")
    suspend fun hasAnyFish(): Boolean

    /** 删除指定大肥鱼（不可删除当前激活的大肥鱼） */
    @Query("DELETE FROM user_fish WHERE id = :fishId AND isActive = 0")
    suspend fun deleteFishById(fishId: Int)
}
