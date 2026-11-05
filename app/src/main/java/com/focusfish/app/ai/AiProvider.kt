package com.focusfish.app.ai

/**
 * AI 服务商预设
 *
 * 大肥鱼不绑定任何一家厂商：所有走 OpenAI 兼容协议（`POST {base}{path}` + `Authorization: Bearer`）
 * 的服务都能接。用户在「设置 → 大肥鱼 AI」里选一个厂商填密钥即可，也可以选「自定义」手填地址。
 *
 * 为什么要预设而不是让用户抄地址：
 * - 各家 baseUrl 的路径前缀差别很大（有的要 /v1，有的要 /compatible-mode/v1，有的要 /api/paas/v4），
 *   抄错一个字符就是 404，用户很难自己排查。
 * - 部分厂商的 chat 路径不叫 /chat/completions（MiniMax 是 /text/chatcompletion_v2），
 *   这类差异无法靠"把地址粘对"解决。
 */
data class AiProvider(
    /** 唯一 id，持久化用 */
    val id: String,
    /** 展示名 */
    val displayName: String,
    /** 接口根地址（不含 chat 路径） */
    val baseUrl: String,
    /** chat 路径（相对 baseUrl） */
    val chatPath: String = "/chat/completions",
    /** 建议的默认模型 */
    val defaultModel: String,
    /** 备选模型，供用户在设置页一键切换 */
    val modelHints: List<String> = emptyList(),
    /** token 上限参数名：老模型用 max_tokens，o 系列用 max_completion_tokens */
    val tokenParam: String = "max_tokens",
    /** 是否支持 temperature 参数（推理模型只接受默认值） */
    val supportsTemperature: Boolean = true,
    /** 备注，显示在设置页 */
    val note: String = "",
    /** 是否需要 API Key（本地 Ollama 可留空） */
    val requiresKey: Boolean = true
)

/**
 * 内置厂商清单
 */
object AiProviders {

    const val CUSTOM_ID = "custom"

    /** DeepSeek 官方 */
    val DEEPSEEK = AiProvider(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash",
        modelHints = listOf("deepseek-flash", "deepseek-chat", "deepseek-reasoner"),
        note = "国产大肥鱼的原生栖身地。deepseek-chat 已于 2026-07-24 停用，默认用 deepseek-flash。"
    )

    /** 月之暗面 Kimi */
    val MOONSHOT = AiProvider(
        id = "moonshot",
        displayName = "Kimi（月之暗面）",
        baseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "kimi-k2-turbo-preview",
        modelHints = listOf(
            "kimi-k2-turbo-preview",
            "kimi-k2-0905-preview",
            "moonshot-v1-128k",
            "moonshot-v1-32k"
        ),
        note = "长上下文强，读论文最合适。"
    )

    /** 阿里通义千问（DashScope 兼容模式） */
    val QWEN = AiProvider(
        id = "qwen",
        displayName = "通义千问（阿里）",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        modelHints = listOf("qwen-plus", "qwen-turbo", "qwen-max", "qwen-long"),
        note = "免费额度大，长文理解稳。"
    )

    /** 智谱 GLM */
    val ZHIPU = AiProvider(
        id = "zhipu",
        displayName = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4-flash",
        modelHints = listOf("glm-4-flash", "glm-4-air", "glm-4-plus", "glm-4.5-flash"),
        note = "glm-4-flash 免费，适合长时间挂着。"
    )

    /** MiniMax */
    val MINIMAX = AiProvider(
        id = "minimax",
        displayName = "MiniMax",
        baseUrl = "https://api.minimaxi.com/v1",
        chatPath = "/text/chatcompletion_v2",
        defaultModel = "MiniMax-M2",
        modelHints = listOf("MiniMax-M2", "MiniMax-Text-01", "abab6.5s-chat"),
        note = "路径不是 /chat/completions，已经帮你填好了。"
    )

    /** 腾讯混元 */
    val HUNYUAN = AiProvider(
        id = "hunyuan",
        displayName = "腾讯混元",
        baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        defaultModel = "hunyuan-turbos-latest",
        modelHints = listOf("hunyuan-turbos-latest", "hunyuan-turbo", "hunyuan-standard"),
        note = "开了腾讯云就能直接用。"
    )

    /** 字节豆包（火山方舟） */
    val DOUBAO = AiProvider(
        id = "doubao",
        displayName = "豆包（火山方舟）",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-1-5-pro-32k",
        modelHints = listOf(
            "doubao-1-5-pro-32k",
            "doubao-1-5-pro",
            "doubao-1-5-lite-32k",
            "doubao-1-5-thinking-pro"
        ),
        note = "模型名要填在方舟控制台创建好的推理接入点 ID，也可以直接填模型代号。"
    )

    /** 百度千帆 */
    val QIANFAN = AiProvider(
        id = "qianfan",
        displayName = "百度千帆",
        baseUrl = "https://qianfan.baidubce.com/v2",
        defaultModel = "ernie-4.5-turbo-128k",
        modelHints = listOf("ernie-4.5-turbo-128k", "ernie-4.5-turbo", "ernie-4.0-8k"),
        note = ""
    )

    /** 讯飞星火 */
    val SPARK = AiProvider(
        id = "spark",
        displayName = "讯飞星火",
        baseUrl = "https://spark-api-open.xf-yun.com/v1",
        defaultModel = "spark-max",
        modelHints = listOf("spark-max", "spark-max-128k", "spark-lite"),
        note = ""
    )

    /** 硅基流动（聚合站，DeepSeek/Qwen 都有，便宜） */
    val SILICONFLOW = AiProvider(
        id = "siliconflow",
        displayName = "硅基流动 SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "deepseek-ai/DeepSeek-V3",
        modelHints = listOf(
            "deepseek-ai/DeepSeek-V3",
            "deepseek-ai/DeepSeek-R1",
            "Qwen/Qwen2.5-7B-Instruct",
            "moonshotai/Kimi-K2-Instruct"
        ),
        note = "一个 Key 打通多家模型，适合薅免费额度。"
    )

    /** OpenAI 官方 */
    val OPENAI = AiProvider(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        modelHints = listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4o", "gpt-5-mini"),
        tokenParam = "max_completion_tokens",
        supportsTemperature = false,
        note = "o 系列 / gpt-5 只接受默认 temperature，已自动处理。"
    )

    /** Groq 高速推理 */
    val GROQ = AiProvider(
        id = "groq",
        displayName = "Groq（极速）",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        modelHints = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen/qwen3-32b"
        ),
        note = "免费额度/token 都很猛，缺点是大肥鱼说话没那么机灵。"
    )

    /** 本地 Ollama（离线，不联网） */
    val OLLAMA = AiProvider(
        id = "ollama",
        displayName = "本地 Ollama（离线）",
        baseUrl = "http://10.0.2.2:11434/v1",
        defaultModel = "qwen3:8b",
        modelHints = listOf("qwen3:8b", "qwen2.5:7b-instruct", "llama3.2:3b", "deepseek-r1:7b"),
        requiresKey = false,
        note = "真机请改成电脑局域网 IP（模拟器用 10.0.2.2 直连本机）。密钥随便填一个即可。"
    )

    /** 自定义 */
    val CUSTOM = AiProvider(
        id = CUSTOM_ID,
        displayName = "自定义（OpenAI 兼容）",
        baseUrl = "",
        defaultModel = "",
        note = "任何 OpenAI 兼容端点都能接，路径自己填。"
    )

    /** 全部预设，按常见程度排序 */
    val ALL: List<AiProvider> = listOf(
        DEEPSEEK,
        MOONSHOT,
        QWEN,
        ZHIPU,
        MINIMAX,
        SILICONFLOW,
        DOUBAO,
        HUNYUAN,
        QIANFAN,
        SPARK,
        OPENAI,
        GROQ,
        OLLAMA,
        CUSTOM
    )

    /** 按 id 取预设 */
    fun byId(id: String?): AiProvider =
        ALL.firstOrNull { it.id == id } ?: CUSTOM
}
