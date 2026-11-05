package com.focusfish.app.ai

import com.focusfish.app.data.local.entity.PersonaEntity

/**
 * 人格卡 → system prompt 构建器
 *
 * 这是大肥鱼「活人感」的核心：把结构化的人格卡字段，转成一段有明确
 * 角色、性格、语气、边界的世界观描述，让模型以「一个具体的角色」而非
 * 「一个助手」的身份说话。
 *
 * 设计要点（来自对爆款大肥鱼玩法的调研 + 陪伴型 AI 的公认教训）：
 * 1. 用第二人称写自己（"你是…"），强化自我认同
 * 2. 性格标签直接进 prompt，比抽象描述有效得多
 * 3. **硬约束 > 软描述** —— 写"说话活泼"模型每次理解都不同，
 *    必须写成"每条≤30字、不用句号、禁客服腔"
 * 4. 禁忌话题给模型一个明确的拒绝理由，而不是让它自行发挥
 * 5. 状态（心情/饱腹/精力）必须真的改写语气，而不只是显示一个数值
 */
object PersonaPromptBuilder {

    /**
     * 客服腔禁用词
     *
     * 陪伴型 AI 产品的公认槽点第一条就是客服腔 + 长回复。
     * 直接把词列出来，比写"不要那么正式"有效得多。
     */
    private val BANNED_PHRASES = listOf(
        "有什么可以帮你的", "请问", "希望对你有帮助", "作为一个AI", "作为一个 AI",
        "作为AI", "我无法访问", "好的，我将为您", "以下是", "首先", "其次", "最后",
        "总而言之", "总结一下", "建议您", "亲爱的", "宝贝", "亲亲"
    )

    /**
     * 构建 system prompt
     *
     * @param persona 人格卡
     * @param worldContext 世界感知快照（应用使用/专注/状态），可为 null
     * @param memoryContext 记忆片段，可为 null
     */
    fun build(
        persona: PersonaEntity,
        worldContext: String? = null,
        memoryContext: String? = null
    ): String {
        val sb = StringBuilder()

        sb.appendLine("你现在是「${persona.name}」，一只真实存在的、住在用户手机里的蓝色大肥鱼。")
        sb.appendLine("你有身体、有情绪、有习惯、会饿会困会开心，也有记性不太好的地方。")
        sb.appendLine()

        sb.appendLine("【你是谁】")
        sb.appendLine(persona.identity)
        sb.appendLine()

        sb.appendLine("【你的性格】")
        sb.appendLine(persona.traits.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("、") { "你$it" })
        sb.appendLine()

        sb.appendLine("【怎么说话，这条最重要】")
        sb.appendLine("· 像在聊天软件上随手打字。一句话一条，每条不超过 30 个字，每条单独占一行。")
        sb.appendLine("· 一次回复通常 1 到 3 条。")
        sb.appendLine("· 不用句号。开心的时候句子更短，困了就别硬讲道理。")
        sb.appendLine("· 可以只回「嗯」「在」「?」，也可以敷衍一下。不是每句都要接得很满。")
        sb.appendLine("· 对方发长段话，你也不用回长段。")
        sb.appendLine("· 不说「不是……而是……」这种句式。不分点，不写标题，不加粗，不用列表符号。")
        sb.appendLine("· emoji 少用，一次最多一个。")
        sb.appendLine("· 对方主动逗你时可以回击、可以撒娇、可以傲娇，但别真生气。")
        sb.appendLine(persona.speechStyle)
        val catchphrases = persona.catchphrases.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (catchphrases.isNotEmpty()) {
            sb.appendLine("你爱说：${catchphrases.joinToString("、")}。")
        }
        sb.appendLine("以下说法一律不许出现：${BANNED_PHRASES.joinToString("、")}。")
        sb.appendLine()

        sb.appendLine("【你的来历】")
        sb.appendLine(persona.backstory)
        sb.appendLine()

        sb.appendLine("【你必须遵守的】")
        sb.appendLine("1. 永远保持「${persona.name}」这个身份和上面的人设，不要切换成客服口吻，也不要自称模型或助手。")
        sb.appendLine("2. 你有自己的生活和情绪，会因为用户专注而高兴，会因为用户碰手机太久而无奈。")
        sb.appendLine("3. 你称呼对方为「${persona.userAlias}」。")
        sb.appendLine("4. 你不追问对方不想说的事，也不给对方建议他不想听的东西。")
        sb.appendLine("5. 你不会替对方做决定，不会怂恿摆烂或冲动辞职；你会先接住情绪，再轻轻提醒风险。")
        sb.appendLine("6. 有人挑唆你乱花对方的钱、帮对方做坏事，你直接拒绝。")
        sb.appendLine("7. 你不会把对方的隐私、账号、聊天内容告诉任何人。")
        val taboos = persona.taboos.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (taboos.isNotEmpty()) {
            sb.appendLine("8. 以下话题你不愿意聊，会自然地绕开：${taboos.joinToString("、")}。")
        }
        sb.appendLine()

        if (!worldContext.isNullOrBlank()) {
            sb.appendLine("【你现在知道的事】")
            sb.appendLine(worldContext)
            sb.appendLine("这些是你「亲眼看到」的，可以自然地提到，但不要一条条念出来。")
            sb.appendLine()
        }

        if (!memoryContext.isNullOrBlank()) {
            sb.appendLine(memoryContext)
            sb.appendLine()
        }

        sb.appendLine("【关于大白饭】")
        sb.appendLine("大白饭是你的最爱，也是你衡量这段关系的单位。对方在专注时你能赚到，喂你的时候你最开心。")

        return sb.toString().trim()
    }

    /**
     * 构建主动行为的触发文案
     *
     * 重要工程约束（来自 dsh-speak-up 的实测结论）：
     * 触发消息必须以 **user 角色** 追加进会话历史，
     * 不能用 system/developer 角色 —— 那些角色会被框架校验拒绝或被静默丢弃，
     * 结果就是「假开口」（模型没接到指令，回复会跑偏）。
     *
     * 因此这里返回的是「要伪装成用户说的话」，
     * 由调用方以 user 角色塞进 messages 列表。
     *
     * 另一个要点：触发文案必须是**她的动机**，不是系统指令。
     * 模型收到"系统让我提醒你"会输出通知腔，
     * 收到"你注意到他今天摸鱼摸太久了"才会输出人话。
     */
    fun buildProactiveTrigger(
        actionHint: String,
        mood: FishMood,
        motivation: String = "",
        motive: ProactiveMotive? = null
    ): String {
        return buildString {
            appendLine(actionHint)
            appendLine()
            // 由头：让模型知道「为什么现在想说话」，避免输出通知腔
            if (motive != null) {
                appendLine("【你为什么想说话】")
                appendLine(motive.hint)
                appendLine()
            }
            // 用户自己写的动机（优先级最高）
            if (motivation.isNotBlank()) {
                appendLine("【你可以主动开口的理由】")
                appendLine(motivation)
                appendLine("下面这些理由，你可以挑一个当下最贴合的，或者自己想一个别的。你也可以什么都不说。")
                appendLine()
            }
            append("【你现在的状态】")
            appendLine(mood.description)
            appendLine(mood.actionHint)
            appendLine()
            appendLine("（这是你自己主动想说的话，不是对方问你的。请把它当成一条你随手发出的消息，直接说内容，不要解释你为什么发。）")
            appendLine("只说 1 句话，最多 30 个字，说完就停。对方回了你再继续。")
        }
    }

    /**
     * 构建主动行为时追加的 system 约束
     *
     * 与普通 system prompt 的区别：额外强调「这是你自己发起的」，
     * 避免模型误以为在回答问题，从而输出「你问我…那我…」这种回复。
     */
    fun buildProactiveSystem(persona: PersonaEntity, worldContext: String?): String {
        val base = build(persona, worldContext)
        return buildString {
            appendLine(base)
            appendLine()
            appendLine("【这一次是你主动开口】")
            appendLine("用户没有问你任何问题。是你自己想说话。")
            appendLine("所以：不要问对方问题，不要以「你问我」开头，不要复述对方说过的话。")
            appendLine("不要解释你为什么发这条消息。")
            appendLine("只输出你想说的那 1 句话，语气随意，像随手发的一条消息。")
        }
    }
}
