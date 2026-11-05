package com.focusfish.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 桌宠素材库
 *
 * 素材在构建前用 `.tools/make-pet-assets.py` 抠过白底，
 * 存在 assets/pet/ 下（PNG，带 alpha）。
 *
 * 与 App 内的 expressions/ 不同：
 * - expressions/ 是统一规格的大表情，给聊天气泡和全屏鼓励页用
 * - pet/ 是抠过白底的紧凑版，给桌宠用（桌宠尺寸小，需要更干净的边缘）
 */
object PetSpriteLibrary {

    /**
     * 桌宠的动画状态
     */
    enum class Pose(val fileName: String) {
        /** 待机 */
        IDLE("idle"),

        /** 饿了，端着大白饭 */
        HUNGRY("hungry"),

        /** 睡着了 */
        SLEEPING("sleeping"),

        /** 摸鱼 */
        SLACKING("slacking"),

        /** 开心 */
        EXCITED("excited"),

        /** 委屈 */
        SAD("sad"),

        /** 生气 */
        ANGRY("angry"),

        /** 加油 */
        CHEER("cheer"),

        /** 害羞 */
        SHY("shy"),

        /** 得意 */
        PROUD("proud"),

        /** 无聊 */
        BORED("bored"),

        /** 在读书 */
        READING("reading"),

        /** 思考 */
        THINKING("thinking")
    }

    /** 缓存：避免每帧重新解码 */
    private val cache = HashMap<String, Bitmap>()

    /**
     * 加载桌宠贴图
     */
    fun load(context: Context, pose: Pose): Bitmap? {
        val key = "pet/${pose.fileName}"
        cache[key]?.let { return it }
        return try {
            context.assets.open("$key.webp").use {
                BitmapFactory.decodeStream(it)
            }?.also { cache[key] = it }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 加载备用品种（元气大肥鱼）
     */
    fun loadAlt(context: Context, pose: Pose): Bitmap? {
        if (pose != Pose.IDLE) return null
        val key = "pet/idle2"
        cache[key]?.let { return it }
        return try {
            context.assets.open("$key.webp").use {
                BitmapFactory.decodeStream(it)
            }?.also { cache[key] = it }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 释放缓存（桌宠服务销毁时）
     */
    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }
}
