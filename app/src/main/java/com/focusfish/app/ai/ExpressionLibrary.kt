package com.focusfish.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.random.Random

/**
 * 表情库
 *
 * 大肥鱼的表情资源管理。
 *
 * 两套资源：
 * 1. `expressions/` 目录：统一规格的大表情（1024×1024 白底 Q 版立绘），
 *    用于对话气泡旁的大图、被拦截应用时的鼓励大图 —— 需要"大而清晰"。
 * 2. `stickers/` 目录：从「蓝色大肥鱼档案馆」采集的 48 张真实表情包
 *    （每种情绪 4 张），用于聊天气泡里的小表情 —— 需要"多、有变化、不重样"。
 *
 * 两套并存的原因：真实表情包更鲜活但规格不一（尺寸、留白、透明背景都有差别），
 * 直接放大到全屏会糊；统一规格的大表情适合全屏展示但只有一张，看多了会腻。
 * 分开用，各取所长。
 *
 * 资源约定（assets/expressions/）：
 * - hungry    饿了想吃大白饭
 * - sleeping  睡觉
 * - slacking  摸鱼
 * - excited   兴奋
 * - sad       委屈
 * - angry     生气
 * - cheer     加油鼓励
 * - shy       害羞
 * - proud     得意
 * - bored     无聊
 * - reading   在读书
 * - thinking  思考
 *
 * 贴纸约定（assets/stickers/）：st_{情绪}_{01..04}.webp
 *
 * 格式说明：表情与贴纸统一存为 WebP。
 * 贴纸原本是 PNG（20MB），转 WebP 后只剩 2MB —— 画质对 96dp 的气泡完全够看，
 * 但 APK 小了 18MB。表情同理，3.2MB → 0.7MB。
 */
object ExpressionLibrary {

    /**
     * 表情资源名枚举
     */
    enum class Expression(val fileName: String, val keywords: List<String>) {
        HUNGRY("hungry", listOf("吃", "饿", "饭", "饿坏")),
        SLEEPING("sleeping", listOf("睡", "困", "晚安", "梦")),
        SLACKING("slacking", listOf("摸鱼", "偷懒", "划水", "摸鱼中")),
        EXCITED("excited", listOf("太棒", "开心", "耶", "好耶", "成功")),
        SAD("sad", listOf("难过", "委屈", "伤心", "抱歉")),
        ANGRY("angry", listOf("生气", "讨厌", "气死", "别玩")),
        CHEER("cheer", listOf("加油", "坚持", "努力", "厉害", "棒")),
        SHY("shy", listOf("害羞", "不好意思", "脸红")),
        PROUD("proud", listOf("得意", "骄傲", "厉害啊", "我厉害")),
        BORED("bored", listOf("无聊", "没意思", "干嘛呢", "闲")),
        READING("reading", listOf("读书", "看书", "读到", "翻到")),
        THINKING("thinking", listOf("思考", "想法", "琢磨", "在想"));

        companion object {
            /**
             * 按文本内容猜测最合适的表情
             *
             * 关键词命中优先，命中不了返回 null（不强行配图）。
             */
            fun guess(text: String): Expression? {
                val lower = text.lowercase()
                // 命中最多的那个
                var best: Expression? = null
                var bestCount = 0
                for (e in entries) {
                    val count = e.keywords.count { lower.contains(it.lowercase()) }
                    if (count > bestCount) {
                        bestCount = count
                        best = e
                    }
                }
                return best
            }
        }
    }

    /**
     * 每种情绪对应的贴纸索引
     *
     * 与 assets/stickers/ 下的文件名一一对应。
     */
    private val STICKER_INDEX: Map<Expression, List<String>> = mapOf(
        Expression.HUNGRY to listOf("st_hungry_01", "st_hungry_02", "st_hungry_03", "st_hungry_04"),
        Expression.SLEEPING to listOf("st_sleeping_01", "st_sleeping_02", "st_sleeping_03", "st_sleeping_04"),
        Expression.SLACKING to listOf("st_slacking_01", "st_slacking_02", "st_slacking_03", "st_slacking_04"),
        Expression.EXCITED to listOf("st_excited_01", "st_excited_02", "st_excited_03", "st_excited_04"),
        Expression.SAD to listOf("st_sad_01", "st_sad_02", "st_sad_03", "st_sad_04"),
        Expression.ANGRY to listOf("st_angry_01", "st_angry_02", "st_angry_03", "st_angry_04"),
        Expression.CHEER to listOf("st_cheer_01", "st_cheer_02", "st_cheer_03", "st_cheer_04"),
        Expression.SHY to listOf("st_shy_01", "st_shy_02", "st_shy_03", "st_shy_04"),
        Expression.PROUD to listOf("st_proud_01", "st_proud_02", "st_proud_03", "st_proud_04"),
        Expression.BORED to listOf("st_bored_01", "st_bored_02", "st_bored_03", "st_bored_04"),
        Expression.READING to listOf("st_reading_01", "st_reading_02", "st_reading_03", "st_reading_04"),
        Expression.THINKING to listOf("st_thinking_01", "st_thinking_02", "st_thinking_03", "st_thinking_04")
    )

    /**
     * 加载表情 bitmap，失败返回 null
     */
    fun load(context: Context, expression: Expression): Bitmap? {
        return try {
            context.assets.open("expressions/${expression.fileName}.webp").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 资源是否已就绪（用于判断能否显示表情气泡）
     */
    fun isAvailable(context: Context, expression: Expression): Boolean {
        return try {
            context.assets.open("expressions/${expression.fileName}.webp").close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 加载一张表情包（真实素材）
     *
     * 同一情绪有多张，随机取一张 —— 这样连续几条消息不会总是同一个表情，
     * 这点很重要：表情包重复出现两次，活泼感就没了。
     *
     * @param expression 情绪
     * @param seed 随机种子，同一 seed 结果固定（避免 recomposition 时图片跳变）
     * @return bitmap，取不到返回 null
     */
    fun loadSticker(
        context: Context,
        expression: Expression,
        seed: Int = Random.nextInt()
    ): Bitmap? {
        val pool = STICKER_INDEX[expression] ?: return null
        // 用 seed 决定选哪一张，保证同一 seed 下稳定
        val name = pool[Math.floorMod(seed, pool.size)]
        return try {
            context.assets.open("stickers/$name.webp").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 表情包是否可用
     */
    fun isStickerAvailable(context: Context, expression: Expression): Boolean {
        val pool = STICKER_INDEX[expression] ?: return false
        return pool.any { name ->
            try {
                context.assets.open("stickers/$name.webp").close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
