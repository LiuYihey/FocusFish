package com.focusfish.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusfish.app.data.local.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

/**
 * 人格卡 DAO（单条，id = 1）
 */
@Dao
interface PersonaDao {

    @Query("SELECT * FROM persona WHERE id = 1")
    fun observe(): Flow<PersonaEntity?>

    @Query("SELECT * FROM persona WHERE id = 1")
    suspend fun get(): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: PersonaEntity)
}
