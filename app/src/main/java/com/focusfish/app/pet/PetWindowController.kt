package com.focusfish.app.pet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.focusfish.app.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 桌宠悬浮窗控制器
 *
 * 负责：
 * - 创建/销毁 WindowManager 悬浮窗
 * - 渲染桌宠本体 + 台词气泡
 * - 处理手势：点击 / 双击 / 长按 / 拖拽 / 甩出
 * - 驱动物理引擎（弹簧跟手、抛物线、边缘反弹）
 * - 状态机：待机 → 散步 → 贴边 → 睡觉 → 拖拽 → 飞行
 *
 * 关键系统约束（踩过的坑）：
 * 1. 必须 `FLAG_NOT_FOCUSABLE`，否则会抢走输入法的键盘焦点
 * 2. Android 8.0+ 后台不能直接显示悬浮窗，必须挂在前台服务上
 * 3. Android 12+ 后台启动前台服务会抛 `ForegroundServiceStartNotAllowedException`，
 *    所以服务必须在用户可见时启动（见 DesktopPetService）
 */
class PetWindowController(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        /** 用户点击了桌宠（打开聊天） */
        fun onPetClicked()

        /** 用户双击了桌宠（投喂） */
        fun onPetDoubleClicked()

        /** 用户长按了桌宠（菜单） */
        fun onPetLongPressed()

        /** 用户拖动结束 */
        fun onPetDragged(x: Int, y: Int)

        /** 用户甩出了桌宠 */
        fun onPetFlung()

        /** 桌宠走到了屏幕边缘 */
        fun onPetReachedEdge(edge: Edge)

        /** 需要显示一条台词 */
        fun onPetSpeakRequest(): String?

        /** 桌宠状态变化 */
        fun onPetStateChanged(state: PetState)
    }

    /** 桌宠状态 */
    enum class PetState {
        /** 待机 */
        IDLE,

        /** 散步中 */
        WALKING,

        /** 贴边躲藏 */
        HIDDEN,

        /** 睡觉 */
        SLEEPING,

        /** 被拖拽 */
        DRAGGING,

        /** 飞行中 */
        FLYING
    }

    /** 屏幕边缘 */
    enum class Edge { LEFT, RIGHT }

    companion object {
        /** 默认桌宠宽度（dp） */
        const val DEFAULT_SIZE_DP = 160

        /** 最小桌宠尺寸（dp） */
        const val MIN_SIZE_DP = 80

        /** 最大桌宠尺寸（dp） */
        const val MAX_SIZE_DP = 280

        /** 右上角默认位置：距顶 */
        const val DEFAULT_TOP_OFFSET_DP = 120

        /** 右上角默认位置：距右 */
        const val DEFAULT_RIGHT_OFFSET_DP = 12

        /** 贴边后静置多久缩进去（ms） */
        const val EDGE_HIDE_DELAY_MS = 12_000L

        /** 行走速度（px/s） */
        const val WALK_SPEED = 120f

        /** 两次散步之间的最小间隔（ms） */
        const val WALK_COOLDOWN_MS = 30_000L

        /** 气泡显示时长（ms） */
        const val BUBBLE_DURATION_MS = 10_000L

        /** 双击判定间隔（ms） */
        const val DOUBLE_CLICK_MS = 280L

        /** 长按判定时间（ms） */
        const val LONG_PRESS_MS = 550L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null
    private var petView: PetSpriteView? = null
    private var bubbleView: PetBubbleView? = null

    /** 物理引擎 */
    private val physics = PetPhysics()

    /** 当前状态 */
    var state: PetState = PetState.IDLE
        private set

    /** 是否已添加到窗口 */
    val isAttached: Boolean get() = rootView != null

    /** 桌宠尺寸（px） */
    private var petSizePx = 0

    /** 手势检测 */
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastTouchTime = 0L
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var longPressTriggered = false

    /** 贴边计时 */
    private var edgeIdleSince = 0L

    /** 气泡隐藏计时 */
    private var bubbleHideAt = 0L

    /** 随机数 */
    private val random = java.util.Random()

    /**
     * 显示桌宠
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(sizeDp: Int = DEFAULT_SIZE_DP, savedX: Int? = null, savedY: Int? = null) {
        if (rootView != null) return

        val metrics = context.resources.displayMetrics
        val sizePx = (sizeDp * metrics.density).roundToInt().coerceAtLeast(1)
        petSizePx = sizePx

        val layout = LayoutInflater.from(context).inflate(R.layout.pet_overlay, null, false)
        rootView = layout as FrameLayout
        petView = layout.findViewById(R.id.petSprite)
        bubbleView = layout.findViewById(R.id.petBubble)

        physics.screenWidth = metrics.widthPixels
        physics.screenHeight = metrics.heightPixels
        physics.petWidth = sizePx
        physics.petHeight = sizePx

        // 位置：优先用保存的，否则默认右上角
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = savedX ?: (metrics.widthPixels - sizePx - (DEFAULT_RIGHT_OFFSET_DP * metrics.density).roundToInt())
        params.y = savedY ?: (DEFAULT_TOP_OFFSET_DP * metrics.density).roundToInt()

        physics.x = params.x.toFloat()
        physics.y = params.y.toFloat()

        setupTouch(layout)

        try {
            windowManager.addView(layout, params)
        } catch (e: Exception) {
            rootView = null
            petView = null
            bubbleView = null
            throw e
        }

        state = PetState.IDLE
        listener.onPetStateChanged(state)
    }

    /**
     * 隐藏桌宠
     */
    fun hide() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // 已经不在窗口里了，忽略
        }
        rootView = null
        petView = null
        bubbleView = null
        state = PetState.IDLE
    }

    /**
     * 悬浮窗类型
     *
     * Android 8.0+ 必须用 TYPE_APPLICATION_OVERLAY，
     * 旧的 TYPE_PHONE 已被废弃且在新版本上无法显示。
     */
    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    /**
     * 设置手势
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    lastTouchTime = System.currentTimeMillis()
                    velocityX = 0f
                    velocityY = 0f
                    isDragging = false
                    longPressTriggered = false

                    // 长按检测：用一个可取消的 Runnable，
                    // 松手时必须把它撤掉，否则会在松手后又触发一次
                    v.removeCallbacks(longPressRunnable)
                    v.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                        // 一旦开始拖动就取消长按检测
                        v.removeCallbacks(longPressRunnable)
                        if (state == PetState.HIDDEN) {
                            // 从贴边状态探出来
                            restorePetVisibility()
                            setState(PetState.IDLE)
                        }
                        physics.beginDrag(event.rawX, event.rawY)
                        setState(PetState.DRAGGING)
                    }
                    if (isDragging) {
                        physics.updateDrag(event.rawX, event.rawY)
                        // 朝向：跟着手指方向
                        val delta = event.rawX - lastTouchX
                        if (abs(delta) > 2f) {
                            physics.facing = if (delta > 0) 1 else -1
                            petView?.facing = physics.facing
                        }
                        velocityX = event.rawX - lastTouchX
                        velocityY = event.rawY - lastTouchY
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        updatePosition()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    if (isDragging) {
                        physics.endDrag(velocityX * 60f, velocityY * 60f)
                        if (physics.flying) {
                            setState(PetState.FLYING)
                            listener.onPetFlung()
                        } else {
                            setState(PetState.IDLE)
                        }
                        listener.onPetDragged(physics.x.roundToInt(), physics.y.roundToInt())
                    } else if (!longPressTriggered) {
                        // 点击 / 双击
                        val now = System.currentTimeMillis()
                        val isDouble = lastClickTime > 0 && now - lastClickTime < DOUBLE_CLICK_MS
                        if (isDouble) {
                            lastClickTime = 0
                            listener.onPetDoubleClicked()
                        } else {
                            lastClickTime = now
                            // 延迟一点再响应单击，避免与双击冲突
                            v.postDelayed({
                                if (lastClickTime == now) {
                                    petView?.playSquash()
                                    listener.onPetClicked()
                                }
                            }, DOUBLE_CLICK_MS)
                        }
                    }
                    lastTouchTime = System.currentTimeMillis()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 长按检测 Runnable
     */
    private val longPressRunnable = Runnable {
        if (!isDragging && !longPressTriggered) {
            longPressTriggered = true
            listener.onPetLongPressed()
        }
    }

    /**
     * 恢复桌宠本体的完整尺寸（从贴边缩进状态探出）
     */
    private fun restorePetVisibility() {
        petView?.scaleX = 1f
        petView?.scaleY = 1f
        petView?.alpha = 1f
    }

    /** 上次点击时间，用于双击判定 */
    private var lastClickTime = 0L

    /**
     * 切换状态
     */
    private fun setState(newState: PetState) {
        if (state == newState) return
        state = newState
        when (newState) {
            PetState.IDLE -> {
                petView?.stopWalking()
                petView?.sleeping = false
                edgeIdleSince = 0L
            }
            PetState.WALKING -> {
                petView?.startWalking()
                edgeIdleSince = 0L
            }
            PetState.HIDDEN -> {
                petView?.stopWalking()
                edgeIdleSince = System.currentTimeMillis()
            }
            PetState.SLEEPING -> {
                petView?.stopWalking()
                petView?.sleeping = true
            }
            PetState.DRAGGING, PetState.FLYING -> {
                petView?.stopWalking()
                edgeIdleSince = 0L
            }
        }
        listener.onPetStateChanged(state)
    }

    /**
     * 每帧推进（由 Service 的渲染循环调用）
     */
    fun tick(deltaMs: Long) {
        val dt = (deltaMs / 1000f).coerceAtMost(0.05f)
        val now = System.currentTimeMillis()

        physics.step(dt)

        // 落地后如果不在边缘，进入待机
        if (state == PetState.FLYING && physics.isAtRest()) {
            setState(PetState.IDLE)
        }

        // 待机时的随机散步
        if (state == PetState.IDLE) {
            maybeStartWalking(now)
        }

        // 行走时撞到边缘 → 掉头
        if (state == PetState.WALKING) {
            // 走够了就停下（不能一直走）
            if (now > walkUntil) {
                setState(PetState.IDLE)
            }
            val hitLeft = physics.x <= 1f
            val hitRight = physics.x >= physics.screenWidth - physics.petWidth - 1f
            if (hitLeft || hitRight) {
                physics.facing = if (hitLeft) 1 else -1
                petView?.facing = physics.facing
                listener.onPetReachedEdge(if (hitLeft) Edge.LEFT else Edge.RIGHT)
                // 撞边后有可能进入贴边躲藏
                if (random.nextInt(100) < 40) {
                    setState(PetState.HIDDEN)
                }
            }
        }

        // 贴边静置超时 → 缩进去
        if (state == PetState.HIDDEN && edgeIdleSince > 0) {
            if (now - edgeIdleSince > EDGE_HIDE_DELAY_MS) {
                // 缩进去：只露半个身子（缩小 + 靠边）
                petView?.scaleX = 0.45f
                petView?.scaleY = 0.45f
                petView?.alpha = 0.75f
                rootView?.let { rv ->
                    val lp = rv.layoutParams as? WindowManager.LayoutParams
                    lp?.let {
                        it.x = if (physics.x <= 1f) 0 else physics.screenWidth - physics.petWidth
                        windowManager.updateViewLayout(rv, it)
                    }
                }
            }
        }

        // 气泡自动隐藏
        if (bubbleHideAt > 0 && now > bubbleHideAt) {
            hideBubble()
        }

        updatePosition()
    }

    /**
     * 随机决定是否开始散步
     *
     * 注意：这是**按时间**判定，不是每帧掷骰。
     * 每帧 3% 的话，30fps 下等于「立刻就开始走」——
     * 桌宠会一直在动，那就不是陪伴是噪音了。
     */
    private fun maybeStartWalking(now: Long) {
        if (physics.isNearEdge()) return
        // 两次散步之间至少隔 30 秒
        if (now < nextWalkCheckAt) return
        nextWalkCheckAt = now + WALK_COOLDOWN_MS
        // 30% 概率开始走
        if (random.nextInt(100) < 30) {
            setState(PetState.WALKING)
            physics.facing = if (random.nextBoolean()) 1 else -1
            petView?.facing = physics.facing
            // 走 3~12 秒
            walkUntil = now + 3_000L + random.nextInt(9_000)
        }
    }

    /** 下次允许开始散步的时间 */
    private var nextWalkCheckAt = 0L

    /** 本次散步结束时间 */
    private var walkUntil = 0L

    /**
     * 行走时推进位置
     */
    private fun advanceWalking(dt: Float) {
        physics.x += physics.facing * WALK_SPEED * dt
    }

    /**
     * 更新悬浮窗位置
     */
    private fun updatePosition() {
        val rv = rootView ?: return
        val lp = rv.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = physics.x.roundToInt()
        lp.y = physics.y.roundToInt()
        try {
            windowManager.updateViewLayout(rv, lp)
        } catch (e: Exception) {
            // 窗口已被移除
        }
    }

    /**
     * 显示一条台词
     */
    fun showBubble(text: String) {
        val bubble = bubbleView ?: return
        bubble.setText(text)
        bubble.visibility = View.VISIBLE
        bubble.alpha = 0f
        bubble.animate().alpha(1f).setDuration(180).start()
        bubbleHideAt = System.currentTimeMillis() + BUBBLE_DURATION_MS

        // 用 translationY 把气泡浮到本体上方 —— 不能改布局，
        // 否则窗口变高会让桌宠在屏幕上往下跳。
        bubble.post {
            val offset = -(bubble.height + (petSizePx * 0.04f).toInt())
            bubble.translationY = offset.toFloat()
        }
    }

    /**
     * 隐藏气泡
     */
    fun hideBubble() {
        val bubble = bubbleView ?: return
        bubble.animate().alpha(0f).setDuration(220).withEndAction {
            bubble.visibility = View.GONE
        }.start()
        bubbleHideAt = 0L
    }

    /**
     * 切换姿态
     */
    fun setPose(pose: PetSpriteLibrary.Pose) {
        petView?.pose = pose
    }

    /**
     * 睡觉 / 醒来
     */
    fun setSleeping(sleeping: Boolean) {
        setState(if (sleeping) PetState.SLEEPING else PetState.IDLE)
        petView?.pose = if (sleeping) PetSpriteLibrary.Pose.SLEEPING else PetSpriteLibrary.Pose.IDLE
    }

    /**
     * 隐藏/显示桌宠本体（保留悬浮窗，用于临时隐藏）
     */
    fun setPetVisible(visible: Boolean) {
        petView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * 拖到某个位置（设置页用）
     */
    fun moveTo(x: Int, y: Int) {
        physics.x = x.toFloat()
        physics.y = y.toFloat()
        updatePosition()
    }

    /**
     * 翻转朝向
     */
    fun flipFacing() {
        physics.facing = -physics.facing
        petView?.facing = physics.facing
    }

    /**
     * 当前坐标
     */
    fun currentPosition(): Pair<Int, Int> = physics.x.roundToInt() to physics.y.roundToInt()

    /**
     * 推进行走（在 tick 里调用）
     */
    fun advance(dt: Float) {
        if (state == PetState.WALKING) {
            advanceWalking(dt)
        }
    }
}
