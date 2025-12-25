package win.liuping.aijudge.data.repository

import android.content.Context
import androidx.core.content.edit
import win.liuping.aijudge.data.model.AppSettings
import win.liuping.aijudge.data.model.LlmProvider

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ai_judge_settings", Context.MODE_PRIVATE)

    fun saveSettings(settings: AppSettings) {
        prefs.edit {
            putString("llmApiKey", settings.llmApiKey)
            putString("llmEndpoint", settings.llmEndpoint)
            putString("llmModel", settings.llmModel)
            putString("sttModelPath", settings.sttModelPath)
            putString("ttsModelPath", settings.ttsModelPath)
            putString("systemPrompt", settings.systemPrompt)
            putString("llmProvider", settings.llmProvider.name)
        }
    }

    fun getSettings(): AppSettings {
        return AppSettings(
            llmApiKey = prefs.getString("llmApiKey", "") ?: "",
            llmEndpoint = prefs.getString("llmEndpoint", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            llmModel = prefs.getString("llmModel", "gpt-3.5-turbo") ?: "gpt-3.5-turbo",
            sttModelPath = prefs.getString("sttModelPath", "") ?: "",
            ttsModelPath = prefs.getString("ttsModelPath", "") ?: "",
            systemPrompt = prefs.getString("systemPrompt", "You are an AI Judge. You listen to the environment and give short, witty judgments.") ?: "You are an AI Judge. You listen to the environment and give short, witty judgments.",
            llmProvider = try {
                LlmProvider.valueOf(prefs.getString("llmProvider", LlmProvider.OPENAI.name) ?: LlmProvider.OPENAI.name)
            } catch (e: Exception) {
                LlmProvider.OPENAI
            }
        )
    }
}
