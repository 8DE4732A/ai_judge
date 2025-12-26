package win.liuping.aijudge.data.repository

import android.content.Context
import androidx.core.content.edit
import win.liuping.aijudge.data.model.AppSettings
import win.liuping.aijudge.data.model.LlmProvider

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ai_judge_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOADING_MODEL = "currently_loading_model"
        private const val KEY_CRASH_COUNT_PREFIX = "crash_count_"
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit {
            putString("llmApiKey", settings.llmApiKey)
            putString("llmEndpoint", settings.llmEndpoint)
            putString("llmModel", settings.llmModel)
            putString("sttModelPath", settings.sttModelPath)
            putString("ttsModelPath", settings.ttsModelPath)
            putString("speakerDiarizationModelPath", settings.speakerDiarizationModelPath)
            putString("punctuationModelPath", settings.punctuationModelPath)
            putString("systemPrompt", settings.systemPrompt)
            putString("llmProvider", settings.llmProvider.name)
            putLong("llmTimeoutSeconds", settings.llmTimeoutSeconds)
        }
    }

    fun getSettings(): AppSettings {
        return AppSettings(
            llmApiKey = prefs.getString("llmApiKey", "") ?: "",
            llmEndpoint = prefs.getString("llmEndpoint", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            llmModel = prefs.getString("llmModel", "gpt-3.5-turbo") ?: "gpt-3.5-turbo",
            sttModelPath = prefs.getString("sttModelPath", "") ?: "",
            ttsModelPath = prefs.getString("ttsModelPath", "") ?: "",
            speakerDiarizationModelPath = prefs.getString("speakerDiarizationModelPath", "") ?: "",
            punctuationModelPath = prefs.getString("punctuationModelPath", "") ?: "",
            systemPrompt = prefs.getString("systemPrompt", "You are an AI Judge. You listen to the environment and give short, witty judgments.") ?: "You are an AI Judge. You listen to the environment and give short, witty judgments.",
            llmProvider = try {
                LlmProvider.valueOf(prefs.getString("llmProvider", LlmProvider.OPENAI.name) ?: LlmProvider.OPENAI.name)
            } catch (e: Exception) {
                LlmProvider.OPENAI
            },
            llmTimeoutSeconds = prefs.getLong("llmTimeoutSeconds", 60),
            sttEndpointTimeout = prefs.getFloat("sttEndpointTimeout", 1.5f)
        )
    }

    // Crash recovery: mark model as loading before init
    fun markModelLoading(modelName: String) {
        prefs.edit { putString(KEY_LOADING_MODEL, modelName) }
    }

    // Crash recovery: clear loading mark after successful init
    fun markModelLoaded() {
        prefs.edit { remove(KEY_LOADING_MODEL) }
    }

    // Check if last crash was during model loading
    fun checkAndRecordCrash(): String? {
        val crashedModel = prefs.getString(KEY_LOADING_MODEL, null)
        if (crashedModel != null) {
            // Increment crash count for this model
            val crashCount = prefs.getInt(KEY_CRASH_COUNT_PREFIX + crashedModel, 0) + 1
            prefs.edit {
                putInt(KEY_CRASH_COUNT_PREFIX + crashedModel, crashCount)
                remove(KEY_LOADING_MODEL)
            }
        }
        return crashedModel
    }

    // Check if model should be skipped due to repeated crashes
    fun shouldSkipModel(modelName: String): Boolean {
        val crashCount = prefs.getInt(KEY_CRASH_COUNT_PREFIX + modelName, 0)
        return crashCount >= 2 // Skip after 2 crashes
    }

    // Reset crash count for a model (e.g., after re-download)
    fun resetCrashCount(modelName: String) {
        prefs.edit { remove(KEY_CRASH_COUNT_PREFIX + modelName) }
    }
}
