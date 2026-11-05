package com.focusfish.app.ai

import com.focusfish.app.data.local.dao.FishStateDao
import com.focusfish.app.data.local.entity.FishStateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大肥鱼状态引擎
 *
 * 负责饱腹度随时间衰减、精力恢复、睡眠判定。
 * 这是「它是个活物」的基础：你不理它，它会饿。
 */
@Singleton
class FishStateEngine @Inject constructor(
    private val fishStateDao: FishStateDao
) {

    companion object {
        /** 饱腹度每小时衰减多少点（约 4 天从满到空） */
        const val SATIETY_DECAY_PER_HOUR = 1

        /** 精力每小时恢复多少点（睡觉时翻倍） */
        const val ENERGY_RECOVER_PER_HOUR = 2

        /** 连续无互动多少小时后开始睡觉 */
        const val SLEEP_AFTER_IDLE_HOURS = 2

        /** 每次喂食增加的饱腹度 */
        const val SATIETY_PER_FEED = 15

        /** 大白饭额外增加的饱腹度（最爱的东西，多一点） */
        const val SATIETY_PER_BIG_RICE = 25
    }

    /**
     * 刷新状态：按经过的时间衰减饱腹度、恢复精力
     */
    suspend fun tick(): FishStateEntity {
        val state = fishStateDao.get() ?: FishStateEntity().also { fishStateDao.upsert(it) }
        val now = System.currentTimeMillis()
        val elapsedHours = (now - state.updatedAt).coerceAtLeast(0) / 3_600_000.0

        if (elapsedHours < 0.01) return state

        val satietyDecay = (SATIETY_DECAY_PER_HOUR * elapsedHours).toInt()
        val energyRecover = (ENERGY_RECOVER_PER_HOUR * elapsedHours).toInt()

        val updated = state.copy(
            satiety = (state.satiety - satietyDecay).coerceIn(0, 100),
            energy = (state.energy + energyRecover).coerceIn(0, 100),
            updatedAt = now
        )
        fishStateDao.upsert(updated)
        return updated
    }

    /**
     * 喂食：增加饱腹度与心情
     *
     * @param isBigRice 是否是大白饭（大肥鱼最爱，额外加成）
     * @param affinityBonus 好感度加成，同时影响心情
     */
    suspend fun onFed(isBigRice: Boolean, affinityBonus: Int) {
        val state = fishStateDao.get() ?: FishStateEntity().also { fishStateDao.upsert(it) }
        val satietyGain = if (isBigRice) SATIETY_PER_BIG_RICE else SATIETY_PER_FEED
        val updated = state.copy(
            satiety = (state.satiety + satietyGain).coerceIn(0, 100),
            // 好感度加成越高，心情涨得越多：喂食本身是开心的事
            mood = (state.mood + (affinityBonus * 2)).coerceIn(-100, 100),
            updatedAt = System.currentTimeMillis()
        )
        fishStateDao.upsert(updated)
    }

    /**
     * 判断是否该睡觉
     *
     * 条件：超过 [SLEEP_AFTER_IDLE_HOURS] 小时没互动，且精力较低
     */
    suspend fun shouldSleep(): Boolean {
        val state = tick()
        if (state.isSleeping) return true
        val idleMillis = System.currentTimeMillis() - state.updatedAt
        return idleMillis > SLEEP_AFTER_IDLE_HOURS * 3_600_000L && state.energy < 60
    }

    /**
     * 入睡 */
    suspend fun sleep() {
        fishStateDao.markSleeping()
    }

    /**
     * 醒来：累计睡眠时长
     */
    suspend fun wake(): Long {
        val state = fishStateDao.get() ?: return 0L
        if (!state.isSleeping) return 0L
        val slept = System.currentTimeMillis() - state.sleepStartedAt
        fishStateDao.markAwake(slept.coerceAtLeast(0L))
        return slept.coerceAtLeast(0L)
    }

    /**
     * 事件引起的心情变化
     */
    suspend fun onEvent(delta: Int) {
        fishStateDao.addMood(delta)
    }
}
