package com.focusfish.app.pet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.doOnLayout

/**
 * 桌宠本体视图
 *
 * 负责渲染大肥鱼并播放轻量动画。
 *
 * 动画清单（对齐 dsh-pet 的核心观感）：
 * - 待机呼吸：轻微的缩放起伏，2.2 秒一个周期
 * - 点击 Q 弹：scaleX 撑大 / scaleY 压扁 → 回弹（OvershootInterpolator）
 * - 行走：上下小跳 + 轻微左右倾
 * - 睡觉：呼吸幅度变大变慢 + 闭眼（切贴图）
 * - 朝向：拖动/行走时水平翻转
 *
 * 刻意不做的事：不引入 Lottie / GLSurfaceView。
 * 桌宠只需要"会动"，不需要骨骼动画。用 Canvas 变换就够，
 * 省掉一个几 MB 的依赖，也省掉 alpha 视频解码的坑。
 */
class PetSpriteView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    companion object {
        /** 呼吸动画周期（ms） */
        const val BREATH_DURATION = 2200L

        /** Q 弹动画时长（ms） */
        const val SQUASH_DURATION = 320L

        /** 行走步频（ms） */
        const val WALK_CYCLE = 700L
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val matrix = Matrix()
    private val srcRect = Rect()
    private val dstRect = RectF()

    /** 当前贴图 */
    private var bitmap: Bitmap? = null

    /** 当前姿态 */
    var pose: PetSpriteLibrary.Pose = PetSpriteLibrary.Pose.IDLE
        set(value) {
            if (field != value) {
                field = value
                bitmap = PetSpriteLibrary.load(context, value)
                invalidate()
            }
        }

    /** 朝向：1 右，-1 左 */
    var facing: Int = 1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 是否正在睡觉（影响呼吸节奏） */
    var sleeping: Boolean = false

    /** 呼吸相位 0..1 */
    private var breathPhase = 0f

    /** Q 弹缩放因子 */
    private var squashX = 1f
    private var squashY = 1f

    /** 行走相位 */
    private var walkPhase = 0f

    /** 是否正在行走 */
    var walking: Boolean = false

    /** 呼吸动画 */
    private var breathAnimator: ValueAnimator? = null

    /** Q 弹动画 */
    private var squashAnimator: ValueAnimator? = null

    init {
        bitmap = PetSpriteLibrary.load(context, pose)
        startBreathing()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnimator?.cancel()
        squashAnimator?.cancel()
    }

    /**
     * 启动呼吸动画
     */
    private fun startBreathing() {
        breathAnimator?.cancel()
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BREATH_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                breathPhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 点击时的 Q 弹反馈
     *
     * 形变方向：被点击瞬间横向撑开、纵向压扁 —— 果冻感。
     * 然后回弹到原状（略过冲）。
     */
    fun playSquash() {
        squashAnimator?.cancel()
        squashAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SQUASH_DURATION
            interpolator = OvershootInterpolator(3f)
            addUpdateListener {
                val t = it.animatedValue as Float
                // 前 40% 压缩，后 60% 回弹
                val compression = if (t < 0.4f) {
                    t / 0.4f
                } else {
                    1f - (t - 0.4f) / 0.6f
                }
                squashX = 1f + compression * 0.22f
                squashY = 1f - compression * 0.18f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    squashX = 1f
                    squashY = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    /**
     * 设置行走状态
     */
    fun startWalking() {
        walking = true
        invalidate()
    }

    /** 停止行走 */
    fun stopWalking() {
        walking = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        if (bmp.isRecycled) return

        val w = width.toFloat()
        val h = height.toFloat()

        // 呼吸：睡觉时幅度大、周期慢
        val breathAmount = if (sleeping) 0.055f else 0.022f
        val breathScale = 1f + kotlin.math.sin(breathPhase * 2f * Math.PI.toFloat()) * breathAmount

        // 行走：上下小跳
        var walkOffsetY = 0f
        var walkTilt = 0f
        if (walking) {
            walkPhase += 1f / WALK_CYCLE
            if (walkPhase > 1f) walkPhase -= 1f
            val swing = kotlin.math.sin(walkPhase * 2f * Math.PI.toFloat())
            walkOffsetY = -kotlin.math.abs(swing) * h * 0.045f
            walkTilt = swing * 4f
        }

        // 合成变换：缩放（呼吸 × Q 弹）→ 平移（行走）→ 旋转（行走倾斜）→ 翻转（朝向）
        val scaleX = breathScale * squashX * facing
        val scaleY = breathScale * squashY

        val drawW = w * kotlin.math.abs(scaleX)
        val drawH = h * scaleY

        matrix.reset()
        // 以中心为锚点
        matrix.postTranslate(-w / 2f, -h / 2f)
        matrix.postScale(scaleX, scaleY)
        if (walkTilt != 0f) {
            matrix.postRotate(walkTilt)
        }
        matrix.postTranslate(w / 2f, h / 2f + walkOffsetY)

        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRect.set(
            (w - drawW) / 2f,
            (h - drawH) / 2f,
            (w + drawW) / 2f,
            (h + drawH) / 2f
        )

        canvas.drawBitmap(bmp, srcRect, dstRect, paint)
    }
}
