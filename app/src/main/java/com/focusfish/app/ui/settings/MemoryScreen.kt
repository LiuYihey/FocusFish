package com.focusfish.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusfish.app.data.local.entity.FishMemoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 大肥鱼的记忆管理页
 *
 * 上半部分是「候选区」——大肥鱼从你说过的话里捕捉到的、等着你点头的事。
 * 下半部分是「已记住的」——确认过、真的会带进对话里的事。
 */
@Composable
fun MemoryScreen(
    onBack: () -> Unit = {},
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("M月d日", Locale.CHINA)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = "大肥鱼的记忆",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text(
                    text = "大肥鱼会从你说过的话里捡一些事记下来。候选的得你点头才算数 —— 记错了比没记更伤人。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.candidates.isNotEmpty()) {
                item {
                    SectionTitle("等你点头（${uiState.candidates.size}）")
                }
                items(uiState.candidates, key = { it.id }) { memory ->
                    CandidateCard(
                        memory = memory,
                        dateText = dateFormat.format(Date(memory.createdAt)),
                        onConfirm = { viewModel.confirm(memory) },
                        onDismiss = { viewModel.dismiss(memory) }
                    )
                }
            }

            item {
                SectionTitle("已经记住了（${uiState.confirmed.size}）")
            }

            if (uiState.confirmed.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "她还什么都没记住呢。跟她说说你喜欢什么、最近在忙什么吧。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(uiState.confirmed, key = { it.id }) { memory ->
                ConfirmedCard(
                    memory = memory,
                    dateText = dateFormat.format(Date(memory.createdAt)),
                    onTogglePin = { viewModel.togglePinned(memory) },
                    onDelete = { viewModel.dismiss(memory) }
                )
            }

            if (uiState.confirmed.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { viewModel.clearAll() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("忘掉所有事")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

/** 小节标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** 候选记忆卡片 */
@Composable
private fun CandidateCard(
    memory: FishMemoryEntity,
    dateText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${kindLabel(memory.kind)} · $dateText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("不用记") }
                TextButton(onClick = onConfirm) { Text("记下来") }
            }
        }
    }
}

/** 已确认记忆卡片 */
@Composable
private fun ConfirmedCard(
    memory: FishMemoryEntity,
    dateText: String,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = if (memory.isPinned) "取消置顶" else "长期记住",
                        tint = if (memory.isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            Text(
                text = "${kindLabel(memory.kind)} · $dateText · 提起过 ${memory.recallCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onDelete) { Text("忘掉这条") }
        }
    }
}

/** 记忆类型的中文名 */
private fun kindLabel(kind: String): String = when (kind) {
    FishMemoryEntity.KIND_RELATION -> "关于你"
    else -> "我们之间"
}
