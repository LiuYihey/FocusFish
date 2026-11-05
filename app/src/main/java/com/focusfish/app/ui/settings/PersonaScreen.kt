package com.focusfish.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 人格卡编辑页
 *
 * 让用户定义「这只大肥鱼是谁」。
 * 字段沿用梗鲸人设卡的公开结构（name / tags / summary / body），
 * 但做成可视化表单 —— 这是官方没有的空白位。
 */
@Composable
fun PersonaScreen(
    onBack: () -> Unit = {},
    viewModel: PersonaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = "大肥鱼人格卡",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::resetToDefault) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "恢复默认",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("默认")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "这里写下的每一句都会成为大肥鱼说话的方式。性格写得越具体，它越像一个真的室友，而不是一个问答机器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PersonaField(
                label = "名字",
                value = uiState.name,
                onValueChange = viewModel::updateName,
                placeholder = "大肥鱼",
                singleLine = true
            )

            PersonaField(
                label = "你是谁",
                value = uiState.identity,
                onValueChange = viewModel::updateIdentity,
                placeholder = "我是吃白饭的大肥鱼。住在你的手机里……",
                minLines = 2
            )

            PersonaField(
                label = "性格标签（逗号分隔）",
                value = uiState.traits,
                onValueChange = viewModel::updateTraits,
                placeholder = "摸鱼,傲娇,话痨,贪吃",
                singleLine = true
            )

            PersonaField(
                label = "说话的样子",
                value = uiState.speechStyle,
                onValueChange = viewModel::updateSpeechStyle,
                placeholder = "短句为主，语气随意，被说胖会炸毛。",
                minLines = 3
            )

            PersonaField(
                label = "口头禅（逗号分隔）",
                value = uiState.catchphrases,
                onValueChange = viewModel::updateCatchphrases,
                placeholder = "呜哇,好耶,我去吃饭了",
                singleLine = true
            )

            PersonaField(
                label = "来历",
                value = uiState.backstory,
                onValueChange = viewModel::updateBackstory,
                placeholder = "住在你的手机里，靠吃大白饭长大……",
                minLines = 3
            )

            PersonaField(
                label = "禁忌话题（逗号分隔）",
                value = uiState.taboos,
                onValueChange = viewModel::updateTaboos,
                placeholder = "承认自己是AI,说它瘦",
                singleLine = true
            )

            PersonaField(
                label = "它怎么称呼你",
                value = uiState.userAlias,
                onValueChange = viewModel::updateUserAlias,
                placeholder = "你",
                singleLine = true
            )

            PersonaField(
                label = "它主动开口的理由",
                value = uiState.proactiveMotivation,
                onValueChange = viewModel::updateProactiveMotivation,
                placeholder = "你注意到他今天摸鱼摸得有点久了，想吐槽他一句",
                singleLine = false,
                minLines = 3,
                helper = "这一段决定大肥鱼「为什么想主动跟你说话」。写得有血有肉一点，它说话才不像通知。留空则用内置的几个理由。"
            )

            // 温度
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "温度",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.1f", uiState.temperature),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = uiState.temperature,
                        onValueChange = viewModel::updateTemperature,
                        valueRange = 0f..2f,
                        steps = 19
                    )
                }
            }

            uiState.errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            uiState.savedMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存人格卡")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 人格卡字段输入框
 */
@Composable
private fun PersonaField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
    helper: String? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        if (helper != null) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}
