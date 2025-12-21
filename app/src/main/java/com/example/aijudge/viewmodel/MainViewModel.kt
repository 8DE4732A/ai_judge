package com.example.aijudge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aijudge.data.model.AppSettings
import com.example.aijudge.data.model.Message
import com.example.aijudge.data.model.Sender
import com.example.aijudge.speech.SttManager
import com.example.aijudge.speech.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val sttManager = SttManager(application)
    private val ttsManager = TtsManager(application)
    private val llmRepository = com.example.aijudge.data.repository.LlmRepository()

    init {
        // Add a welcome message
        addMessage("Welcome to AI Judge. Please check settings and start listening.", Sender.SYSTEM)
        
        // Initialize speech engines (async ideally)
        viewModelScope.launch {
            sttManager.initRecognizer()
            ttsManager.initTts()
        }
    }

    fun addMessage(content: String, sender: Sender) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            sender = sender,
            content = content
        )
        _messages.update { it + message }
        
        // If message is from Judge, speak it
        if (sender == Sender.JUDGE) {
            ttsManager.speak(content)
        }
    }

    private fun processJudgeResponse() {
        viewModelScope.launch {
            val currentSettings = _settings.value
            // Build conversation history
            val chatMessages = mutableListOf<com.example.aijudge.data.network.model.ChatMessage>()
            chatMessages.add(com.example.aijudge.data.network.model.ChatMessage("system", currentSettings.systemPrompt))
            
            // Take last 10 messages for context
            val recentMessages = _messages.value.takeLast(10)
            recentMessages.forEach { msg ->
                val role = when (msg.sender) {
                    Sender.USER -> "user"
                    Sender.JUDGE -> "assistant"
                    Sender.SYSTEM -> "system" // Usually skip system messages in context or map to system
                }
                if (msg.sender != Sender.SYSTEM) {
                    chatMessages.add(com.example.aijudge.data.network.model.ChatMessage(role, msg.content))
                }
            }
            
            val response = llmRepository.getJudgeResponse(chatMessages, currentSettings)
            addMessage(response, Sender.JUDGE)
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        // TODO: Save to encryption/preferences and re-init engines if paths changed
    }

    fun toggleListening() {
        val newState = !_isListening.value
        _isListening.value = newState
        
        if (newState) {
            sttManager.startListening { text ->
                // Callback from STT
                if (text.isNotBlank()) {
                     viewModelScope.launch {
                         addMessage(text, Sender.USER)
                         processJudgeResponse() 
                     }
                }
            }
            addMessage("Started listening...", Sender.SYSTEM)
        } else {
            sttManager.stopListening()
            addMessage("Stopped listening.", Sender.SYSTEM)
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.release()
        ttsManager.release()
    }
}
