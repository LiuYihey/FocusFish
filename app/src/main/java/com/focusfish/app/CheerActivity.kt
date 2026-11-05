package com.focusfish.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusfish.app.ai.ExpressionLibrary
import com.focusfish.app.ui.theme.FocusFishTheme

/**
 * 大肥鱼鼓励页
 *
 * 触发时机：用户在拦截页选择「不进去了」之后。
 * 对应 FocusCat 里「退出被约束应用 → 展示格言」的位置，
 * 但这里换成大肥鱼的表情包 + 一句它自己说的话。
 *
 * 设计要点：
 * - 纯白背景的大表情，无边框无阴影
 * - 文案来自大肥鱼的人格（不是通用鸡汤）
 * - 点击任意处即可关闭，直接回桌面
 */
class CheerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val expressionName = intent.getStringExtra(EXTRA_EXPRESSION) ?: "cheer"

        setContent {
            FocusFishTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CheerContent(expressionName = expressionName)
                }
            }
        }
    }

    companion object {
        const val EXTRA_EXPRESSION = "extra_expression"
    }
}

/**
 * 鼓励页内容
 */
@androidx.compose.runtime.Composable
private fun CheerContent(expressionName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val expression = ExpressionLibrary.Expression.entries
        .firstOrNull { it.name == expressionName }
        ?: ExpressionLibrary.Expression.CHEER

    val bitmap = remember(expression) {
        ExpressionLibrary.load(context, expression)
    }

    // 真实表情包：全屏大图时优先用它，比统一规格的立绘更鲜活
    val stickerBitmap = remember(expression) {
        ExpressionLibrary.loadSticker(context, expression)
    }

    // 入场：轻微弹入
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.7f,
        animationSpec = tween(durationMillis = 420),
        label = "cheerScale"
    )

    // 台词：按表情类型给一句大肥鱼口吻的话
    val line = remember(expression) { cheerLine(expression) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 表情
            if (stickerBitmap != null) {
                Image(
                    bitmap = stickerBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
            } else {
                // 资源缺失时的占位
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🐟",
                        fontSize = 72.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 大肥鱼说的话
            Text(
                text = line,
                fontSize = 19.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            TextButton(
                onClick = { (context as? CheerActivity)?.finishCheer() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "去专注一会儿",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 各表情对应的台词
 *
 * 刻意用大肥鱼的口吻（摸鱼、吃白饭、傲娇），而不是通用励志语 ——
 * 这才像「有个活物在跟你说话」。
 */
private fun cheerLine(expression: ExpressionLibrary.Expression): String = when (expression) {
    ExpressionLibrary.Expression.CHEER -> "欸，别走啊！\n再专注一会儿，大白饭就有着落了。"
    ExpressionLibrary.Expression.HUNGRY -> "算你识相。\n我快饿扁了，等下记得喂我大白饭。"
    ExpressionLibrary.Expression.SLACKING -> "行吧行吧，你先去。\n不过说真的，我摸鱼摸得也挺开心的。"
    ExpressionLibrary.Expression.EXCITED -> "哇！你居然忍住了！\n这波操作我给你记上了。"
    ExpressionLibrary.Expression.SAD -> "呜…你又没忍住。\n我在这儿等你回来。"
    ExpressionLibrary.Expression.ANGRY -> "哼！\n说了别摸手机，你是听不懂鲸语吗。"
    ExpressionLibrary.Expression.SHY -> "那个…你要是真的回来了，\n我、我就不计较刚才的事了。"
    ExpressionLibrary.Expression.PROUD -> "嘿嘿，我早就说了吧。\n你比我想象中能扛。"
    ExpressionLibrary.Expression.BORED -> "你终于舍得回来了。\n我在这儿无聊得快发霉了。"
    ExpressionLibrary.Expression.READING -> "我刚读完一页书呢。\n等你回来我讲给你听。"
    ExpressionLibrary.Expression.THINKING -> "我刚才在想个事。\n你猜我在想什么？"
    ExpressionLibrary.Expression.SLEEPING -> "zzz…\n你回来了？我刚睡醒。"
}

/**
 * 关闭并回到桌面
 */
fun CheerActivity.finishCheer() {
    // 回到桌面而不是留在这一页
    val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_HOME)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(homeIntent) }
    finish()
}
