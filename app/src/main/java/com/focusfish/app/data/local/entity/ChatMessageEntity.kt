package com.focusfish.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 聊天消息实体
 *
 * 统计页 → 对话页后，这里承载大肥鱼与用户的全部对话。
 *
 * [kind] 用于区分消息来源，决定气泡样式与是否进入上下文：
 * - USER      用户输入
 * - FISH      大肥鱼回复（正常对话）
 * - PROACTIVE 大肥鱼主动发起（掷骰触发时的自言自语）
 * - SYSTEM    系统事件（专注结束、投喂完成、被拦截等），作为上下文注入而非气泡
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 消息角色：USER / FISH / PROACTIVE / SYSTEM */
    val role: String,

    /** 消息内容 */
    val content: String,

    /** 消息类型细分：CHAT / PROACTIVE / FOCUS_END / FEED / BLOCKED / SLEEP / READ_REPORT */
    val kind: String = "CHAT",

    /** 触发本条消息的大肥鱼动作名（用于主动行为复盘） */
    val action: String? = null,

    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis()
)
