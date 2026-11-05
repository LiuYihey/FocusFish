package com.focusfish.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusfish.app.data.local.entity.FishMemoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 大肥鱼记忆 DAO
 */
@Dao
interface FishMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: FishMemoryEntity): Long

    @Update
    suspend fun update(memory: FishMemoryEntity)

    @Delete
    suspend fun delete(memory: FishMemoryEntity)

    @Query("DELETE FROM fish_memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 全部记忆，按创建时间倒序（记忆管理页用） */
    @Query("SELECT * FROM fish_memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FishMemoryEntity>>

    /** 已确认的记忆（参与对话注入） */
    @Query("SELECT * FROM fish_memories WHERE status = :status ORDER BY isPinned DESC, lastRecalledAt DESC")
    suspend fun getByStatus(status: String = FishMemoryEntity.STATUS_CONFIRMED): List<FishMemoryEntity>

    /** 候选记忆（等人点头） */
    @Query("SELECT * FROM fish_memories WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String = FishMemoryEntity.STATUS_CANDIDATE): Flow<List<FishMemoryEntity>>

    @Query("UPDATE fish_memories SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM fish_memories WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    /** 清空全部记忆 */
    @Query("DELETE FROM fish_memories")
    suspend fun clearAll()

    /** 记录一次复述：更新最后复述时间与次数 */
    @Query("UPDATE fish_memories SET lastRecalledAt = :now, recallCount = recallCount + 1 WHERE id IN (:ids)")
    suspend fun markRecalled(ids: List<Long>, now: Long = System.currentTimeMillis())
}
