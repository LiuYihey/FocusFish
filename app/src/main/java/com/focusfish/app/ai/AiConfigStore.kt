package com.focusfish.app.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI API 配置存储
 *
 * 用户在「设置 → 大肥鱼 AI」中自行填写：
 * - baseUrl：OpenAI 兼容端点根地址，例如 https://api.deepseek.com/v1
 * - apiKey：密钥，长期保存在本机 SharedPreferences
 * - model：模型名，例如 deepseek-chat
 *
 * 长期存储说明：
 * - SharedPreferences 的 apply() 异步落盘，进程被杀不丢数据
 * - 单独文件 focus_fish_ai，与其他设置隔离
 * - 通过 StateFlow 对外暴露变更，配置后立即生效，无需重启
 */
class AiConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<AiConfig> = _config.asStateFlow()

    /** 保存配置并立即广播变更 */
    fun save(config: AiConfig) {
        prefs.edit()
            .putString(KEY_PROVIDER_ID, config.providerId)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, SecureKeyStore.encrypt(config.apiKey))
            .putString(KEY_MODEL, config.model)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putInt(KEY_MAX_TOKENS, config.maxTokens)
            .putBoolean(KEY_ENABLED, config.enabled)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
        _config.value = config
    }

    /** 只更新开关，不动其他字段 */
    fun setEnabled(enabled: Boolean) {
        save(_config.value.copy(enabled = enabled))
    }

    /** 掩码显示的密钥，如 sk-abc...xyz */
    fun maskedApiKey(): String {
        val key = _config.value.apiKey
        if (key.isBlank()) return ""
        if (key.length <= 10) return "••••••"
        return key.take(4) + "••••••" + key.takeLast(4)
    }

    private fun read(): AiConfig {
        val storedKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val providerId = prefs.getString(KEY_PROVIDER_ID, DEFAULT_PROVIDER_ID) ?: DEFAULT_PROVIDER_ID
        val provider = AiProviders.byId(providerId)
        return AiConfig(
            providerId = providerId,
            baseUrl = prefs.getString(KEY_BASE_URL, provider.baseUrl)?.takeIf { it.isNotBlank() }
                ?: provider.baseUrl,
            apiKey = SecureKeyStore.decrypt(storedKey),
            model = prefs.getString(KEY_MODEL, provider.defaultModel)?.takeIf { it.isNotBlank() }
                ?: provider.defaultModel,
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.9f),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 1024),
            enabled = prefs.getBoolean(KEY_ENABLED, true)
        )
    }

    companion object {
        private const val PREFS_NAME = "focus_fish_ai"
        private const val KEY_PROVIDER_ID = "provider_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_UPDATED_AT = "updated_at"

        /** 默认厂商 */
        const val DEFAULT_PROVIDER_ID = "deepseek"

        /** 默认模型名（用户可在设置里改） */
        const val DEFAULT_MODEL = "deepseek-flash"

        /** DeepSeek 官方 OpenAI 兼容端点，作为预设默认值 */
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }
}

/**
 * AI 配置数据类
 */
data class AiConfig(
    /** 厂商预设 id，见 [AiProviders] */
    val providerId: String = AiConfigStore.DEFAULT_PROVIDER_ID,
    /** OpenAI 兼容端点根地址，如 https://api.deepseek.com */
    val baseUrl: String = AiConfigStore.DEFAULT_BASE_URL,
    /** API Key */
    val apiKey: String = "",
    /** 模型名 */
    val model: String = AiConfigStore.DEFAULT_MODEL,
    /** 温度 0~2 */
    val temperature: Float = 0.9f,
    /** 单次回复最大 token */
    val maxTokens: Int = 1024,
    /** 是否启用 */
    val enabled: Boolean = true
) {
    /** 当前厂商预设 */
    val provider: AiProvider get() = AiProviders.byId(providerId)

    /** 配置是否完整可用 */
    val isValid: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() &&
            (apiKey.isNotBlank() || !provider.requiresKey)

    /** 拼接完整的 chat 地址 */
    val chatEndpoint: String
        get() {
            val base = baseUrl.trimEnd('/')
            // 用户可能直接把完整 chat 地址粘进来了，此时不要再拼
            if (base.endsWith("/chat/completions") || base.endsWith("/completions")) return base
            val path = provider.chatPath
            return if (base.endsWith(path)) base else "$base$path"
        }
}
