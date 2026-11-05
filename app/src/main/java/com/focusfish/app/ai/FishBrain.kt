package com.focusfish.app.ai

import com.focusfish.app.data.local.dao.BookDao
import com.focusfish.app.data.local.dao.ChatDao
import com.focusfish.app.data.local.dao.FishStateDao
import com.focusfish.app.data.local.dao.PersonaDao
import com.focusfish.app.data.local.entity.ChatMessageEntity
import com.focusfish.app.data.local.entity.FishStateEntity
import com.focusfish.app.data.local.entity.PersonaEntity
import com.focusfish.app.data.repository.FishRepository
import com.focusfish.app.data.repository.StatsRepositoryImpl
import com.focusfish.app.util.FocusSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大肥鱼大脑
 *
 * 把「人格卡 + 世界感知 + 对话历史 + 当前状态」组装成一次 AI 调用。
 * 这是大肥鱼所有对外表达的统一出口 —— 无论是用户主动聊天，
 * 还是它自己掷骰触发的主动行为，走的都是这里。
 */
@Singleton
class FishBrain @Inject constructor(
    private val aiConfigStore: AiConfigStore,
    private val personaDao: PersonaDao,
    private val chatDao: ChatDao,
    private val fishStateDao: FishStateDao,
    private val fishRepository: FishRepository,
    private val statsRepository: StatsRepositoryImpl,
    private val bookDao: BookDao,
    private val worldContextProvider: WorldContextProvider,
    private val memoryEngine: MemoryEngine
) {

    companion object {
        /** 上下文窗口包含的最近消息条数 */
    const val CONTEXT_WINDOW = 20

        const val ROLE_USER = "USER"
        const val ROLE_FISH = "FISH"
        const val ROLE_PROACTIVE = "PROACTIVE"
        const val ROLE_SYSTEM = "SYSTEM"
    }

    /**
     * 获取当前人格卡，没有则创建默认的
     */
    suspend fun getPersona(): PersonaEntity {
        personaDao.get()?.let { return it }
        val default = PersonaEntity()
        personaDao.upsert(default)
        return default
    }

    /** 保存人格卡 */
    suspend fun savePersona(persona: PersonaEntity) {
        personaDao.upsert(persona.copy(updatedAt = System.currentTimeMillis()))
    }

    /** 观察人格卡 */
    fun observePersona(): Flow<PersonaEntity?> = personaDao.observe()

    /** 观察聊天记录 */
    fun observeMessages(): Flow<List<ChatMessageEntity>> = chatDao.observeAll()

    /** 观察状态 */
    fun observeState(): Flow<FishStateEntity?> = fishStateDao.observe()

    /** 获取状态，没有则初始化 */
    suspend fun getState(): FishStateEntity {
        fishStateDao.get()?.let { return it }
        val initial = FishStateEntity()
        fishStateDao.upsert(initial)
        return initial
    }

    /**
     * 发送一条用户消息并流式接收大肥鱼回复
     *
     * @param userInput 用户输入
     * @param onChunk 增量文本回调
     * @return 大肥鱼的完整回复
     */
    suspend fun chat(userInput: String, onChunk: (String) -> Unit): String {
        val config = aiConfigStore.config.value
        require(config.isValid) { "尚未配置 AI API" }

        // 1. 记录用户消息
        chatDao.insert(
            ChatMessageEntity(role = ROLE_USER, content = userInput, kind = "CHAT")
        )

        // 2. 组装上下文
        val persona = getPersona()
        val world = buildWorldContext()
        val mood = FishMoodResolver.resolve(
            affinity = currentAffinity(),
            satiety = world.satiety,
            mood = world.mood,
            energy = world.energy,
            isSleeping = world.isSleeping
        )

        // 记忆注入：已确认的记忆才进 prompt，候选态不参与
        val memories = memoryEngine.selectForInjection(userInput)
        val memoryContext = memoryEngine.renderForPrompt(memories)

        val systemPrompt = buildString {
            append(PersonaPromptBuilder.build(persona, worldContextProvider.describe(world), memoryContext))
            appendLine()
            appendLine("【你现在的状态】")
            appendLine(mood.description)
            appendLine(mood.actionHint)
        }

        val history = buildHistory()
        val messages = buildList {
            add("system" to systemPrompt)
            addAll(history)
        }

        // 3. 流式请求
        val reply = LlmClient.chatStream(config, messages) { delta ->
            onChunk(delta)
        }

        // 4. 落库
        if (reply.isNotBlank()) {
            chatDao.insert(
                ChatMessageEntity(role = ROLE_FISH, content = reply, kind = "CHAT")
            )
        }

        // 5. 抽取候选记忆（待用户确认，不直接进 prompt）
        memoryEngine.extractCandidates(userInput).forEach { candidate ->
            memoryEngine.rememberCandidate(candidate.content, candidate.kind)
        }

        return reply
    }

    /**
     * 触发一次主动行为
     *
     * 与 [chat] 的区别：
     * - 不需要用户输入
     * - 用 proactive prompt，要求它「随手发一条消息」而不是「回答」
     * - 行为名会记录进消息的 action 字段，方便复盘
     *
     * @param action 行为类型
     * @param extraHint 额外上下文（如刚读到的一段书）
     * @param motive 开口的由头，决定注入的动机文案
     * @return 生成的消息，若未触发或失败返回 null
     */
    suspend fun runProactive(
        action: ProactiveAction,
        extraHint: String = "",
        motive: ProactiveMotive? = null
    ): ChatMessageEntity? {
        val config = aiConfigStore.config.value
        if (!config.isValid || !config.enabled) return null

        val persona = getPersona()
        val world = buildWorldContext()
        val mood = FishMoodResolver.resolve(
            affinity = currentAffinity(),
            satiety = world.satiety,
            mood = world.mood,
            energy = world.energy,
            isSleeping = world.isSleeping
        )

        val hint = buildString {
            append(action.hint)
            appendLine()
            // 由头：让模型知道「为什么现在想说话」，避免输出通知腔
            motive?.let {
                appendLine(it.hint)
                appendLine()
            }
            append("【你现在的状态】")
            appendLine(mood.description)
            if (extraHint.isNotBlank()) {
                appendLine(extraHint)
            }
        }

        // 关键：触发文案必须以 user 角色追加，不能用 system/developer，
        // 否则会被端点静默丢弃，模型收不到「你要主动说话」的指令。
        val messages = buildList {
            add("system" to PersonaPromptBuilder.buildProactiveSystem(persona, worldContextProvider.describe(world)))
            addAll(buildHistory())
            add("user" to PersonaPromptBuilder.buildProactiveTrigger(
                hint,
                mood,
                motivation = persona.proactiveMotivation,
                motive = motive
            ))
        }

        return try {
            val reply = LlmClient.chatOnce(config, messages).trim()
            if (reply.isBlank()) {
                null
            } else {
                val entity = ChatMessageEntity(
                    role = ROLE_FISH,
                    content = reply,
                    kind = "PROACTIVE",
                    action = action.name
                )
                chatDao.insert(entity)
                // 触发后更新状态
                when (action) {
                    ProactiveAction.HUNGRY -> fishStateDao.addMood(-5)
                    ProactiveAction.DAYDREAM, ProactiveAction.WANDER -> fishStateDao.addMood(3)
                    ProactiveAction.NUDGE -> fishStateDao.addMood(-2)
                    ProactiveAction.SLEEP -> fishStateDao.addEnergy(-15)
                    else -> Unit
                }
                fishStateDao.markProactive()
                entity
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 发送一条系统事件消息（不请求 AI，仅作为上下文记录）
     *
     * 用于「专注结束」「投喂完成」「被拦截应用」这类事件：
     * 它们不产生气泡，但会在后续对话中成为大肥鱼的话题来源。
     */
    suspend fun pushEvent(content: String, kind: String) {
        chatDao.insert(
            ChatMessageEntity(role = ROLE_SYSTEM, content = content, kind = kind)
        )
    }

    /** 清空对话记录 */
    suspend fun clearChat() = chatDao.clearAll()

    /**
     * 构建历史消息列表
     *
     * SYSTEM 角色的消息会被转成 user 角色的上下文描述，
     * 因为 OpenAI 兼容端点通常不接受连续多条 system 消息。
     */
    private suspend fun buildHistory(): List<Pair<String, String>> {
        val recent = chatDao.getRecent(CONTEXT_WINDOW).reversed()
        return recent.mapNotNull { msg ->
            when (msg.role) {
                ROLE_USER -> "user" to msg.content
                ROLE_FISH -> "assistant" to msg.content
                // 系统事件转成 user 角色的「你知道的事」，让模型能顺着它接话
                ROLE_SYSTEM -> "user" to "（系统提示：${msg.content}）"
                else -> null
            }
        }
    }

    /**
     * 采集当前世界快照
     */
    suspend fun buildWorldContext(): WorldContext {
        val state = getState()
        val appUsage = worldContextProvider.collectAppUsage()
        val todayFocus = statsRepository.getTodayFocusDurationMillis()
        val currentFocus = FocusSessionState.accumulatedMillis

        return WorldContext(
            appUsage = appUsage,
            totalScreenTime = appUsage.sumOf { it.durationMillis },
            todayFocusMillis = todayFocus,
            currentFocusMillis = currentFocus,
            isFocusing = currentFocus > 0,
            satiety = state.satiety,
            mood = state.mood,
            energy = state.energy,
            affinity = currentAffinity(),
            isSleeping = state.isSleeping,
            readingBook = currentReadingBook(),
            readingPage = currentReadingPage()
        )
    }

    /** 当前好感度 */
    suspend fun currentAffinity(): Int {
        val fish = fishRepository.getUserFish() ?: return 0
        return fish.affinityLevel
    }

    private suspend fun currentReadingBook(): String? {
        return bookDao.getCurrentReading()?.title
    }

    private suspend fun currentReadingPage(): Int {
        return bookDao.getCurrentReading()?.currentPage ?: 0
    }
}
