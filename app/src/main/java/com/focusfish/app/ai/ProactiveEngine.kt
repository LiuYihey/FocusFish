package com.focusfish.app.ai

import java.util.Calendar
import javax.inject.Inject

/**
 * 大肥鱼主动行为
 *
 * 参照 dsh-speak-up（DeepSeek Harness 生态的「主动开口」插件）的机制：
 * 不是定时必发消息，而是本地掷骰与命中线比较，只有真要说话时才调模型 ——
 * 这样既省 token，又不会变成定时骚扰。
 */
enum class ProactiveAction(
    /** 该行为对应的 system prompt 指令 */
    val hint: String,
    /** 默认权重（相对值，非概率） */
    val weight: Int,
    /** 冷却时间：同一种行为在此时间内不重复触发 */
    val cooldownMillis: Long
) {
    /** 闲聊：没事找话说（摸鱼、敷衍这类是 IP 的核心人格簇） */
    CHAT(
        hint = "你现在有点无聊，想随口跟对方说句话。",
        weight = 40,
        cooldownMillis = 8 * 60 * 1000L
    ),

    /** 提醒：该专注了 */
    NUDGE(
        hint = "你想提醒对方别再碰手机了，去做点正事。",
        weight = 20,
        cooldownMillis = 15 * 60 * 1000L
    ),

    /** 汇报：你陪读的那本书有进展 */
    READING(
        hint = "你刚读完手上那本书的一页，想跟对方说说你看到了什么。",
        weight = 20,
        cooldownMillis = 20 * 60 * 1000L
    ),

    /** 发呆：你自己在胡思乱想 */
    DAYDREAM(
        hint = "你刚才发了一会儿呆，脑子里冒出些奇怪的想法，想跟对方念叨念叨。",
        weight = 12,
        cooldownMillis = 25 * 60 * 1000L
    ),

    /** 饿了：想吃东西 */
    HUNGRY(
        hint = "你有点饿了，很想要一份大白饭。",
        weight = 12,
        cooldownMillis = 30 * 60 * 1000L
    ),

    /** 巡游：出去转了一圈 */
    WANDER(
        hint = "你刚才在自己的小世界里转了一圈，看到了些有意思的东西。",
        weight = 8,
        cooldownMillis = 40 * 60 * 1000L
    ),

    /** 睡觉：困了 */
    SLEEP(
        hint = "你有点困了，想眯一会儿。",
        weight = 5,
        cooldownMillis = 60 * 60 * 1000L
    );

    companion object {
        /** 按权重随机抽一个动作 */
        fun pick(random: kotlin.random.Random = kotlin.random.Random.Default): ProactiveAction {
            val total = entries.sumOf { it.weight }
            var roll = random.nextInt(total)
            for (action in entries) {
                roll -= action.weight
                if (roll < 0) return action
            }
            return CHAT
        }
    }
}

/**
 * 开口的「由头」
 *
 * 借鉴 dsh-mnemo-proactive 的分类：主动开口必须有理由，
 * 不能是「系统让我说句话」。分类决定注入给模型的动机文案，
 * 让模型输出的是「她自己的念头」而不是「一条通知」。
 *
 * 注意：这里的 hint 是**兜底文案**。如果用户在人格卡里写了
 * 「主动开口的动机」，那一段会作为总约束追加在后面，
 * 优先级高于这里的分类 —— 用户改写动机 = 用户认领这个角色。
 */
enum class ProactiveMotive(
    /** 注入给模型的动机描述 */
    val hint: String,
    /** 权重 */
    val weight: Int
) {
    /** 情绪：心情有波动，想找对方说说 */
    EMOTION(
        hint = "你心里有点情绪，说不上来是什么就是想跟对方念叨两句。",
        weight = 20
    ),

    /** 里程碑：刚刚发生了什么值得庆祝/纪念的事 */
    MILESTONE(
        hint = "刚刚发生了一件对你来说有意义的事，你想跟对方说一声。",
        weight = 12
    ),

    /** 联想：看到了什么/想到了什么，跟对方有关 */
    ASSOCIATION(
        hint = "你刚才看到/想到一件事，脑子里一下子冒出跟对方有关的念头，就想提一嘴。",
        weight = 22
    ),

    /** 记忆：想起对方之前说过的事 */
    MEMORY(
        hint = "你忽然想起对方之前说过的一件事，想起来就顺手问一句。",
        weight = 18
    ),

    /** 沉默：太久没人理你了 */
    SILENCE(
        hint = "好久没人跟你说话了，你有点闷，想随便冒个泡。",
        weight = 28
    );

    companion object {
        /** 按权重随机抽一个由头 */
        fun pick(random: kotlin.random.Random = kotlin.random.Random.Default): ProactiveMotive {
            val total = entries.sumOf { it.weight }
            var roll = random.nextInt(total)
            for (motive in entries) {
                roll -= motive.weight
                if (roll < 0) return motive
            }
            return SILENCE
        }
    }
}

/**
 * 主动行为调度结果
 */
data class ProactiveDecision(
    /** 是否触发 */
    val triggered: Boolean,
    /** 触发的动作 */
    val action: ProactiveAction?,
    /** 未触发的原因（用于日志与调试） */
    val reason: String,
    /** 本次计算的命中线（百分比），用于调试与设置页展示 */
    val hitRate: Float = 0f,
    /** 本次开口的「由头」（emotion/milestone/association/memory/silence） */
    val motive: String = ""
)

/**
 * 主动性引擎
 *
 * 命中线公式（照搬 dsh-speak-up，已核实）：
 * ```
 * 最终命中线 = min(100, 档位命中线 × 时机因子 + min(加成上限, 连没中 × 没中加成))
 * ```
 * 先乘因子、再加攒下来的加成，最后夹到 0–100%。
 *
 * 默认参数（对齐 dsh-speak-up）：
 * - 峰时命中线 20 / 谷时命中线 60（峰时 = 贵时段，少开口；谷时 = 便宜，多开口）
 * - 峰时判定：周一至周五 09:00–12:00 与 14:00–18:00
 * - 没中加成 10 / 次，加成上限 40
 * - 近阈值 20 分钟 → 因子 0.15（刚说过话，闭嘴）
 * - 远阈值 360 分钟 → 因子 1.5（很久没说话，多说点）
 * - 搭话冷却 20 分钟
 * - 安静时段 01:00–08:00
 *
 * Android 侧额外加了三道「分寸闸门」（对应 dsh-heartbeat 的闸门相）：
 * 全部是纯代码判定，判定「别说」时根本不调模型。
 */
class ProactiveEngine @Inject constructor() {

    companion object {
        /** 最小检查间隔 */
        const val MIN_CHECK_INTERVAL_MS = 3 * 60 * 1000L

        /** 默认检查间隔 */
        const val DEFAULT_CHECK_INTERVAL_MS = 10 * 60 * 1000L

        /** 最大检查间隔 */
        const val MAX_CHECK_INTERVAL_MS = 60 * 60 * 1000L

        /** 峰时命中线 */
        const val PEAK_HIT_RATE = 20f

        /** 谷时命中线 */
        const val VALLEY_HIT_RATE = 60f

        /** 每次没掷中的加成 */
        const val MISS_BONUS = 10f

        /** 加成上限 */
        const val MISS_BONUS_CAP = 40f

        /** 近阈值：距上次说话小于此值 → 因子 0.15 */
        const val NEAR_THRESHOLD_MS = 20 * 60 * 1000L
        const val NEAR_FACTOR = 0.15f

        /** 远阈值：距上次说话大于此值 → 因子 1.5 */
        const val FAR_THRESHOLD_MS = 360 * 60 * 1000L
        const val FAR_FACTOR = 1.5f

        /** 搭话冷却 */
        const val SPEAK_COOLDOWN_MS = 20 * 60 * 1000L

        /**
         * 短冷却（"话痨"档）：刚说完话 15-40 秒内不再开口
         *
         * 借鉴 dsh-whale-musume 的双档冷却设计。
         * 人不是按时钟说话的 —— 固定冷却太机械。
         */
        const val SHORT_COOLDOWN_MIN_MS = 15 * 1000L
        const val SHORT_COOLDOWN_MAX_MS = 40 * 1000L

        /**
         * 安静冷却档：90-240 秒不主动开口
         *
         * 两次主动开口之间留够空档，让它显得"不是一直在烦你"。
         */
        const val QUIET_COOLDOWN_MIN_MS = 90 * 1000L
        const val QUIET_COOLDOWN_MAX_MS = 240 * 1000L

        /** 抽到话痨档的概率（0~1） */
        const val CHATTY_COOLDOWN_CHANCE = 0.3f

        /** 久坐关怀：连续专注/使用超过 25 分钟，关心一句 */
        const val SEDENTARY_REMINDER_MS = 25 * 60 * 1000L

        /** 久坐关怀的最小间隔：同一段久坐只提醒一次 */
        const val SEDENTARY_REMINDER_COOLDOWN_MS = 40 * 60 * 1000L

        /** 安静时段起止（小时，含起不含止） */
        const val QUIET_START_HOUR = 1
        const val QUIET_END_HOUR = 8

        /** 每日主动开口上限（防打扰核心） */
        const val DAILY_PROACTIVE_LIMIT = 6
    }

    /**
     * 做一次决策
     *
     * @param now 当前时间戳
     * @param lastCheckAt 上次检查时间戳
     * @param lastProactiveAt 上次真正触发的时间戳
     * @param missStreak 连续没掷中的次数（用于加成）
     * @param recentActions 最近已触发的动作与时间，用于冷却判定
     * @param intensity 用户设置的主动性强度 0~100，用于缩放基础命中线
     * @param todayProactiveCount 今日已主动开口次数
     * @param isFocusing 用户是否正在专注（专注时降低打扰频率）
     * @param isSleeping 大肥鱼是否在睡觉
     * @param isHungry 大肥鱼是否饿了
     * @param hasReadingBook 是否有正在读的书
     * @param hasPendingMemory 是否有待确认的记忆（可以顺口提一句）
     */
    fun decide(
        now: Long,
        lastCheckAt: Long,
        lastProactiveAt: Long,
        missStreak: Int = 0,
        recentActions: List<Pair<ProactiveAction, Long>> = emptyList(),
        intensity: Int = 40,
        todayProactiveCount: Int = 0,
        isFocusing: Boolean = false,
        isSleeping: Boolean = false,
        isHungry: Boolean = false,
        hasReadingBook: Boolean = false,
        hasPendingMemory: Boolean = false
    ): ProactiveDecision {
        // ── 分寸闸门 1：睡觉时不说话 ──
        if (isSleeping) {
            return ProactiveDecision(false, null, "大肥鱼在睡觉")
        }

        // ── 分寸闸门 2：安静时段（凌晨 1 点到早上 8 点）──
        if (isQuietHours(now)) {
            return ProactiveDecision(false, null, "安静时段")
        }

        // ── 分寸闸门 3：每日开口上限 ──
        if (todayProactiveCount >= DAILY_PROACTIVE_LIMIT) {
            return ProactiveDecision(false, null, "今日主动开口已达上限 $DAILY_PROACTIVE_LIMIT 次")
        }

        // ── 分寸闸门 4：检查间隔未到 ──
        val elapsed = now - lastCheckAt
        if (elapsed < MIN_CHECK_INTERVAL_MS) {
            return ProactiveDecision(false, null, "距上次检查仅 ${elapsed / 1000} 秒")
        }

        // ── 分寸闸门 5：搭话冷却（双档随机）──
        // 借鉴 dsh-whale-musume：人不是按时钟说话的。
        // 「话痨档」15-40 秒：上一句还没落地就又来一句，会很出戏；
        // 「安静档」90-240 秒：留够空档，让她显得不是一直在烦你。
        // 两档按 3:7 随机抽，形成"有时话多、有时安静"的节奏。
        // 注意：真正控制打扰频率的是下面的命中线（近阈值因子 0.15），
        // 这里只负责"上一句落地之后别立刻接上"。
        if (lastProactiveAt > 0) {
            val since = now - lastProactiveAt
            val cooldown = randomCooldown()
            if (since < cooldown) {
                return ProactiveDecision(false, null, "搭话冷却中（还需 ${(cooldown - since) / 1000} 秒）")
            }
        }

        // ── 计算命中线 ──
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val isPeak = isPeakHour(hour)
        val base = if (isPeak) PEAK_HIT_RATE else VALLEY_HIT_RATE

        // 用户强度调节：0 → 30% 基准线，100 → 130% 基准线
        val intensityFactor = 0.3f + (intensity.coerceIn(0, 100) / 100f) * 1.0f

        // 时机因子：刚说过话闭嘴，很久没说话多说
        val sinceLastSpeak = now - lastProactiveAt
        val timeFactor = when {
            lastProactiveAt > 0 && sinceLastSpeak < NEAR_THRESHOLD_MS -> NEAR_FACTOR
            lastProactiveAt > 0 && sinceLastSpeak > FAR_THRESHOLD_MS -> FAR_FACTOR
            else -> 1f
        }

        // 连续没中加成：越没人理她越想开口（上限 40）
        val bonus = minOf(MISS_BONUS_CAP, missStreak * MISS_BONUS)

        val hitRate = (base * intensityFactor * timeFactor + bonus).coerceIn(0f, 100f)

        // ── 掷骰 ──
        if (kotlin.random.Random.nextFloat() * 100f > hitRate) {
            return ProactiveDecision(false, null, "掷骰未命中（命中线 ${hitRate.toInt()}%）", hitRate)
        }

        // ── 上下文调整候选动作 ──
        // 关键规则（借鉴 whale-musume「工作时绝不插嘴」）：
        // 用户正在专注时，除了「汇报阅读」（那不打扰，是分享）之外一律不开口。
        // 专注的人最烦被催，这一条直接决定她是不是"懂你"。
        val candidates = ProactiveAction.entries.filter { action ->
            when (action) {
                // 饿的时候才说饿了；不饿时说饿了很假
                ProactiveAction.HUNGRY -> isHungry
                // 有书才汇报阅读
                ProactiveAction.READING -> hasReadingBook
                // 专注时不催对方（那是在打扰），反之才提醒
                ProactiveAction.NUDGE -> !isFocusing
                // 专注时不闲聊、不发呆、不吐槽 —— 只有汇报阅读例外
                ProactiveAction.CHAT, ProactiveAction.DAYDREAM,
                ProactiveAction.WANDER, ProactiveAction.SLEEP -> !isFocusing
                else -> true
            }
        }
        if (candidates.isEmpty()) {
            return ProactiveDecision(false, null, "当前上下文无可用动作", hitRate)
        }

        // ── 按权重抽取 ──
        val action = pickWeighted(candidates)

        // ── 单动作冷却 ──
        val lastSame = recentActions.firstOrNull { it.first == action }
        if (lastSame != null && now - lastSame.second < action.cooldownMillis) {
            return ProactiveDecision(false, null, "${action.name} 冷却中", hitRate)
        }

        // ── 抽「由头」：主动开口必须有理由，不能是空冒泡 ──
        val motive = pickMotive(
            action = action,
            hasPendingMemory = hasPendingMemory,
            isHungry = isHungry,
            hasReadingBook = hasReadingBook,
            sinceLastSpeak = sinceLastSpeak
        )

        return ProactiveDecision(
            triggered = true,
            action = action,
            reason = "掷骰命中 ${action.name}（命中线 ${hitRate.toInt()}%，由头 ${motive.name}）",
            hitRate = hitRate,
            motive = motive.name
        )
    }

    /**
     * 挑一个开口的由头
     *
     * 规则优先于随机：上下文明显指向某个由头时优先用它，
     * 否则按权重随机（沉默最常见，因为"太久没人理"是最高频的真实动机）。
     */
    private fun pickMotive(
        action: ProactiveAction,
        hasPendingMemory: Boolean,
        isHungry: Boolean,
        hasReadingBook: Boolean,
        sinceLastSpeak: Long
    ): ProactiveMotive = when {
        action == ProactiveAction.HUNGRY -> ProactiveMotive.EMOTION
        action == ProactiveAction.READING -> ProactiveMotive.MILESTONE
        action == ProactiveAction.NUDGE -> ProactiveMotive.ASSOCIATION
        hasPendingMemory -> ProactiveMotive.MEMORY
        isHungry -> ProactiveMotive.EMOTION
        hasReadingBook && sinceLastSpeak > FAR_THRESHOLD_MS -> ProactiveMotive.ASSOCIATION
        else -> ProactiveMotive.pick()
    }

    /**
     * 是否为峰时段（工作日 09:00–12:00 与 14:00–18:00）
     */
    private fun isPeakHour(hour: Int): Boolean {
        val isWeekend = Calendar.getInstance().let { cal ->
            cal.set(Calendar.HOUR_OF_DAY, hour)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        }
        if (isWeekend) return false
        return (hour in 9..11) || (hour in 14..17)
    }

    /**
     * 抽一个搭话冷却时长
     *
     * 3 成概率走「话痨档」（15-40 秒），7 成走「安静档」（90-240 秒）。
     */
    private fun randomCooldown(): Long {
        return if (kotlin.random.Random.nextFloat() < CHATTY_COOLDOWN_CHANCE) {
            randomLong(SHORT_COOLDOWN_MIN_MS, SHORT_COOLDOWN_MAX_MS)
        } else {
            randomLong(QUIET_COOLDOWN_MIN_MS, QUIET_COOLDOWN_MAX_MS)
        }
    }

    /** 取 [min, max] 区间内的随机长整数 */
    private fun randomLong(min: Long, max: Long): Long {
        if (max <= min) return min
        return min + (kotlin.random.Random.nextLong() % (max - min))
    }

    /**
     * 是否处于安静时段
     */
    private fun isQuietHours(now: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return hour >= QUIET_START_HOUR && hour < QUIET_END_HOUR
    }

    private fun pickWeighted(candidates: List<ProactiveAction>): ProactiveAction {
        val total = candidates.sumOf { it.weight }
        var roll = kotlin.random.Random.nextInt(total)
        for (action in candidates) {
            roll -= action.weight
            if (roll < 0) return action
        }
        return candidates.first()
    }
}
