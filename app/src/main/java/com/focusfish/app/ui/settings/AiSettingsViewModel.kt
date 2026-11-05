package com.focusfish.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusfish.app.ai.AiConfig
import com.focusfish.app.ai.AiConfigStore
import com.focusfish.app.ai.AiProvider
import com.focusfish.app.ai.AiProviders
import com.focusfish.app.ai.LlmClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * AI 设置 UI 状态
 */
data class AiSettingsUiState(
    /** 选中的厂商 id */
    val providerId: String = AiConfigStore.DEFAULT_PROVIDER_ID,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Float = 0.9f,
    val maxTokens: Int = 1024,
    val enabled: Boolean = true,
    /** 是否已保存过（决定是否显示掩码） */
    val hasSavedKey: Boolean = false,
    val savedMessage: String? = null,
    val errorMessage: String? = null,
    /** 正在测试连通性 */
    val isTesting: Boolean = false,
    /** 测试结果 */
    val testResult: String? = null
) {
    /** 表单是否可提交 */
    val canSave: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() &&
            (apiKey.isNotBlank() || hasSavedKey)

    /** 当前厂商预设 */
    val provider: AiProvider get() = AiProviders.byId(providerId)
}

/**
 * AI 设置 ViewModel
 *
 * 处理用户自填的 API 配置。密钥经 Keystore 加密后长期存储在本机。
 * 大肥鱼不绑定厂商 —— 所有 OpenAI 兼容端点都能接，这里只负责把预设填进表单。
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiConfigStore: AiConfigStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        val config = aiConfigStore.config.value
        _uiState.update {
            it.copy(
                providerId = config.providerId,
                baseUrl = config.baseUrl,
                apiKey = "",
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                enabled = config.enabled,
                hasSavedKey = config.apiKey.isNotBlank()
            )
        }
    }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value) }

    fun updateApiKey(value: String) = _uiState.update { it.copy(apiKey = value) }

    fun updateModel(value: String) = _uiState.update { it.copy(model = value) }

    fun updateTemperature(value: Float) = _uiState.update { it.copy(temperature = value) }

    fun updateMaxTokens(value: Int) = _uiState.update { it.copy(maxTokens = value) }

    fun toggleEnabled(value: Boolean) {
        aiConfigStore.setEnabled(value)
        _uiState.update { it.copy(enabled = value) }
    }

    /**
     * 切换厂商：自动把地址和默认模型填好
     *
     * 密钥和温度保留 —— 用户换厂商通常是想换模型，不是想换 Key。
     */
    fun selectProvider(providerId: String) {
        val provider = AiProviders.byId(providerId)
        _uiState.update {
            it.copy(
                providerId = provider.id,
                baseUrl = provider.baseUrl,
                model = provider.defaultModel,
                errorMessage = null,
                testResult = null
            )
        }
    }

    /** 保存配置 */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(errorMessage = "请填写 API 地址和模型名") }
            return
        }
        // 密钥输入框为空时保留原有密钥
        val current = aiConfigStore.config.value
        val key = state.apiKey.ifBlank { current.apiKey }

        aiConfigStore.save(
            AiConfig(
                providerId = state.providerId,
                baseUrl = state.baseUrl.trim(),
                apiKey = key.trim(),
                model = state.model.trim(),
                temperature = state.temperature,
                maxTokens = state.maxTokens,
                enabled = state.enabled
            )
        )
        _uiState.update {
            it.copy(
                apiKey = "",
                hasSavedKey = true,
                savedMessage = "已保存",
                errorMessage = null
            )
        }
    }

    /**
     * 测试连通性
     *
     * 发一个极短的请求（max_tokens=16），确认地址、密钥、模型名三者都对。
     * 换厂商后最怕的就是地址抄错或者模型名不存在，这里给一个即时反馈。
     */
    fun testConnection() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(errorMessage = "请先填写 API 地址和模型名") }
            return
        }
        val current = aiConfigStore.config.value
        val key = state.apiKey.ifBlank { current.apiKey }

        val probe = AiConfig(
            providerId = state.providerId,
            baseUrl = state.baseUrl.trim(),
            apiKey = key.trim(),
            model = state.model.trim(),
            temperature = state.temperature,
            maxTokens = 16,
            enabled = true
        )

        _uiState.update { it.copy(isTesting = true, testResult = null, errorMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val reply = LlmClient.chatOnce(
                        probe,
                        listOf("user" to "只回复两个字：连上了")
                    )
                    if (reply.isBlank()) {
                        throw IllegalStateException("接口返回了空内容")
                    }
                    reply.trim().take(40)
                }
            }
            _uiState.update { st ->
                result.fold(
                    onSuccess = { text ->
                        st.copy(
                            isTesting = false,
                            testResult = "连上了：$text"
                        )
                    },
                    onFailure = { e ->
                        st.copy(
                            isTesting = false,
                            testResult = null,
                            errorMessage = "连不上：${e.message?.take(200) ?: "未知错误"}"
                        )
                    }
                )
            }
        }
    }

    fun clearSavedMessage() = _uiState.update { it.copy(savedMessage = null) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun clearTestResult() = _uiState.update { it.copy(testResult = null) }
}
