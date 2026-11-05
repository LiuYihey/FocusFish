package com.focusfish.app.ai

/**
 * 大肥鱼的反应分档
 *
 * 这是 FocusFish 相对 FocusCat 最重要的体验升级：
 * 猫只有「好感度」一个维度，大肥鱼有「好感度 × 饱腹度 × 心情 × 精力」，
 * 组合出有层次的行为差异 —— 吃饱了、饿了、想让你专注、刚睡醒，
 * 每种状态下的反应都不一样。
 */
data class FishMood(
    /** 分档标识 */
    val tier: Tier,
    /** 注入给模型的状态描述 */
    val description: String,
    /** 触发该分档时给模型的行动暗示 */
    val actionHint: String,
    /** 该状态下大白饭加成倍率（吃饱了就不那么稀罕了） */
    val foodBonusMultiplier: Float = 1.0f
) {
    /**
     * 状态分档
     */
    enum class Tier {
        /** 刚认识，还很客气 */
        SHY,

        /** 熟悉了，会开玩笑 */
        FAMILIAR,

        /** 亲近，开始黏人 */
        CLOSE,

        /** 非常亲密，有默契 */
        BONDED,

        /** 饿坏了，只想吃饭 */
        STARVING,

        /** 吃饱了，懒洋洋 */
        FED,

        /** 想让你专注 */
        WANT_FOCUS,

        /** 困了 */
        SLEEPY,

        /** 心情不好 */
        GLUM,

        /** 刚睡醒，迷糊 */
        GROGGY
    }
}

/**
 * 状态解析器
 *
 * 把「好感度 + 饱腹度 + 心情 + 精力 + 是否刚睡醒」翻译成一个明确的
 * [FishMood]，供 UI（表情/动画）和 AI（说话方式）共同使用。
 *
 * 判定优先级：生理紧急状态（饿/困/刚醒） > 情绪状态 > 好感度档位。
 * 这样保证「饿了」的反馈不会被「好感度高」盖住 —— 活人也是这样，
 * 再亲近的关系下饿了也会先喊饿。
 */
object FishMoodResolver {

    /** 好感度档位阈值 */
    const val AFFINITY_SHY = 10
    const val AFFINITY_FAMILIAR = 50
    const val AFFINITY_CLOSE = 100
    const val AFFINITY_BONDED = 200

    /**
     * 解析当前状态
     *
     * @param affinity 好感度（投喂累积）
     * @param satiety 饱腹度 0~100
     * @param mood 心情 -100~100
     * @param energy 精力 0~100
     * @param isSleeping 是否正在睡觉
     * @param justWokeUp 是否刚睡醒（用于区分"刚醒"和"一直醒着"）
     */
    fun resolve(
        affinity: Int,
        satiety: Int,
        mood: Int,
        energy: Int,
        isSleeping: Boolean = false,
        justWokeUp: Boolean = false
    ): FishMood {
        // 1. 睡觉 / 刚睡醒
        if (isSleeping) {
            return FishMood(
                tier = FishMood.Tier.SLEEPY,
                description = "你正睡着，呼噜呼噜的。",
                actionHint = "你正在睡觉，梦到了一些东西。醒来后可以跟对方说说你梦到了什么。"
            )
        }
        if (justWokeUp) {
            return FishMood(
                tier = FishMood.Tier.GROGGY,
                description = "你刚睡醒，还有点迷糊，眼睛都没完全睁开。",
                actionHint = "你刚睡醒，脑子还有点懵，迷迷糊糊地想说点什么。"
            )
        }

        // 2. 饿
        if (satiety < 20) {
            return FishMood(
                tier = FishMood.Tier.STARVING,
                description = "你快饿扁了，脑子里全是大白饭。",
                actionHint = "你饿坏了，非常想要一份大白饭。",
                foodBonusMultiplier = 1.5f
            )
        }

        // 3. 困
        if (energy < 20) {
            return FishMood(
                tier = FishMood.Tier.SLEEPY,
                description = "你困得不行，眼皮开始打架了。",
                actionHint = "你困得厉害，说话都慢吞吞的。"
            )
        }

        // 4. 吃饱
        if (satiety > 85) {
            return FishMood(
                tier = FishMood.Tier.FED,
                description = "你刚吃饱，肚子圆滚滚的，浑身都是幸福。",
                actionHint = "你吃得很饱，说话都软乎乎的，特别满足。",
                foodBonusMultiplier = 0.5f
            )
        }

        // 5. 心情差
        if (mood < -40) {
            return FishMood(
                tier = FishMood.Tier.GLUM,
                description = "你有点不太高兴，闷闷的，不太想说话。",
                actionHint = "你心情不太好，闷闷的。如果对方关心你，你可以稍微软化一点。"
            )
        }

        // 6. 好感度档位
        return when {
            affinity >= AFFINITY_BONDED -> FishMood(
                tier = FishMood.Tier.BONDED,
                description = "你和对方已经很默契了，一个眼神就知道对方在想什么。",
                actionHint = "你们已经很熟了。你说话可以更随意，甚至可以偶尔撒个小娇。"
            )

            affinity >= AFFINITY_CLOSE -> FishMood(
                tier = FishMood.Tier.CLOSE,
                description = "你很喜欢对方，尾巴都藏不住那种喜欢。",
                actionHint = "你很喜欢对方，说话会带上明显的亲昵劲儿。"
            )

            affinity >= AFFINITY_FAMILIAR -> FishMood(
                tier = FishMood.Tier.FAMILIAR,
                description = "你和对方已经熟悉了，会开点玩笑。",
                actionHint = "你和对方已经挺熟了，可以开玩笑、可以损他两句。"
            )

            affinity >= AFFINITY_SHY -> FishMood(
                tier = FishMood.Tier.SHY,
                description = "你刚认识对方，还有点不好意思。",
                actionHint = "你刚认识对方，还有点拘谨，说话客气。"
            )

            else -> FishMood(
                tier = FishMood.Tier.SHY,
                description = "你还不认识对方，有点怕生。",
                actionHint = "你还不认识对方，怯生生的，想靠近又不敢。"
            )
        }
    }

    /**
     * 「想让你专注」状态
     *
     * 这是一个特殊触发态：不是由数值决定的，而是由「用户长时间碰手机」
     * 这个外部事件触发。单独提供方法便于事件驱动调用。
     */
    fun wantFocus(): FishMood = FishMood(
        tier = FishMood.Tier.WANT_FOCUS,
        description = "你有点担心对方，想劝他去专注一会儿。",
        actionHint = "你想劝对方别老碰手机了，去专注一会儿。"
    )

    /**
     * 好感度档位标题（UI 用）
     */
    fun affinityTitle(affinity: Int): String = when {
        affinity >= AFFINITY_BONDED -> "同一条鱼"
        affinity >= AFFINITY_CLOSE -> "心有灵犀"
        affinity >= AFFINITY_FAMILIAR -> "默契伙伴"
        affinity >= AFFINITY_SHY -> "初识之友"
        else -> "初次见面"
    }

    /**
     * 下一个里程碑（UI 用）
     */
    fun nextMilestone(affinity: Int): Int = when {
        affinity < AFFINITY_SHY -> AFFINITY_SHY
        affinity < AFFINITY_FAMILIAR -> AFFINITY_FAMILIAR
        affinity < AFFINITY_CLOSE -> AFFINITY_CLOSE
        affinity < AFFINITY_BONDED -> AFFINITY_BONDED
        else -> AFFINITY_BONDED
    }

    /**
     * 心情档位对应的表情资源名（UI 用，缺图时回退到 idle）
     */
    fun expressionFor(mood: FishMood): String = when (mood.tier) {
        FishMood.Tier.STARVING -> "hungry"
        FishMood.Tier.FED -> "happy"
        FishMood.Tier.WANT_FOCUS -> "nudge"
        FishMood.Tier.SLEEPY, FishMood.Tier.GROGGY -> "sleeping"
        FishMood.Tier.GLUM -> "sulky"
        FishMood.Tier.BONDED, FishMood.Tier.CLOSE -> "happy"
        FishMood.Tier.FAMILIAR -> "idle"
        FishMood.Tier.SHY -> "idle"
    }
}
