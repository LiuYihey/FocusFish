package com.focusfish.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusfish.app.ai.MemoryEngine
import com.focusfish.app.data.local.entity.FishMemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 大肥鱼记忆管理 UI 状态
 */
data class MemoryUiState(
    /** 待确认的候选记忆 */
    val candidates: List<FishMemoryEntity> = emptyList(),
    /** 已确认的记忆 */
    val confirmed: List<FishMemoryEntity> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * 记忆管理 ViewModel
 *
 * 为什么要"候选态"这个设计：
 * 陪伴型 AI 产品的公认教训是「假装记住了比没记住更伤」。
 * 所以大肥鱼从对话里捕捉到可能值得记的事时，先放进候选区，
 * 用户点头之后才真的进她的记忆里。
 */
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryEngine: MemoryEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            memoryEngine.observeCandidates().collect { list ->
                _uiState.update { it.copy(candidates = list, isLoading = false) }
            }
        }
        viewModelScope.launch {
            memoryEngine.observeAll().collect { list ->
                _uiState.update {
                    it.copy(
                        confirmed = list.filter { m -> m.status == FishMemoryEntity.STATUS_CONFIRMED },
                        isLoading = false
                    )
                }
            }
        }
    }

    /** 确认：这条真的要记住 */
    fun confirm(memory: FishMemoryEntity) {
        viewModelScope.launch { memoryEngine.confirm(memory.id) }
    }

    /** 拒绝：不记了 */
    fun dismiss(memory: FishMemoryEntity) {
        viewModelScope.launch { memoryEngine.dismiss(memory.id) }
    }

    /** 置顶：这条是长期事实，每轮都带着 */
    fun togglePinned(memory: FishMemoryEntity) {
        viewModelScope.launch { memoryEngine.togglePinned(memory) }
    }

    /** 清空全部记忆 */
    fun clearAll() {
        viewModelScope.launch { memoryEngine.clearAll() }
    }
}
