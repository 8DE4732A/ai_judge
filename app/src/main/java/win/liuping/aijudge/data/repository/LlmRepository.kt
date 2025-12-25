package win.liuping.aijudge.data.repository

import android.util.Log
import win.liuping.aijudge.data.model.AppSettings
import win.liuping.aijudge.data.network.LlmApi
import win.liuping.aijudge.data.network.model.ChatCompletionRequest
import win.liuping.aijudge.data.network.model.ChatMessage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class LlmRepository {

    private var currentApi: LlmApi? = null
    private var currentTimeout: Long = -1

    private fun getApi(timeoutSeconds: Long): LlmApi {
        if (currentApi != null && currentTimeout == timeoutSeconds) {
            return currentApi!!
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // Default connect timeout
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/") 
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val newApi = retrofit.create(LlmApi::class.java)
        currentApi = newApi
        currentTimeout = timeoutSeconds
        return newApi
    }

    suspend fun getJudgeResponse(
        conversation: List<ChatMessage>,
        settings: AppSettings
    ): String {
        return try {
            val api = getApi(settings.llmTimeoutSeconds)

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
