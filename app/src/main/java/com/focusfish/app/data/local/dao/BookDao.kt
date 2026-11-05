package com.focusfish.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusfish.app.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

/**
 * 书籍 DAO
 */
@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BookEntity?

    /** 当前正在读的书（未读完且有进度） */
    @Query("SELECT * FROM books WHERE isFinished = 0 ORDER BY currentPage ASC, createdAt ASC LIMIT 1")
    suspend fun getCurrentReading(): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET currentPage = :page, isFinished = :finished WHERE id = :id")
    suspend fun updateProgress(id: Long, page: Int, finished: Boolean)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}
