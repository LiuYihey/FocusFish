package com.focusfish.app.ai

import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusfish.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 世界感知快照
 *
 * 大肥鱼「知道」的现实信息打包成一段自然语言，塞进 prompt。
 * 它知道自己今天用了哪些 App、用了多久、专注了多久、现在什么状态。
 */
data class WorldContext(
    /** 今日各 App 使用时长（毫秒），已按时间降序 */
    val appUsage: List<AppUsage> = emptyList(),
    /** 今日总使用时长（毫秒） */
    val totalScreenTime: Long = 0,
    /** 今日专注总时长（毫秒） */
    val todayFocusMillis: Long = 0,
    /** 本次专注时长（毫秒），未在专注中为 0 */
    val currentFocusMillis: Long = 0,
    /** 当前是否在专注 */
    val isFocusing: Boolean = false,
    /** 饱腹度 0~100 */
    val satiety: Int = 70,
    /** 心情 -100~100 */
    val mood: Int = 0,
    /** 精力 0~100 */
    val energy: Int = 80,
    /** 好感度 */
    val affinity: Int = 0,
    /** 是否在睡觉 */
    val isSleeping: Boolean = false,
    /** 当前正在读的书名 */
    val readingBook: String? = null,
    /** 读到第几页 */
    val readingPage: Int = 0
) {
    /** 是否几乎没有任何世界信息（避免把空快照塞给模型） */
    val isEmpty: Boolean
        get() = appUsage.isEmpty() && todayFocusMillis == 0L && totalScreenTime == 0L
}

/**
 * 单个 App 的使用记录
 */
data class AppUsage(
    val packageName: String,
    val appName: String,
    val durationMillis: Long
)

/**
 * 世界感知提供器
 *
 * 负责把 UsageStats（今天用了什么）+ Room（专注时长）+ FishState（状态）
 * 汇总成 [WorldContext]。
 */
class WorldContextProvider(
    private val context: Context
) {

    /**
     * 采集今日 App 使用情况
     */
    suspend fun collectAppUsage(): List<AppUsage> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.checkUsageStatsPermission(context)) return@withContext emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as UsageStatsManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val beginTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = runCatching {
            usageStatsManager.queryAndAggregateUsageStats(beginTime, endTime)
        }.getOrNull() ?: return@withContext emptyList()

        val pm = context.packageManager
        stats.values
            .filter { it.totalTimeInForeground > 30_000L } // 过滤掉误触级别的碎片
            .sortedByDescending { it.totalTimeInForeground }
            .take(8)
            .map { stat ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                }.getOrDefault(stat.packageName)
                AppUsage(
                    packageName = stat.packageName,
                    appName = label,
                    durationMillis = stat.totalTimeInForeground
                )
            }
    }

    /**
     * 把世界快照转成给模型看的自然语言
     *
     * 刻意写成「你亲眼看到的事实」而不是「数据报表」，
     * 这样模型更容易以角色口吻说出来，而不是复述统计。
     */
    fun describe(world: WorldContext): String {
        val lines = mutableListOf<String>()

        if (world.isFocusing) {
            val mins = TimeUnit.MILLISECONDS.toMinutes(world.currentFocusMillis)
            lines += "用户此刻正在专注，已经持续了 $mins 分钟。"
        } else if (world.todayFocusMillis > 0) {
            val mins = TimeUnit.MILLISECONDS.toMinutes(world.todayFocusMillis)
            lines += "用户今天已经专注了 $mins 分钟。"
        }

        if (world.appUsage.isNotEmpty()) {
            val top = world.appUsage.take(3).joinToString("、") {
                "${it.appName} ${TimeUnit.MILLISECONDS.toMinutes(it.durationMillis)} 分钟"
            }
            lines += "用户今天用得最多的是：$top。"
        }

        if (!world.readingBook.isNullOrBlank()) {
            lines += "你正在读《${world.readingBook}》，读到第 ${world.readingPage} 页。"
        }

        // 状态描述：让反应分档对模型可见
        val stateWord = when {
            world.satiety < 25 -> "你有点饿了，一直惦记着大白饭"
            world.satiety > 85 -> "你刚吃饱，懒洋洋的，特别满足"
            else -> "你肚子不饿不饱"
        }
        val moodWord = when {
            world.mood > 30 -> "你心情很好"
            world.mood < -30 -> "你有点闷闷不乐"
            else -> "你情绪平平"
        }
        lines += "$stateWord，$moodWord，好感度 ${world.affinity}。"

        if (world.isSleeping) {
            lines += "你刚刚睡醒，还有点迷糊。"
        }

        return lines.joinToString("\n")
    }
}
