package win.liuping.aijudge.data.repository

import android.util.Log
import win.liuping.aijudge.data.model.AppSettings
import win.liuping.aijudge.data.network.LlmApi
import win.liuping.aijudge.data.network.model.ChatCompletionRequest
import win.liuping.aijudge.data.network.model.ChatMessage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LlmRepository {

    private val api: LlmApi

    init {
        // Base URL doesn't matter much as we pass @Url, but Retrofit needs one.
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/") 
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(LlmApi::class.java)
    }

    suspend fun getJudgeResponse(
        conversation: List<ChatMessage>,
        settings: AppSettings
    ): String {
        return try {
            val url = if (settings.llmEndpoint.endsWith("/")) {
                settings.llmEndpoint + "chat/completions"
            } else {
                "${settings.llmEndpoint}/chat/completions"
            }

            val request = ChatCompletionRequest(
                model = settings.llmModel,
                messages = conversation
            )

            val auth = if (settings.llmApiKey.startsWith("Bearer ")) {
                settings.llmApiKey
            } else {
                "Bearer ${settings.llmApiKey}"
            }

            Log.d("LlmRepository", "Calling LLM API: $url")
            Log.d("LlmRepository", "Headers: Authorization=${auth.take(15)}... (masked)")
            Log.d("LlmRepository", "Request: $request")

            try {
                val response = api.chatCompletion(url, auth, request)
                Log.d("LlmRepository", "LLM Response received: ${response.choices.size} choices")
                response.choices.firstOrNull()?.message?.content ?: "I have nothing to say."
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("LlmRepository", "HTTP Error ${e.code()}: $errorBody", e)
                "Error judging: HTTP ${e.code()} - $errorBody"
            }
        } catch (e: Exception) {
            Log.e("LlmRepository", "Error api call to: ${settings.llmEndpoint}", e)
            "Error judging: ${e.message}"
        }
    }
}
