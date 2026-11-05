package com.focusfish.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI 兼容 LLM 客户端
 *
 * 用 [HttpURLConnection] 手写而非引入 Retrofit/OkHttp：
 * 请求形态固定（POST + JSON + SSE 流式），依赖越少 APK 越小、离线构建更稳。
 *
 * 支持两种模式：
 * - [chatStream]：SSE 流式，边收边回调，用于对话页的打字机效果
 * - [chatOnce]：一次性返回完整文本，用于主动行为等不需要打字的场景
 */
object LlmClient {

    /**
     * 流式对话
     *
     * @param config 用户配置
     * @param messages 消息列表，role/content 的 Map 列表
     * @param onChunk 每收到一段增量文本回调一次（在 IO 线程，调用方自行切回主线程）
     * @return 完整回复文本
     */
    suspend fun chatStream(
        config: AiConfig,
        messages: List<Pair<String, String>>,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val connection = openConnection(config)
        try {
            connection.outputStream.use { out ->
                out.write(buildRequestBody(config, messages, stream = true).toByteArray())
                out.flush()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.let { readAll(it) }.orEmpty()
                throw AiException("HTTP $status: ${errorBody.take(300)}")
            }

            val builder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                // SSE 格式：data: {json}\n，空行表示事件结束，[DONE] 表示流结束
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue

                val delta = parseDelta(payload) ?: continue
                if (delta.isNotEmpty()) {
                    builder.append(delta)
                    onChunk(delta)
                }
            }
            builder.toString()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 一次性对话（非流式）
     */
    suspend fun chatOnce(
        config: AiConfig,
        messages: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        val connection = openConnection(config)
        try {
            connection.outputStream.use { out ->
                out.write(buildRequestBody(config, messages, stream = false).toByteArray())
                out.flush()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.let { readAll(it) }.orEmpty()
                throw AiException("HTTP $status: ${errorBody.take(300)}")
            }

            val text = readAll(connection.inputStream)
            // 非流式响应：choices[0].message.content
            return@withContext runCatching {
                val json = JSONObject(text)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")
                        ?.optString("content").orEmpty()
                } else {
                    ""
                }
            }.getOrElse { throw AiException("响应解析失败: ${it.message}") }
        } finally {
            connection.disconnect()
        }
    }

    /** 解析 SSE 单条增量，返回文本增量 */
    private fun parseDelta(payload: String): String? {
        return runCatching {
            val json = JSONObject(payload)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) return null
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null
            delta.optString("content").takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun openConnection(config: AiConfig): HttpURLConnection {
        val url = URL(config.chatEndpoint)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Accept", "application/json, text/event-stream")
        }
    }

    private fun buildRequestBody(
        config: AiConfig,
        messages: List<Pair<String, String>>,
        stream: Boolean
    ): String {
        val messagesArray = JSONArray()
        messages.forEach { (role, content) ->
            messagesArray.put(
                JSONObject()
                    .put("role", role)
                    .put("content", content)
            )
        }
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", messagesArray)
            .put("stream", stream)

        // token 上限参数名：老模型 max_tokens，o 系列 / gpt-5 用 max_completion_tokens
        body.put(tokenParamOf(config), config.maxTokens)

        // temperature：推理模型（o 系列 / gpt-5 / deepseek-reasoner）只接受默认值，
        // 显式传 temperature 会被 400 拒绝，所以这类模型干脆不传这个字段。
        if (supportsTemperature(config)) {
            body.put("temperature", config.temperature)
        }

        return body.toString()
    }

    /**
     * token 上限参数名。
     *
     * 除了厂商预设外，还要看模型名 —— 同一个厂商下既有老模型也有 o 系列，
     * 只按厂商判断会漏。
     */
    private fun tokenParamOf(config: AiConfig): String {
        if (isReasoningModel(config.model)) return "max_completion_tokens"
        return config.provider.tokenParam
    }

    /** 是否可以传 temperature */
    private fun supportsTemperature(config: AiConfig): Boolean {
        if (!config.provider.supportsTemperature) return false
        return !isReasoningModel(config.model)
    }

    /** 判断是否是只接受默认 temperature 的推理模型 */
    private fun isReasoningModel(model: String): Boolean {
        val m = model.lowercase()
        // o1/o3/o4 系列、gpt-5 系列
        if (Regex("^(o1|o3|o4)[-/]").containsMatchIn(m)) return true
        if (m.startsWith("gpt-5")) return true
        // deepseek-reasoner 及其变体
        if (m.contains("deepseek-reasoner")) return true
        return false
    }

    private fun readAll(stream: java.io.InputStream): String {
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

/**
 * AI 调用异常
 */
class AiException(message: String) : Exception(message)
