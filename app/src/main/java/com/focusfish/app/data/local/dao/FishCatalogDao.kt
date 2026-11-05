package com.focusfish.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusfish.app.data.local.entity.FishCatalogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 大肥鱼形态目录 DAO
 * 提供对 fish_catalog 表的查询操作
 */
@Dao
interface FishCatalogDao {

    /** 获取所有大肥鱼形态 */
    @Query("SELECT * FROM fish_catalog ORDER BY displayName")
    suspend fun getAllBreeds(): List<FishCatalogEntity>

    /** 观察所有大肥鱼形态（Flow），种子数据插入后自动通知 UI */
    @Query("SELECT * FROM fish_catalog ORDER BY displayName")
    fun observeAllBreeds(): Flow<List<FishCatalogEntity>>

    /** 根据品种 ID 获取品种信息 */
    @Query("SELECT * FROM fish_catalog WHERE breedId = :breedId LIMIT 1")
    suspend fun getById(breedId: String): FishCatalogEntity?

    /** 插入品种（若已存在则替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(breeds: List<FishCatalogEntity>)
}
