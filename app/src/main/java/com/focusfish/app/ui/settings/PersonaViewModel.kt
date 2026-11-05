package com.focusfish.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusfish.app.ai.FishBrain
import com.focusfish.app.data.local.entity.PersonaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 人格卡编辑 UI 状态
 */
data class PersonaUiState(
    val name: String = "",
    val identity: String = "",
    val traits: String = "",
    val speechStyle: String = "",
    val catchphrases: String = "",
    val backstory: String = "",
    val taboos: String = "",
    val userAlias: String = "",
    val proactiveMotivation: String = "",
    val temperature: Float = 0.9f,
    val savedMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * 人格卡 ViewModel
 *
 * 让用户定义大肥鱼是谁：名字、性格、口头禅、来历、禁忌。
 * 保存后立即对后续对话生效。
 */
@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val fishBrain: FishBrain
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaUiState())
    val uiState: StateFlow<PersonaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val persona = fishBrain.getPersona()
            _uiState.update {
                it.copy(
                    name = persona.name,
                    identity = persona.identity,
                    traits = persona.traits,
                    speechStyle = persona.speechStyle,
                    catchphrases = persona.catchphrases,
                    backstory = persona.backstory,
                    taboos = persona.taboos,
                    userAlias = persona.userAlias,
                    proactiveMotivation = persona.proactiveMotivation,
                    temperature = persona.temperature
                )
            }
        }
    }

    fun updateName(v: String) = _uiState.update { it.copy(name = v) }
    fun updateIdentity(v: String) = _uiState.update { it.copy(identity = v) }
    fun updateTraits(v: String) = _uiState.update { it.copy(traits = v) }
    fun updateSpeechStyle(v: String) = _uiState.update { it.copy(speechStyle = v) }
    fun updateCatchphrases(v: String) = _uiState.update { it.copy(catchphrases = v) }
    fun updateBackstory(v: String) = _uiState.update { it.copy(backstory = v) }
    fun updateTaboos(v: String) = _uiState.update { it.copy(taboos = v) }
    fun updateUserAlias(v: String) = _uiState.update { it.copy(userAlias = v) }
    fun updateProactiveMotivation(v: String) = _uiState.update { it.copy(proactiveMotivation = v) }
    fun updateTemperature(v: Float) = _uiState.update { it.copy(temperature = v) }

    /** 保存人格卡 */
    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "总得给它起个名字吧") }
            return
        }
        viewModelScope.launch {
            fishBrain.savePersona(
                PersonaEntity(
                    id = 1,
                    name = state.name.trim(),
                    identity = state.identity.trim(),
                    traits = state.traits.trim(),
                    speechStyle = state.speechStyle.trim(),
                    catchphrases = state.catchphrases.trim(),
                    backstory = state.backstory.trim(),
                    taboos = state.taboos.trim(),
                    userAlias = state.userAlias.trim().ifBlank { "你" },
                    proactiveMotivation = state.proactiveMotivation.trim(),
                    temperature = state.temperature,
                    enabled = true
                )
            )
            _uiState.update { it.copy(savedMessage = "人格卡已更新", errorMessage = null) }
        }
    }

    /** 恢复默认人格 */
    fun resetToDefault() {
        viewModelScope.launch {
            fishBrain.savePersona(PersonaEntity())
            val persona = fishBrain.getPersona()
            _uiState.update {
                it.copy(
                    name = persona.name,
                    identity = persona.identity,
                    traits = persona.traits,
                    speechStyle = persona.speechStyle,
                    catchphrases = persona.catchphrases,
                    backstory = persona.backstory,
                    taboos = persona.taboos,
                    userAlias = persona.userAlias,
                    proactiveMotivation = persona.proactiveMotivation,
                    temperature = persona.temperature,
                    savedMessage = "已恢复默认人格",
                    errorMessage = null
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(savedMessage = null, errorMessage = null) }
    }
}
