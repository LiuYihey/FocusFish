package com.focusfish.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusfish.app.data.local.entity.FishStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 大肥鱼状态 DAO（单条，id = 1）
 */
@Dao
interface FishStateDao {

    @Query("SELECT * FROM fish_state WHERE id = 1")
    fun observe(): Flow<FishStateEntity?>

    @Query("SELECT * FROM fish_state WHERE id = 1")
    suspend fun get(): FishStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FishStateEntity)

    /** 更新饱腹度（限制在 0~100） */
    @Query("UPDATE fish_state SET satiety = MAX(0, MIN(100, satiety + :delta)), updatedAt = :now WHERE id = 1")
    suspend fun addSatiety(delta: Int, now: Long = System.currentTimeMillis())

    /** 更新心情（限制在 -100~100） */
    @Query("UPDATE fish_state SET mood = MAX(-100, MIN(100, mood + :delta)), updatedAt = :now WHERE id = 1")
    suspend fun addMood(delta: Int, now: Long = System.currentTimeMillis())

    /** 更新精力（限制在 0~100） */
    @Query("UPDATE fish_state SET energy = MAX(0, MIN(100, energy + :delta)), updatedAt = :now WHERE id = 1")
    suspend fun addEnergy(delta: Int, now: Long = System.currentTimeMillis())

    /** 记录入睡 */
    @Query("UPDATE fish_state SET isSleeping = 1, sleepStartedAt = :now, updatedAt = :now WHERE id = 1")
    suspend fun markSleeping(now: Long = System.currentTimeMillis())

    /** 记录醒来，累计睡眠时长 */
    @Query("UPDATE fish_state SET isSleeping = 0, totalSleepMillis = totalSleepMillis + :slept, energy = MIN(100, energy + 30), updatedAt = :now WHERE id = 1")
    suspend fun markAwake(slept: Long, now: Long = System.currentTimeMillis())

    /** 更新上次主动行为时间 */
    @Query("UPDATE fish_state SET lastProactiveAt = :at, updatedAt = :at WHERE id = 1")
    suspend fun markProactive(at: Long = System.currentTimeMillis())
}
