package com.focusfish.app.pet

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 桌宠物理引擎
 *
 * 参数照搬 dsh-pet 电脑版（安卓移植版逐行对齐）：
 * - 弹簧 K=200、C=30（拖拽时的跟手迟滞感）
 * - 重力 1400
 * - 落地恢复系数 0.78
 * - 地面摩擦 2.5
 *
 * 为什么不用纯"贴手指"：
 * 硬贴会让桌宠像贴纸，没有重量感。弹簧跟随有迟滞，
 * 甩出去时会被"拽"一下再加速 —— 这点差别就是"活着"和"贴图"的差别。
 */
class PetPhysics(
    /** 屏幕宽（px） */
    var screenWidth: Int = 1080,
    /** 屏幕高（px） */
    var screenHeight: Int = 1920,
    /** 桌宠宽度（px） */
    var petWidth: Int = 200
) {

    companion object {
        /** 弹簧刚度 */
        const val SPRING_K = 200f

        /** 弹簧阻尼 */
        const val SPRING_C = 30f

        /** 重力加速度 */
        const val GRAVITY = 1400f

        /** 落地恢复系数 */
        const val RESTITUTION = 0.78f

        /** 地面摩擦 */
        const val GROUND_FRICTION = 2.5f

        /** 边缘恢复系数 */
        const val WALL_RESTITUTION = 0.5f

        /** 判定为"甩出"的速度阈值（px/s） */
        const val FLING_THRESHOLD = 1800f

        /** 静止判定：速度低于此值视为停下 */
        const val REST_SPEED = 30f
    }

    /** 当前位置（桌宠左上角） */
    var x = 0f
    var y = 0f

    /** 速度 */
    var vx = 0f
    var vy = 0f

    /** 是否正在被手指拖拽 */
    var dragging = false

    /** 拖拽目标位置 */
    var dragTargetX = 0f
    var dragTargetY = 0f

    /** 是否已经着地 */
    var grounded = true

    /** 是否处于「飞行中」（抛物线） */
    var flying = false

    /**
     * 切换到拖拽状态
     */
    fun beginDrag(touchX: Float, touchY: Float) {
        dragging = true
        flying = false
        // 记录手指相对桌宠的偏移，拖动时不会跳到手指中心
        dragOffsetX = touchX - x
        dragOffsetY = touchY - y
        dragTargetX = x
        dragTargetY = y
    }

    /** 拖拽偏移 */
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    /**
     * 更新拖拽目标
     */
    fun updateDrag(touchX: Float, touchY: Float) {
        dragTargetX = touchX - dragOffsetX
        dragTargetY = touchY - dragOffsetY
    }

    /**
     * 结束拖拽
     *
     * @return 甩出速度（px/s），用于决定是否进入抛物线
     */
    fun endDrag(velocityX: Float, velocityY: Float): Float {
        dragging = false
        val speed = abs(velocityX) + abs(velocityY)
        if (speed > FLING_THRESHOLD) {
            // 甩出去了 → 进入抛物线
            flying = true
            grounded = false
            vx = velocityX
            vy = velocityY
        } else {
            // 只是放下 → 保持在原地
            vx = 0f
            vy = 0f
            flying = false
            grounded = true
        }
        return speed
    }

    /**
     * 每帧推进物理
     *
     * @param dt 时间步长（秒）
     */
    fun step(dt: Float) {
        if (dragging) {
            // 弹簧跟随：向目标位置收敛
            val ax = (dragTargetX - x) * SPRING_K - vx * SPRING_C
            val ay = (dragTargetY - y) * SPRING_K - vy * SPRING_C
            vx += ax * dt
            vy += ay * dt
            x += vx * dt
            y += vy * dt
            clampToScreen()
            return
        }

        if (!flying) {
            // 静止：只做微小的呼吸式浮动由渲染层负责
            return
        }

        // 抛物线：重力 + 积分
        vy += GRAVITY * dt
        x += vx * dt
        y += vy * dt

        // 左右边缘反弹
        if (x < 0f) {
            x = 0f
            vx = -vx * WALL_RESTITUTION
        } else if (x > screenWidth - petWidth) {
            x = (screenWidth - petWidth).toFloat()
            vx = -vx * WALL_RESTITUTION
        }

        // 触底：着地
        val floor = (screenHeight - petHeight).toFloat()
        if (y >= floor) {
            y = floor
            if (abs(vy) > REST_SPEED) {
                vy = -vy * RESTITUTION
                // 落地后水平速度受摩擦衰减
                vx /= GROUND_FRICTION
                if (abs(vx) < REST_SPEED) vx = 0f
            } else {
                vy = 0f
                grounded = true
                flying = false
            }
        }
    }

    /** 桌宠高度（px），用于触底计算 */
    var petHeight: Int = 200

    /**
     * 限制在屏幕内
     */
    private fun clampToScreen() {
        x = x.coerceIn(0f, max(0f, (screenWidth - petWidth).toFloat()))
        y = y.coerceIn(0f, max(0f, (screenHeight - petHeight).toFloat()))
    }

    /**
     * 是否已经基本静止
     */
    fun isAtRest(): Boolean =
        !dragging && !flying && abs(vx) < REST_SPEED && abs(vy) < REST_SPEED

    /**
     * 贴到左边缘
     */
    fun snapToLeftEdge() {
        x = 0f
        vx = 0f
    }

    /**
     * 贴到右边缘
     */
    fun snapToRightEdge() {
        x = (screenWidth - petWidth).toFloat()
        vx = 0f
    }

    /**
     * 判断是否贴在某个边缘（用于"贴边躲藏"）
     */
    fun isNearEdge(threshold: Int = 24): Boolean {
        return x <= threshold || x >= screenWidth - petWidth - threshold
    }

    /**
     * 朝向：1 表示朝右，-1 表示朝左
     */
    var facing = 1
}
