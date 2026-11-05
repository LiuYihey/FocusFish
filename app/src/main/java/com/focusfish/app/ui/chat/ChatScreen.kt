package com.focusfish.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusfish.app.ai.ExpressionLibrary
import com.focusfish.app.data.local.entity.ChatMessageEntity
import com.focusfish.app.ui.components.FishCanvas

/**
 * 对话界面
 *
 * 取代原统计页：这里是大肥鱼与用户的聊天窗口。
 * 顶部展示大肥鱼当前状态（好感度 / 饱腹度 / 情绪），
 * 底部是输入框，右上角有「让大肥鱼说点什么」与「清空」两个操作。
 */
@Composable
fun ChatScreen(
    onOpenPersona: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    // 新消息或流式文本变化时滚到底
    LaunchedEffect(messages.size, uiState.streamingText) {
        if (messages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            listState.animateScrollToItem(
                (messages.size + if (uiState.streamingText.isNotEmpty()) 1 else 0).coerceAtLeast(0)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // ── 顶部：大肥鱼状态条 ──
        ChatHeader(
            affinityTitle = uiState.affinityTitle,
            affinity = uiState.affinity,
            satiety = uiState.satiety,
            moodDescription = uiState.moodDescription,
            onOpenPersona = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenPersona()
            }
        )

        // ── 消息列表 ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 未配置 API 时的引导
            if (!uiState.isConfigured) {
                item {
                    NotConfiguredCard(onOpenAiSettings = onOpenAiSettings)
                }
            }

            // 空对话时的欢迎语
            if (messages.isEmpty()) {
                item {
                    WelcomeCard()
                }
            }

            // 候选记忆提示：大肥鱼想记住某件事，等用户点头
            if (uiState.pendingMemoryCount > 0) {
                item {
                    PendingMemoryBanner(
                        count = uiState.pendingMemoryCount,
                        onClick = onOpenMemory
                    )
                }
            }

            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }

            // 流式输出中的气泡
            if (uiState.streamingText.isNotEmpty()) {
                item {
                    ChatBubble(
                        ChatMessageEntity(
                            role = "FISH",
                            content = uiState.streamingText,
                            kind = "CHAT"
                        ),
                        isStreaming = true
                    )
                }
            }

            // 等待首个 token 时的加载态
            if (uiState.isReplying && uiState.streamingText.isEmpty()) {
                item { TypingIndicator() }
            }
        }

        // ── 错误提示 ──
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ── 底部输入区 ──
        ChatInputBar(
            input = uiState.input,
            isReplying = uiState.isReplying,
            canSend = uiState.canSend,
            onInputChange = viewModel::updateInput,
            onSend = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.send()
            },
            onStop = viewModel::stopReplying,
            onTriggerProactive = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.triggerProactive()
            }
        )
    }
}

/**
 * 顶部状态条
 */
@Composable
private fun ChatHeader(
    affinityTitle: String,
    affinity: Int,
    satiety: Int,
    moodDescription: String,
    onOpenPersona: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 大肥鱼头像（缩略）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                FishCanvas(
                    breedId = "bigfatfish",
                    state = "idle",
                    modifier = Modifier.fillMaxSize(),
                    useVideo = false
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = affinityTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = moodDescription.ifBlank { "好感度 $affinity · 饱腹 $satiety" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            IconButton(onClick = onOpenPersona) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "人格卡",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 单条消息气泡
 */
@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "USER"
    // 主动开口的消息用左侧色带标记，让用户一眼看出这是大肥鱼自己发起的
    val isProactive = message.kind == "PROACTIVE"
    val context = androidx.compose.ui.platform.LocalContext.current

    // 匹配表情：只对大肥鱼的消息生效，用内容关键词猜
    val expression = remember(message.content, isUser) {
        if (isUser) null else ExpressionLibrary.Expression.guess(message.content)
    }
    // 优先用真实表情包（每种情绪 4 张，随机取一张不重样）；
    // 没有贴纸时退回统一规格的大表情。
    // seed 用消息 id，保证同一条消息每次重组都是同一张脸，不会闪。
    val stickerBitmap = remember(expression, message.id) {
        expression?.let { ExpressionLibrary.loadSticker(context, it, message.id.toInt()) }
    }
    val expressionBitmap = remember(expression) {
        if (stickerBitmap != null) null
        else expression?.let { ExpressionLibrary.load(context, it) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                FishCanvas(
                    breedId = "bigfatfish",
                    state = "idle",
                    modifier = Modifier.fillMaxSize(),
                    useVideo = false
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            if (isProactive) {
                Text(
                    text = "大肥鱼主动说",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }

            // 表情包：命中关键词时在气泡上方显示大肥鱼表情
            if (stickerBitmap != null) {
                Image(
                    bitmap = stickerBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .padding(bottom = 4.dp)
                )
            } else if (expressionBitmap != null) {
                Image(
                    bitmap = expressionBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .padding(bottom = 4.dp)
                )
            }

            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 5.dp,
                    bottomEnd = if (isUser) 5.dp else 16.dp
                )
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * 等待首个 token 的加载态
 */
@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 5.dp, bottomEnd = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 未配置 API 的引导卡
 */
@Composable
private fun NotConfiguredCard(onOpenAiSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "大肥鱼还没连上脑子",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "在设置里填上你自己的 API 地址和密钥，大肥鱼就能陪你聊天了。密钥只存在这台设备上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.TextButton(onClick = onOpenAiSettings) {
                Text("去配置 API")
            }
        }
    }
}

/**
 * 候选记忆提示条
 *
 * 大肥鱼从你说的话里捡到了可能值得记的事，先放着。
 * 你点头它才真的记住 —— 假装记住了比没记住更伤。
 */
@Composable
private fun PendingMemoryBanner(count: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "大肥鱼想记住 $count 件事",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "去看看",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 空对话时的欢迎语
 */
@Composable
private fun WelcomeCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FishCanvas(
            breedId = "bigfatfish",
            state = "idle",
            modifier = Modifier.size(140.dp),
            useVideo = false
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "我是吃白饭的大肥鱼",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "陪我专注，我就有大白饭吃。\n想聊点什么都行，反正我闲着也是摸鱼。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 合规提示：明确告知用户正在与 AI 而非自然人互动
        //（《人工智能拟人化互动服务管理暂行办法》要求显著提示）
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "大肥鱼是 AI，不是真人。它的记忆和心情都由程序模拟，聊天记录只存在你手机里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/**
 * 底部输入区
 */
@Composable
private fun ChatInputBar(
    input: String,
    isReplying: Boolean,
    canSend: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onTriggerProactive: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 让大肥鱼主动开口
            IconButton(onClick = onTriggerProactive, enabled = !isReplying) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "让大肥鱼说点什么",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }

            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "跟大肥鱼说点什么…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(16.dp),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.size(6.dp))

            if (isReplying) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "停止",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "发送",
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
