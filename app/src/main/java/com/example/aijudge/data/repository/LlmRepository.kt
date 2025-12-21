package com.example.aijudge.data.repository

import android.util.Log
import com.example.aijudge.data.model.AppSettings
import com.example.aijudge.data.network.LlmApi
import com.example.aijudge.data.network.model.ChatCompletionRequest
import com.example.aijudge.data.network.model.ChatMessage
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

            val response = api.chatCompletion(url, auth, request)
            response.choices.firstOrNull()?.message?.content ?: "I have nothing to say."
        } catch (e: Exception) {
            Log.e("LlmRepository", "Error api call", e)
            "Error judging: ${e.message}"
        }
    }
}
