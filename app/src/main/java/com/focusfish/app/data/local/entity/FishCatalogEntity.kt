package com.focusfish.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 大肥鱼形态目录实体
 * 存储可选大肥鱼形态的目录信息（2 种品种：经典大肥鱼、元气大肥鱼）
 */
@Entity(tableName = "fish_catalog")
data class FishCatalogEntity(
    /** 品种 ID，如 "bigfatfish"、"bigfatfish2" */
    @PrimaryKey
    val breedId: String,
    /** 品种显示名称，如 "元气大肥鱼" */
    val displayName: String,
    /** 品种描述 */
    val description: String,
    /** 图标资源名（drawable 或 asset） */
    val iconAsset: String,
    /** 待机动画资源名 */
    val idleAnimAsset: String,
    /** 进食动画资源名 */
    val eatAnimAsset: String,
    /** 开心动画资源名 */
    val happyAnimAsset: String
)
