package com.focusfish.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 大肥鱼状态实体
 *
 * 承载「活的」那一部分：饱腹度、心情、精力、睡觉时长。
 * 好感度仍存在 user_fish 表（沿用 FocusCat 的养成语义），
 * 这里补的是好感度之外的「生理状态」，两者共同决定大肥鱼的反应分档。
 *
 * 单条记录（id = 1），跟随当前激活的大肥鱼。
 */
@Entity(tableName = "fish_state")
data class FishStateEntity(
    @PrimaryKey
    val id: Int = 1,

    /** 饱腹度 0~100。吃大白饭会上升，随时间缓慢下降 */
    val satiety: Int = 70,

    /** 心情值 -100~100。受投喂、专注陪伴、被拦截等事件影响 */
    val mood: Int = 0,

    /** 精力 0~100。睡觉时上升，随活动下降 */
    val energy: Int = 80,

    /** 当前是否在睡觉 */
    val isSleeping: Boolean = false,

    /** 本次入睡时间戳 */
    val sleepStartedAt: Long = 0,

    /** 累计睡眠毫秒 */
    val totalSleepMillis: Long = 0,

    /** 上次主动行为触发时间戳（用于冷却判定） */
    val lastProactiveAt: Long = 0,

    /** 上次状态变化时间戳 */
    val updatedAt: Long = System.currentTimeMillis()
)
