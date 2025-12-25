package win.liuping.aijudge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import win.liuping.aijudge.R
import win.liuping.aijudge.data.model.AppSettings
import win.liuping.aijudge.data.model.Message
import win.liuping.aijudge.data.model.Sender
import win.liuping.aijudge.speech.SttManager
import win.liuping.aijudge.speech.TtsManager
import win.liuping.aijudge.util.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.Dispatchers

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val settingsRepository = win.liuping.aijudge.data.repository.SettingsRepository(application)
    private val _settings = MutableStateFlow(settingsRepository.getSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val sttManager = SttManager(application)
    private val ttsManager = TtsManager(application)
    private val llmRepository = win.liuping.aijudge.data.repository.LlmRepository()

    private val _sttDownloadStatus = MutableStateFlow<ModelDownloadManager.DownloadStatus?>(null)
    val sttDownloadStatus = _sttDownloadStatus.asStateFlow()

    private val _ttsDownloadStatus = MutableStateFlow<ModelDownloadManager.DownloadStatus?>(null)
    val ttsDownloadStatus = _ttsDownloadStatus.asStateFlow()

    init {
        // Add a welcome message
        addMessage(getApplication<Application>().getString(R.string.msg_welcome), Sender.SYSTEM)
        
        // Initialize speech engines (async ideally)
        // Initialize speech engines if models exist
        viewModelScope.launch {
            val context = getApplication<Application>()
            
            // Check if we have saved paths or default paths
            var currentSttPath = _settings.value.sttModelPath
            val sttDir = File(context.filesDir, "stt-model")
            if (sttDir.exists() && sttDir.listFiles()?.isNotEmpty() == true) {
                 currentSttPath = sttDir.absolutePath
            }
            
            if (currentSttPath.isNotBlank()) {
                 sttManager.initRecognizer(currentSttPath)
                 _settings.update { it.copy(sttModelPath = currentSttPath) }
            }

            var currentTtsPath = _settings.value.ttsModelPath
            val ttsDir = File(context.filesDir, "tts-model")
            if (ttsDir.exists() && ttsDir.listFiles()?.isNotEmpty() == true) {
                 currentTtsPath = ttsDir.absolutePath
            }
            
            if (currentTtsPath.isNotBlank()) {
                 ttsManager.initTts(currentTtsPath)
                 _settings.update { it.copy(ttsModelPath = currentTtsPath) }
            }
            
            // Persist potential path updates
            settingsRepository.saveSettings(_settings.value)
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
            viewModelScope.launch(Dispatchers.IO) {
                ttsManager.speak(content)
            }
        }
    }

    fun reSpeakMessage(message: Message) {
        if (message.sender == Sender.JUDGE) {
            viewModelScope.launch(Dispatchers.IO) {
                ttsManager.speak(message.content)
            }
        }
    }

    private fun processJudgeResponse() {
        viewModelScope.launch {
            val currentSettings = _settings.value
            // Build conversation history
            val chatMessages = mutableListOf<win.liuping.aijudge.data.network.model.ChatMessage>()
            chatMessages.add(win.liuping.aijudge.data.network.model.ChatMessage("system", currentSettings.systemPrompt))
            
            // Take last 10 messages for context
            val recentMessages = _messages.value.takeLast(10)
            recentMessages.forEach { msg ->
                val role = when (msg.sender) {
                    Sender.USER -> "user"
                    Sender.JUDGE -> "assistant"
                    Sender.SYSTEM -> "system" // Usually skip system messages in context or map to system
                }
                if (msg.sender != Sender.SYSTEM) {
                    chatMessages.add(win.liuping.aijudge.data.network.model.ChatMessage(role, msg.content))
                }
            }
            
            val response = llmRepository.getJudgeResponse(chatMessages, currentSettings)
            addMessage(response, Sender.JUDGE)
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        settingsRepository.saveSettings(newSettings)
    }

    fun downloadSttModel() {
        viewModelScope.launch {
            ModelDownloadManager.downloadModel(
                getApplication(),
                ModelDownloadManager.STT_FILES,
                "stt-model"
            ).collect { status ->
                _sttDownloadStatus.value = status
                if (status is ModelDownloadManager.DownloadStatus.Completed) {
                    val path = File(getApplication<Application>().filesDir, "stt-model").absolutePath
                     _settings.update { it.copy(sttModelPath = path) }
                     sttManager.release()
                     sttManager.initRecognizer(path)
                }
            }
        }
    }

    fun downloadTtsModel() {
        viewModelScope.launch {
            ModelDownloadManager.downloadModel(
                getApplication(),
                ModelDownloadManager.TTS_FILES,
                "tts-model"
            ).collect { status ->
                _ttsDownloadStatus.value = status
                if (status is ModelDownloadManager.DownloadStatus.Completed) {
                    val path = File(getApplication<Application>().filesDir, "tts-model").absolutePath
                     _settings.update { it.copy(ttsModelPath = path) }
                     ttsManager.release()
                     ttsManager.initTts(path)
                }
            }
        }
    }

    private var currentMessageId: String? = null

    fun toggleListening() {
        if (_settings.value.sttModelPath.isBlank() || !sttManager.isReady()) {
            addMessage(getApplication<Application>().getString(R.string.msg_stt_not_ready), Sender.SYSTEM)
            return
        }

        val newState = !_isListening.value
        _isListening.value = newState
        
        if (newState) {
            sttManager.startListening { text, isEndpoint ->
                viewModelScope.launch {
                    if (text.isNotBlank()) {
                        if (currentMessageId == null) {
                            val newMessage = Message(
                                id = UUID.randomUUID().toString(),
                                sender = Sender.USER,
                                content = text
                            )
                            currentMessageId = newMessage.id
                            _messages.update { it + newMessage }
                        } else {
                            _messages.update { list ->
                                list.map { 
                                    if (it.id == currentMessageId) it.copy(content = text) else it 
                                }
                            }
                        }

                        if (isEndpoint) {
                            Log.d("MainViewModel", "Endpoint detected, processing: $text")
                            currentMessageId = null
                            processJudgeResponse()
                        }
                    }
                }
            }
            addMessage(getApplication<Application>().getString(R.string.msg_listening_started), Sender.SYSTEM)
        } else {
            sttManager.stopListening()
            currentMessageId = null
            addMessage(getApplication<Application>().getString(R.string.msg_listening_stopped), Sender.SYSTEM)
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
