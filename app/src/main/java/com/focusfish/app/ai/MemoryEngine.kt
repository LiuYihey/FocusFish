package com.focusfish.app.ai

import com.focusfish.app.data.local.dao.FishMemoryDao
import com.focusfish.app.data.local.entity.FishMemoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * 大肥鱼的记忆引擎
 *
 * 借鉴 dsh-fish-memory 的实测参数（省 token 又保"她记得"）：
 * - 普通记忆半衰期 30 天，每轮最多注入 6 条 / 800 字
 * - 长期事实每轮固定带 2 条
 *
 * 借鉴 dsh-meow-memory 的分层：关系记忆（关于用户）与任务记忆（做过的事）
 * 分开存、分开衰减 —— 压缩上下文后不会出现"新魂旧壳"。
 *
 * 关键设计：**候选态**。
 * 大肥鱼从对话里捕捉到"可能要记住的事"时，先存成 CANDIDATE，
 * 用户在记忆页点头确认后才进 CONFIRMED，才会参与对话注入。
 * 理由（陪伴产品的公认教训）：假装记住了比没记住更伤。
 */
@Singleton
class MemoryEngine @Inject constructor(
    private val memoryDao: FishMemoryDao
) {

    companion object {
        /** 每轮最多注入的记忆条数 */
        const val MAX_INJECT = 6

        /** 每轮最多注入的记忆总字数 */
        const val MAX_CHARS = 800

        /** 每轮固定注入的长期事实条数 */
        const val PINNED_COUNT = 2

        /** 低于此权重的记忆不再注入（衰减到底了） */
        const val MIN_WEIGHT = 0.05f

        /** 一天毫秒数 */
        private const val DAY_MS = 24 * 60 * 60 * 1000.0

        /** 关系记忆关键词（用于从用户输入里抽事实） */
        private val RELATION_PATTERNS = listOf(
            "我叫", "我的名字", "叫我", "我住在", "我在", "我最近",
            "我喜欢", "我讨厌", "我不喜欢", "我怕", "我每天", "我通常",
            "我习惯", "我生日", "我工作是", "我学", "我在做", "我准备"
        )

        /** 任务记忆关键词 */
        private val TASK_PATTERNS = listOf(
            "昨天", "今天要", "明天要", "这周", "下周", "截止", "deadline",
            "考试", "面试", "作业", "论文", "项目", "汇报"
        )
    }

    /**
     * 抽取候选记忆
     *
     * 纯本地规则，不调模型 —— 每次对话多一次模型调用不值当，
     * 而且规则抽出来的东西更可控。
     *
     * @param userInput 用户刚说的那句话
     * @return 候选记忆（未确认，不参与注入）
     */
    fun extractCandidates(userInput: String): List<FishMemoryEntity> {
        val text = userInput.trim()
        if (text.length < 4) return emptyList()

        val kind = when {
            RELATION_PATTERNS.any { text.contains(it) } -> FishMemoryEntity.KIND_RELATION
            TASK_PATTERNS.any { text.contains(it) } -> FishMemoryEntity.KIND_TASK
            else -> return emptyList()
        }

        // 太长不像"一件事"，太短没信息量
        if (text.length > 120) return emptyList()

        return listOf(
            FishMemoryEntity(
                kind = kind,
                content = text,
                status = FishMemoryEntity.STATUS_CANDIDATE
            )
        )
    }

    /** 存一条候选记忆 */
    suspend fun rememberCandidate(content: String, kind: String) {
        if (content.isBlank()) return
        memoryDao.insert(
            FishMemoryEntity(
                kind = kind,
                content = content.trim(),
                status = FishMemoryEntity.STATUS_CANDIDATE
            )
        )
    }

    /** 用户确认了一条记忆 */
    suspend fun confirm(id: Long) = memoryDao.setStatus(id, FishMemoryEntity.STATUS_CONFIRMED)

    /** 用户拒绝了一条记忆 */
    suspend fun dismiss(id: Long) = memoryDao.deleteById(id)

    /** 置顶/取消置顶（长期事实） */
    suspend fun togglePinned(memory: FishMemoryEntity) {
        memoryDao.update(memory.copy(isPinned = !memory.isPinned))
    }

    /** 观察全部记忆 */
    fun observeAll() = memoryDao.observeAll()

    /** 观察候选记忆 */
    fun observeCandidates() = memoryDao.observeByStatus(FishMemoryEntity.STATUS_CANDIDATE)

    /** 清空记忆 */
    suspend fun clearAll() = memoryDao.clearAll()

    /**
     * 选出本轮要注入的记忆
     *
     * 权重 = 半衰期衰减 × 复述次数加成 × 关键词相关度
     * 半衰期越长、复述越多、与当前话题越相关的记忆，越可能被想起。
     */
    suspend fun selectForInjection(context: String): List<FishMemoryEntity> {
        val confirmed = memoryDao.getByStatus(FishMemoryEntity.STATUS_CONFIRMED)
        if (confirmed.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val scored = confirmed.map { memory ->
            memory to score(memory, context, now)
        }.filter { it.second >= MIN_WEIGHT }
            .sortedByDescending { it.second }

        // 长期事实固定带 2 条（不受排序影响）
        val pinned = confirmed.filter { it.isPinned }.take(PINNED_COUNT)
        val pinnedIds = pinned.map { it.id }.toSet()

        val picked = LinkedHashSet<FishMemoryEntity>()
        var chars = 0
        for (memory in scored) {
            if (picked.size >= MAX_INJECT) break
            if (memory.first.id in pinnedIds) continue
            val len = memory.first.content.length
            if (chars + len > MAX_CHARS && picked.isNotEmpty()) continue
            picked.add(memory.first)
            chars += len
        }
        pinned.forEach { picked.add(it) }

        val result = picked.toList()
        if (result.isNotEmpty()) {
            memoryDao.markRecalled(result.map { it.id }, now)
        }
        return result
    }

    /**
     * 计算记忆权重
     *
     * @param memory 记忆
     * @param context 当前上下文（用于关键词相关度）
     * @param now 当前时间
     */
    private fun score(memory: FishMemoryEntity, context: String, now: Long): Float {
        // 半衰期衰减
        val halfLifeDays = if (memory.kind == FishMemoryEntity.KIND_RELATION) {
            FishMemoryEntity.HALF_LIFE_DAYS_RELATION
        } else {
            FishMemoryEntity.HALF_LIFE_DAYS_TASK
        }
        val days = (now - memory.lastRecalledAt) / DAY_MS
        val decay = 0.5.pow(days / halfLifeDays)

        // 复述次数加成：被提起过多次的记忆更牢固（封顶 2 倍）
        val recallBoost = (1.0 + memory.recallCount * 0.15).coerceAtMost(2.0)

        // 置顶：直接给满分权重
        if (memory.isPinned) return 1.0f

        // 关键词相关度：内容里的词出现在上下文里就加分
        val relevance = if (context.isBlank()) {
            0.5f
        } else {
            val tokens = memory.content.split(' ', '，', '。', '、', '！', '？', ',', '.')
                .filter { it.length >= 2 }
            if (tokens.isEmpty()) {
                0.5f
            } else {
                val hits = tokens.count { context.contains(it) }
                (0.5f + hits * 0.2f).coerceAtMost(1.5f)
            }
        }

        return (decay * recallBoost * relevance).toFloat()
    }

    /**
     * 把选中的记忆渲染成 prompt 片段
     */
    fun renderForPrompt(memories: List<FishMemoryEntity>): String {
        if (memories.isEmpty()) return ""
        return buildString {
            appendLine("【你记得的事】")
            memories.forEach { memory ->
                val prefix = if (memory.kind == FishMemoryEntity.KIND_RELATION) "关于他的" else "你们之间"
                appendLine("- $prefix：${memory.content}")
            }
            appendLine("这些是你真的记得的事，可以自然地提一句，但一次最多提一件，别念档案。")
            appendLine("如果某件事你记不清了，就说「诶我好像忘了…你再说一遍？」——不要编。")
        }
    }
}
