package com.focusfish.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusfish.app.ai.AiConfigStore
import com.focusfish.app.ai.FishBrain
import com.focusfish.app.ai.FishMood
import com.focusfish.app.ai.FishMoodResolver
import com.focusfish.app.ai.MemoryEngine
import com.focusfish.app.ai.ProactiveAction
import com.focusfish.app.ai.ProactiveMotive
import com.focusfish.app.data.local.entity.ChatMessageEntity
import com.focusfish.app.data.local.entity.FishStateEntity
import com.focusfish.app.data.local.entity.PersonaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 对话页 UI 状态
 */
data class ChatUiState(
    /** 当前输入框内容 */
    val input: String = "",
    /** 是否正在等待大肥鱼回复 */
    val isReplying: Boolean = false,
    /** 流式回复的实时文本（打字机效果） */
    val streamingText: String = "",
    /** 错误提示 */
    val errorMessage: String? = null,
    /** 是否已完成配置 API */
    val isConfigured: Boolean = false,
    /** 大肥鱼当前状态分档 */
    val moodTier: FishMood.Tier = FishMood.Tier.SHY,
    /** 当前状态描述 */
    val moodDescription: String = "",
    /** 饱腹度 0~100 */
    val satiety: Int = 70,
    /** 好感度 */
    val affinity: Int = 0,
    /** 好感度档位标题 */
    val affinityTitle: String = "初次见面",
    /** 待用户确认的候选记忆条数（>0 时聊天页给个提示） */
    val pendingMemoryCount: Int = 0
) {
    /** 是否可以发送 */
    val canSend: Boolean get() = input.isNotBlank() && !isReplying
}

/**
 * 对话 ViewModel
 *
 * 承接原统计页的位置：现在这一页是「大肥鱼与你聊天」。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val fishBrain: FishBrain,
    private val aiConfigStore: AiConfigStore,
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    /** 全部消息 */
    val messages: StateFlow<List<ChatMessageEntity>> = fishBrain.observeMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 人格卡 */
    val persona: StateFlow<PersonaEntity?> = fishBrain.observePersona()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** 大肥鱼状态 */
    val fishState: StateFlow<FishStateEntity?> = fishBrain.observeState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 当前流式请求的 Job，用于中途取消 */
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            aiConfigStore.config.collect { config ->
                _uiState.update { it.copy(isConfigured = config.isValid) }
            }
        }
        refreshMood()
    }

    /** 更新输入框 */
    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /** 发送消息 */
    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.isReplying) return

        _uiState.update { it.copy(input = "", isReplying = true, streamingText = "", errorMessage = null) }

        streamingJob = viewModelScope.launch {
            try {
                fishBrain.chat(text) { delta ->
                    _uiState.update { it.copy(streamingText = it.streamingText + delta) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "和大肥鱼说话失败了，稍后再试试")
                }
            } finally {
                _uiState.update { it.copy(isReplying = false, streamingText = "") }
                refreshMood()
            }
        }
    }

    /** 停止生成 */
    fun stopReplying() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update { it.copy(isReplying = false, streamingText = "") }
    }

    /** 清空对话记录 */
    fun clearChat() {
        viewModelScope.launch {
            fishBrain.clearChat()
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * 手动触发一次主动行为
     *
     * 用户主动点「让大肥鱼说点什么」时用，绕过概率判定直接触发。
     */
    fun triggerProactive(action: ProactiveAction = ProactiveAction.CHAT) {
        if (_uiState.value.isReplying) return
        viewModelScope.launch {
            _uiState.update { it.copy(isReplying = true, errorMessage = null) }
            // 手动触发时也给一个由头，让这次主动开口也有理由
            val result = fishBrain.runProactive(action, motive = ProactiveMotive.SILENCE)
            if (result == null) {
                _uiState.update { it.copy(errorMessage = "大肥鱼现在没什么想说的（可能还没配置 API）") }
            }
            _uiState.update { it.copy(isReplying = false) }
            refreshMood()
        }
    }

    /** 刷新状态分档 */
    private fun refreshMood() {
        viewModelScope.launch {
            val state = fishBrain.getState()
            val affinity = fishBrain.currentAffinity()
            val mood = FishMoodResolver.resolve(
                affinity = affinity,
                satiety = state.satiety,
                mood = state.mood,
                energy = state.energy,
                isSleeping = state.isSleeping
            )
            _uiState.update {
                it.copy(
                    moodTier = mood.tier,
                    moodDescription = mood.description,
                    satiety = state.satiety,
                    affinity = affinity,
                    affinityTitle = FishMoodResolver.affinityTitle(affinity)
                )
            }
        }
        // 候选记忆条数：有新候选时在聊天页给个提示
        viewModelScope.launch {
            memoryEngine.observeCandidates().collect { list ->
                _uiState.update { it.copy(pendingMemoryCount = list.size) }
            }
        }
    }
}
