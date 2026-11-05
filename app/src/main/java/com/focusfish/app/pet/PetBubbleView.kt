package com.focusfish.app.pet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * 桌宠的台词气泡
 *
 * 设计要点（来自 dsh-pet 与桌宠用户研究的教训）：
 * - 白色毛玻璃感（半透明白 + 细描边），不要纯白也不要纯黑
 * - 圆角 + 小尾巴指向大肥鱼
 * - 文字自动换行，最大宽度受限
 * - 10 秒自动收起（工作状态气泡按 dsh 规则）
 * - 用户可一键关气泡 → 隐藏时不是"消失"，而是退化成静音模式
 */
class PetBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    companion object {
        /** 气泡最大宽度（dp） */
        const val MAX_WIDTH_DP = 220f

        /** 内边距（dp） */
        const val PADDING_DP = 12f

        /** 圆角半径（dp） */
        const val RADIUS_DP = 16f

        /** 尾巴高度（dp） */
        const val TAIL_HEIGHT_DP = 8f
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 255, 255, 255)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(60, 90, 110, 140)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B3A4A")
        textSize = sp(13f)
    }

    private var text: String = ""
    private var bubbleRect = RectF()
    private val tailPath = Path()

    /** 文字最大宽度 */
    private val maxTextWidth: Float get() = dp(MAX_WIDTH_DP) - dp(PADDING_DP) * 2

    /**
     * 设置气泡文字
     *
     * @return 气泡实际高度（px），供外部布局用
     */
    fun setText(value: String): Int {
        text = value
        val lines = wrapText(value, maxTextWidth)
        val lineHeight = textPaint.fontSpacing
        val textHeight = lineHeight * lines.size
        val height = (textHeight + dp(PADDING_DP) * 2 + dp(TAIL_HEIGHT_DP)).toInt()
        requestLayout()
        invalidate()
        return height
    }

    /**
     * 简单换行（按字符宽度，中文友好）
     */
    private fun wrapText(value: String, maxWidth: Float): List<String> {
        if (value.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in value) {
            if (ch == '\n') {
                lines.add(current.toString())
                current = StringBuilder()
                continue
            }
            current.append(ch)
            if (textPaint.measureText(current.toString()) > maxWidth) {
                // 去掉最后一个字符，另起一行
                val last = current.toString()
                current = StringBuilder()
                current.append(last.last())
                lines.add(last.dropLast(1))
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lines = wrapText(text, maxTextWidth)
        val lineHeight = textPaint.fontSpacing
        val width = (dp(MAX_WIDTH_DP)).toInt()
        val height = (lineHeight * lines.size + dp(PADDING_DP) * 2 + dp(TAIL_HEIGHT_DP)).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (text.isEmpty()) return

        val lines = wrapText(text, maxTextWidth)
        val lineHeight = textPaint.fontSpacing
        val pad = dp(PADDING_DP)
        val tail = dp(TAIL_HEIGHT_DP)

        // 气泡矩形（底部留出尾巴的空间）
        val bodyBottom = height - tail
        bubbleRect.set(0f, 0f, width.toFloat(), bodyBottom)

        // 主体
        canvas.drawRoundRect(bubbleRect, dp(RADIUS_DP), dp(RADIUS_DP), bgPaint)
        canvas.drawRoundRect(bubbleRect, dp(RADIUS_DP), dp(RADIUS_DP), borderPaint)

        // 尾巴（指向下方的桌宠）
        val centerX = width / 2f
        tailPath.reset()
        tailPath.moveTo(centerX - dp(7f), bodyBottom - 1f)
        tailPath.lineTo(centerX, height.toFloat())
        tailPath.lineTo(centerX + dp(7f), bodyBottom - 1f)
        tailPath.close()
        canvas.drawPath(tailPath, bgPaint)

        // 文字
        var baseline = pad + textPaint.textSize
        for (line in lines) {
            canvas.drawText(line, pad, baseline, textPaint)
            baseline += lineHeight
        }
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    private fun sp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
    )
}
