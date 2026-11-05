package com.focusfish.app.ai

import android.content.Context
import android.util.Log
import com.focusfish.app.data.local.dao.ChatDao
import com.focusfish.app.data.local.dao.FishMemoryDao
import com.focusfish.app.data.local.entity.ChatMessageEntity
import com.focusfish.app.data.local.entity.FishMemoryEntity
import com.focusfish.app.reading.ReadingEngine
import com.focusfish.app.util.FocusSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主动行为调度器
 *
 * 在应用进程存活期间跑一个循环，定期问引擎「要不要开口」。
 * 掷骰判定完全本地，命中了才调模型 —— 省电省额度。
 *
 * 调度节奏（对齐 dsh-heartbeat 的「60 分钟一跳 + ±20% 抖动」）：
 * 固定间隔容易被用户摸出规律，所以每次检查间隔会随机 ±20%。
 */
@Singleton
class ProactiveScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ProactiveEngine,
    private val fishBrain: FishBrain,
    private val chatDao: ChatDao,
    private val readingEngine: ReadingEngine,
    private val fishStateEngine: FishStateEngine,
    private val aiConfigStore: AiConfigStore,
    private val memoryDao: FishMemoryDao
) {

    companion object {
        private const val TAG = "ProactiveScheduler"

        /** 调度循环的间隔（毫秒），实际会带 ±20% 抖动 */
        private const val TICK_INTERVAL_MS = 10 * 60 * 1000L

        /** 抖动比例 */
        private const val JITTER_RATIO = 0.2

        /** 调度器 Job 的持有者 key */
        private const val JOB_KEY = "focus_fish_proactive_job"
    }

    /** 当前调度 Job */
    private var job: Job? = null

    /** 连续没掷中的次数（用于命中线加成） */
    private var missStreak = 0

    /** 上次检查时间 */
    private var lastCheckAt = 0L

    /** 上次真正开口时间 */
    private var lastProactiveAt = 0L

    /** 已触发的动作记录（用于冷却判定） */
    private val recentActions = mutableListOf<Pair<ProactiveAction, Long>>()

    /** 今日已开口次数 */
    private var todayProactiveCount = 0

    /** 今日计数所属的日期 */
    private var todayKey: String = ""

    /**
     * 启动调度
     */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            Log.i(TAG, "主动行为调度器已启动")
            while (isActive) {
                val interval = jitter(TICK_INTERVAL_MS)
                delay(interval)
                try {
                    tick()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "主动行为检查异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 停止调度
     */
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * 执行一次检查
     */
    suspend fun tick() {
        val now = System.currentTimeMillis()

        // 跨天重置
        val currentDay = dayKey(now)
        if (currentDay != todayKey) {
            todayKey = currentDay
            todayProactiveCount = 0
        }

        // 状态衰减
        val state = fishStateEngine.tick()

        // 没有配置 API 就不做无谓的检查
        val config = aiConfigStore.config.value
        if (!config.isValid || !config.enabled) return

        // 睡眠判定：长时间没互动且精力低 → 睡觉
        if (fishStateEngine.shouldSleep()) {
            fishStateEngine.sleep()
            fishBrain.pushEvent("大肥鱼睡着了。", "SLEEP")
            return
        }

        // 若之前在睡，现在醒了
        if (state.isSleeping && !fishStateEngine.shouldSleep()) {
            val slept = fishStateEngine.wake()
            val minutes = slept / 60000
            if (minutes >= 5) {
                fishBrain.pushEvent("大肥鱼睡了 $minutes 分钟，刚醒。", "WAKE")
            }
        }

        // 清理过期动作记录
        recentActions.removeAll { now - it.second > 60 * 60 * 1000L }

        val currentBook = readingEngine.currentBook()

        // 待确认的记忆：有一条就说明对方刚说过什么值得记住的事，
        // 大肥鱼可以顺口提一句「诶你上次说的那个…」
        val pendingMemory = memoryDao.countByStatus(FishMemoryEntity.STATUS_CANDIDATE) > 0

        val decision = engine.decide(
            now = now,
            lastCheckAt = lastCheckAt,
            lastProactiveAt = lastProactiveAt,
            missStreak = missStreak,
            recentActions = recentActions,
            intensity = 40,
            todayProactiveCount = todayProactiveCount,
            isFocusing = FocusSessionState.isActive,
            isSleeping = state.isSleeping,
            isHungry = state.satiety < 25,
            hasReadingBook = currentBook != null,
            hasPendingMemory = pendingMemory
        )

        lastCheckAt = now

        if (!decision.triggered) {
            missStreak++
            Log.d(TAG, "不开口：${decision.reason}")
            return
        }

        val action = decision.action ?: return
        missStreak = 0

        // 专注时如果是「汇报阅读」，先去读一页，再让大肥鱼说
        var extraHint = ""
        if (action == ProactiveAction.READING) {
            val page = readingEngine.readNextPage()
            if (page != null) {
                extraHint = "你刚读了《${page.bookTitle}》第 ${page.pageNumber} 页，内容是：\n${page.content.take(600)}"
            }
        }

        val motive = decision.motive.takeIf { it.isNotBlank() }
            ?.let { name -> ProactiveMotive.entries.firstOrNull { it.name == name } }

        val message = fishBrain.runProactive(action, extraHint, motive)
        if (message != null) {
            lastProactiveAt = now
            todayProactiveCount++
            recentActions.add(action to now)
            Log.i(TAG, "主动开口：${action.name}（${decision.reason}）")
        }
    }

    /**
     * 间隔抖动：实际间隔 = 基准 × (1 ± 20%)
     */
    private fun jitter(base: Long): Long {
        val factor = 1.0 + (Math.random() * 2 - 1) * JITTER_RATIO
        return (base * factor).toLong()
    }

    private fun dayKey(time: Long): String {
        val c = Calendar.getInstance().apply { this.timeInMillis = time }
        return String.format(
            "%04d%02d%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }
}
