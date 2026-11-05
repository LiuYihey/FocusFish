package com.focusfish.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户大肥鱼实体
 *
 * 支持多只大肥鱼实例化设计：每只大肥鱼是独立记录，有独立的 breedId/name/affinityLevel/totalFeedCount。
 * 通过 [isActive] 标记当前激活的大肥鱼（全局唯一），切换时：
 * - 旧大肥鱼 isActive=false（保留其好感度/投喂次数等数据）
 * - 新大肥鱼 isActive=true（恢复其独立数据）
 *
 * 类似"切换账号"语义：每只大肥鱼的养成进度独立保留，切换即恢复该大肥鱼的完整状态。
 */
@Entity(tableName = "user_fish")
data class UserFishEntity(
    /** 主键自增，支持多只大肥鱼 */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** 大肥鱼形态 ID，关联 fish_catalog */
    val breedId: String,
    /** 用户给大肥鱼起的名字 */
    val name: String,
    /** 好感度数值，默认 0（每只大肥鱼独立） */
    val affinityLevel: Int = 0,
    /** 累计投喂次数，默认 0（每只大肥鱼独立） */
    val totalFeedCount: Int = 0,
    /** 是否为当前激活的大肥鱼（全局唯一，切换时由 DAO 事务保证） */
    val isActive: Boolean = false,
    /** 创建时间戳（毫秒） */
    val createdAt: Long = System.currentTimeMillis()
)
