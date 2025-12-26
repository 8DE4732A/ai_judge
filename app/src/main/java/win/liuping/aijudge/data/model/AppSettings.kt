package win.liuping.aijudge.data.model

data class AppSettings(
    val llmApiKey: String = "",
    val llmEndpoint: String = "https://api.openai.com/v1",
    val llmModel: String = "gpt-3.5-turbo",
    val sttModelPath: String = "", // Path or identifier for Sherpa model
    val ttsModelPath: String = "",
    val speakerDiarizationModelPath: String = "",
    val punctuationModelPath: String = "",
    val systemPrompt: String = "You are an AI Judge. You listen to the environment and give short, witty judgments.",
    val llmProvider: LlmProvider = LlmProvider.OPENAI,
    val llmTimeoutSeconds: Long = 60,
    val sttEndpointTimeout: Float = 1.5f // Seconds of silence to consider speech ended
)

enum class LlmProvider(val displayName: String, val defaultEndpoint: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-3.5-turbo"),
    CLAUDE("Claude (Via Proxy)", "https://api.anthropic.com/v1", "claude-3-opus-20240229"), // Placeholder, requires proxy for OpenAI format
    GEMINI("Gemini (Via Proxy)", "https://generativelanguage.googleapis.com/v1beta", "gemini-pro"), // Placeholder
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    DOUBAO("Doubao", "https://ark.cn-beijing.volces.com/api/v3", "doubao-pro-32k"),
    XIAOMI("Xiaomi", "https://api.siliconflow.cn/v1", "mimo-v2-flash"), // Assumption based on common hosting
    CUSTOM("Custom", "", "")
}

enum class ModelLoadStatus {
    NOT_DOWNLOADED,  // Model files not present
    LOADING,         // Currently loading
    LOADED,          // Successfully loaded
    FAILED           // Failed to load
}
