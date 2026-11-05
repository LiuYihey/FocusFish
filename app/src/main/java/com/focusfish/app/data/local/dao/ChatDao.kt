package com.focusfish.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusfish.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 聊天消息 DAO
 */
@Dao
interface ChatDao {

    /** 观察全部消息（按时间升序） */
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    /** 观察最近 N 条（用于构建上下文窗口） */
    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ChatMessageEntity>

    /** 获取全部消息（单次） */
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    /** 插入消息 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    /** 清空全部消息 */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    /** 统计消息总数 */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int
}
