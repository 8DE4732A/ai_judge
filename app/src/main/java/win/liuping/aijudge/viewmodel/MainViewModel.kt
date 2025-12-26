package win.liuping.aijudge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import win.liuping.aijudge.R
import win.liuping.aijudge.data.model.AppSettings
import win.liuping.aijudge.data.model.Message
import win.liuping.aijudge.data.model.ModelLoadStatus
import win.liuping.aijudge.data.model.Sender
import win.liuping.aijudge.data.model.Session
import win.liuping.aijudge.data.repository.SessionRepository
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
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Session management
    private val sessionRepository = SessionRepository(application)
    private val _currentSession = MutableStateFlow(Session())
    val currentSession: StateFlow<Session> = _currentSession.asStateFlow()

    private val _sessionList = MutableStateFlow<List<Session>>(emptyList())
    val sessionList: StateFlow<List<Session>> = _sessionList.asStateFlow()

    private val _showSessionList = MutableStateFlow(false)
    val showSessionList: StateFlow<Boolean> = _showSessionList.asStateFlow()

    private val _showSpeakerAliasDialog = MutableStateFlow(false)
    val showSpeakerAliasDialog: StateFlow<Boolean> = _showSpeakerAliasDialog.asStateFlow()

    // Messages are now derived from current session
    val messages: StateFlow<List<Message>> get() = MutableStateFlow(_currentSession.value.messages).asStateFlow()

    private val settingsRepository = win.liuping.aijudge.data.repository.SettingsRepository(application)
    private val _settings = MutableStateFlow(settingsRepository.getSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isJudging = MutableStateFlow(false)
    val isJudging: StateFlow<Boolean> = _isJudging.asStateFlow()

    private val _isOrganizing = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    private val _organizationResult = MutableStateFlow<String?>(null)
    val organizationResult: StateFlow<String?> = _organizationResult.asStateFlow()

    private val sttManager = SttManager(application)
    private val ttsManager = TtsManager(application)
    private val llmRepository = win.liuping.aijudge.data.repository.LlmRepository()

    private val _sttDownloadStatus = MutableStateFlow<ModelDownloadManager.DownloadStatus?>(null)
    val sttDownloadStatus = _sttDownloadStatus.asStateFlow()

    private val _ttsDownloadStatus = MutableStateFlow<ModelDownloadManager.DownloadStatus?>(null)
    val ttsDownloadStatus = _ttsDownloadStatus.asStateFlow()

    private val _diarizationDownloadStatus = MutableStateFlow<ModelDownloadManager.DownloadStatus?>(null)
    val diarizationDownloadStatus = _diarizationDownloadStatus.asStateFlow()

    private val _punctuationDownloadStatus = MutableStateFlow<ModelDownloadManager.DownloadStatus?>(null)
    val punctuationDownloadStatus = _punctuationDownloadStatus.asStateFlow()

    // Model load status
    private val _sttLoadStatus = MutableStateFlow(ModelLoadStatus.NOT_DOWNLOADED)
    val sttLoadStatus: StateFlow<ModelLoadStatus> = _sttLoadStatus.asStateFlow()

    private val _ttsLoadStatus = MutableStateFlow(ModelLoadStatus.NOT_DOWNLOADED)
    val ttsLoadStatus: StateFlow<ModelLoadStatus> = _ttsLoadStatus.asStateFlow()

    private val _diarizationLoadStatus = MutableStateFlow(ModelLoadStatus.NOT_DOWNLOADED)
    val diarizationLoadStatus: StateFlow<ModelLoadStatus> = _diarizationLoadStatus.asStateFlow()

    private val _punctuationLoadStatus = MutableStateFlow(ModelLoadStatus.NOT_DOWNLOADED)
    val punctuationLoadStatus: StateFlow<ModelLoadStatus> = _punctuationLoadStatus.asStateFlow()

    init {
        // Load session list and last session
        viewModelScope.launch {
            loadSessionList()
            // Load last session or create new
            val sessions = sessionRepository.getAllSessions()
            if (sessions.isNotEmpty()) {
                _currentSession.value = sessions.first()
            } else {
                createNewSession()
            }
        }

        // Check for crash during last model loading
        val crashedModel = settingsRepository.checkAndRecordCrash()
        if (crashedModel != null) {
            Log.w("MainViewModel", "Detected crash during loading of model: $crashedModel")
        }

        // Initialize speech engines asynchronously on IO thread
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

            // STT Model
            if (!settingsRepository.shouldSkipModel("stt")) {
                var currentSttPath = _settings.value.sttModelPath
                val sttDir = File(context.filesDir, "stt-model")
                if (sttDir.exists() && sttDir.listFiles()?.isNotEmpty() == true) {
                     currentSttPath = sttDir.absolutePath
                }

                if (currentSttPath.isNotBlank()) {
                    _sttLoadStatus.value = ModelLoadStatus.LOADING
                    settingsRepository.markModelLoading("stt")
                    val success = sttManager.initRecognizer(currentSttPath, _settings.value.sttEndpointTimeout)
                    settingsRepository.markModelLoaded()
                    _sttLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                    if (success) {
                        _settings.update { it.copy(sttModelPath = currentSttPath) }
                    }
                }
            } else {
                Log.w("MainViewModel", "Skipping STT model due to repeated crashes")
                _sttLoadStatus.value = ModelLoadStatus.FAILED
            }

            // TTS Model
            if (!settingsRepository.shouldSkipModel("tts")) {
                var currentTtsPath = _settings.value.ttsModelPath
                val ttsDir = File(context.filesDir, "tts-model")
                if (ttsDir.exists() && ttsDir.listFiles()?.isNotEmpty() == true) {
                     currentTtsPath = ttsDir.absolutePath
                }

                if (currentTtsPath.isNotBlank()) {
                    _ttsLoadStatus.value = ModelLoadStatus.LOADING
                    settingsRepository.markModelLoading("tts")
                    val success = ttsManager.initTts(currentTtsPath)
                    settingsRepository.markModelLoaded()
                    _ttsLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                    if (success) {
                        _settings.update { it.copy(ttsModelPath = currentTtsPath) }
                    }
                }
            } else {
                Log.w("MainViewModel", "Skipping TTS model due to repeated crashes")
                _ttsLoadStatus.value = ModelLoadStatus.FAILED
            }

            // Diarization Model
            if (!settingsRepository.shouldSkipModel("diarization")) {
                var currentDiarizationPath = _settings.value.speakerDiarizationModelPath
                val diarizationDir = File(context.filesDir, "diarization-model")
                val diarizationFile = File(diarizationDir, "speaker_model.onnx")

                if (diarizationFile.exists()) {
                     currentDiarizationPath = diarizationFile.absolutePath
                }

                if (currentDiarizationPath.isNotBlank()) {
                    _diarizationLoadStatus.value = ModelLoadStatus.LOADING
                    settingsRepository.markModelLoading("diarization")
                    val success = sttManager.initSpeakerRecognizer(currentDiarizationPath)
                    settingsRepository.markModelLoaded()
                    _diarizationLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                    if (success) {
                        _settings.update { it.copy(speakerDiarizationModelPath = currentDiarizationPath) }
                    }
                }
            } else {
                Log.w("MainViewModel", "Skipping Diarization model due to repeated crashes")
                _diarizationLoadStatus.value = ModelLoadStatus.FAILED
            }

            // Punctuation Model
            if (!settingsRepository.shouldSkipModel("punctuation")) {
                var currentPunctuationPath = _settings.value.punctuationModelPath
                val punctuationDir = File(context.filesDir, "punctuation-model")
                val punctuationFile = File(punctuationDir, "model.onnx")

                if (punctuationFile.exists()) {
                     currentPunctuationPath = punctuationFile.absolutePath
                }

                if (currentPunctuationPath.isNotBlank()) {
                    _punctuationLoadStatus.value = ModelLoadStatus.LOADING
                    settingsRepository.markModelLoading("punctuation")
                    val success = sttManager.initPunctuation(currentPunctuationPath)
                    settingsRepository.markModelLoaded()
                    _punctuationLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                    if (success) {
                        _settings.update { it.copy(punctuationModelPath = currentPunctuationPath) }
                    }
                }
            } else {
                Log.w("MainViewModel", "Skipping Punctuation model due to repeated crashes")
                _punctuationLoadStatus.value = ModelLoadStatus.FAILED
            }

            // Persist potential path updates
            withContext(Dispatchers.Main) {
                settingsRepository.saveSettings(_settings.value)
            }
        }
    }

    // Session Management Functions
    fun createNewSession() {
        viewModelScope.launch {
            // Save current session first
            saveCurrentSession()

            // Create new session
            val newSession = Session()
            _currentSession.value = newSession
            loadSessionList()
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            // Save current session first
            saveCurrentSession()

            // Load selected session
            val session = sessionRepository.loadSession(sessionId)
            if (session != null) {
                _currentSession.value = session
            }
            _showSessionList.value = false
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            loadSessionList()

            // If deleted current session, create new one
            if (_currentSession.value.id == sessionId) {
                val sessions = sessionRepository.getAllSessions()
                if (sessions.isNotEmpty()) {
                    _currentSession.value = sessions.first()
                } else {
                    createNewSession()
                }
            }
        }
    }

    fun updateSessionTitle(title: String) {
        _currentSession.update { it.copy(title = title, updatedAt = System.currentTimeMillis()) }
        saveCurrentSession()
    }

    fun saveCurrentSession() {
        viewModelScope.launch {
            if (_currentSession.value.messages.isNotEmpty()) {
                sessionRepository.saveSession(_currentSession.value.copy(updatedAt = System.currentTimeMillis()))
                loadSessionList()
            }
        }
    }

    private suspend fun loadSessionList() {
        _sessionList.value = sessionRepository.getAllSessions()
    }

    fun toggleSessionList() {
        viewModelScope.launch {
            loadSessionList()
            _showSessionList.value = !_showSessionList.value
        }
    }

    fun hideSessionList() {
        _showSessionList.value = false
    }

    // Speaker Alias Functions
    fun showSpeakerAliasDialog() {
        _showSpeakerAliasDialog.value = true
    }

    fun hideSpeakerAliasDialog() {
        _showSpeakerAliasDialog.value = false
    }

    fun updateSpeakerAlias(speakerId: String, alias: String) {
        _currentSession.update { session ->
            val newAliases = session.speakerAliases.toMutableMap()
            if (alias.isBlank()) {
                newAliases.remove(speakerId)
            } else {
                newAliases[speakerId] = alias
            }
            session.copy(speakerAliases = newAliases, updatedAt = System.currentTimeMillis())
        }
        saveCurrentSession()
    }

    fun getSpeakerDisplayName(speakerId: String?): String? {
        return _currentSession.value.getSpeakerDisplayName(speakerId)
    }

    fun getAllSpeakersInSession(): List<String> {
        return _currentSession.value.messages
            .mapNotNull { it.speakerName }
            .distinct()
    }

    // Message Functions
    fun addMessage(content: String, sender: Sender, speakerName: String? = null) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            sender = sender,
            content = content,
            speakerName = speakerName
        )
        _currentSession.update { session ->
            session.copy(
                messages = session.messages + message,
                updatedAt = System.currentTimeMillis()
            )
        }

        // Auto-save periodically
        if (_currentSession.value.messages.size % 5 == 0) {
            saveCurrentSession()
        }

        // If message is from Judge, speak it
        if (sender == Sender.JUDGE) {
            viewModelScope.launch(Dispatchers.IO) {
                ttsManager.speak(content)
            }
        }
    }

    private fun updateMessage(messageId: String, update: (Message) -> Message) {
        _currentSession.update { session ->
            session.copy(
                messages = session.messages.map {
                    if (it.id == messageId) update(it) else it
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun reSpeakMessage(message: Message) {
        if (message.sender == Sender.JUDGE) {
            viewModelScope.launch(Dispatchers.IO) {
                ttsManager.speak(message.content)
            }
        }
    }

    fun clearOrganizationResult() {
        _organizationResult.value = null
    }

    fun requestOrganization() {
        if (_isOrganizing.value) return

        viewModelScope.launch {
            _isOrganizing.value = true
            val currentSettings = _settings.value

            val scribePrompt = """
                You are an expert Meeting Scribe.
                Your task is to take the following raw speech transcript (which may have STT errors, fragmentation, or wrong speaker labels) and rewrite it into a clean, professional Meeting Record.

                Guidelines:
                1. MERGE fragmented sentences that belong together.
                2. FIX obvious STT errors and filler words.
                3. ATTRIBUTE speakers correctly based on context if obvious.
                4. FORMAT as Markdown with clear sections (e.g., "## Summary", "## Detailed Log").
                5. Do NOT invent information. Only reorganize provided text.

                Input Format: [ID] Speaker: Content
            """.trimIndent()

            val chatMessages = mutableListOf<win.liuping.aijudge.data.network.model.ChatMessage>()
            chatMessages.add(win.liuping.aijudge.data.network.model.ChatMessage("system", scribePrompt))

            _currentSession.value.messages.forEach { msg ->
                if (msg.sender != Sender.SYSTEM && msg.sender != Sender.JUDGE) {
                    val speakerDisplay = getSpeakerDisplayName(msg.speakerName) ?: "User"
                    chatMessages.add(win.liuping.aijudge.data.network.model.ChatMessage("user", "$speakerDisplay: ${msg.content}"))
                }
            }

            val response = llmRepository.getJudgeResponse(chatMessages, currentSettings)
            _organizationResult.value = response
            _isOrganizing.value = false
        }
    }

    fun requestJudge() {
        if (_isJudging.value) return

        viewModelScope.launch {
            _isJudging.value = true
            val currentSettings = _settings.value

            // Build conversation history
            val chatMessages = mutableListOf<win.liuping.aijudge.data.network.model.ChatMessage>()
            chatMessages.add(win.liuping.aijudge.data.network.model.ChatMessage("system", currentSettings.systemPrompt))

            // Take last 10 messages for context
            val recentMessages = _currentSession.value.messages.takeLast(10)
            recentMessages.forEach { msg ->
                val role = when (msg.sender) {
                    Sender.USER -> "user"
                    Sender.JUDGE -> "assistant"
                    Sender.SYSTEM -> "system"
                }
                if (msg.sender != Sender.SYSTEM) {
                    val speakerDisplay = getSpeakerDisplayName(msg.speakerName)
                    val prefix = if (speakerDisplay != null) "$speakerDisplay: " else ""
                    chatMessages.add(win.liuping.aijudge.data.network.model.ChatMessage(role, "$prefix${msg.content}"))
                }
            }

            val response = llmRepository.getJudgeResponse(chatMessages, currentSettings)
            addMessage(response, Sender.JUDGE)
            _isJudging.value = false
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        val oldSettings = _settings.value
        _settings.value = newSettings
        settingsRepository.saveSettings(newSettings)

        if (oldSettings.sttEndpointTimeout != newSettings.sttEndpointTimeout && newSettings.sttModelPath.isNotBlank()) {
             // Re-init STT with new timeout
             viewModelScope.launch {
                 sttManager.release()
                 sttManager.initRecognizer(newSettings.sttModelPath, newSettings.sttEndpointTimeout)
             }
        }
    }

    fun downloadSttModel() {
        viewModelScope.launch {
            settingsRepository.resetCrashCount("stt")
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
                    _sttLoadStatus.value = ModelLoadStatus.LOADING
                    settingsRepository.markModelLoading("stt")
                    val success = sttManager.initRecognizer(path, _settings.value.sttEndpointTimeout)
                    settingsRepository.markModelLoaded()
                    _sttLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                }
            }
        }
    }

    fun downloadTtsModel() {
        viewModelScope.launch {
            settingsRepository.resetCrashCount("tts")
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
                    _ttsLoadStatus.value = ModelLoadStatus.LOADING
                    settingsRepository.markModelLoading("tts")
                    val success = ttsManager.initTts(path)
                    settingsRepository.markModelLoaded()
                    _ttsLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                }
            }
        }
    }

    fun downloadDiarizationModel() {
        viewModelScope.launch {
            settingsRepository.resetCrashCount("diarization")
            ModelDownloadManager.downloadModel(
                getApplication(),
                ModelDownloadManager.DIARIZATION_FILES,
                "diarization-model"
            ).collect { status ->
                _diarizationDownloadStatus.value = status
                if (status is ModelDownloadManager.DownloadStatus.Completed) {
                    val path = File(getApplication<Application>().filesDir, "diarization-model").absolutePath
                    val modelFile = File(path, "speaker_model.onnx").absolutePath
                    _settings.update { it.copy(speakerDiarizationModelPath = modelFile) }
                    _diarizationLoadStatus.value = ModelLoadStatus.LOADING
                    settingsRepository.markModelLoading("diarization")
                    val success = sttManager.initSpeakerRecognizer(modelFile)
                    settingsRepository.markModelLoaded()
                    _diarizationLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                }
            }
        }
    }

    fun downloadPunctuationModel() {
        viewModelScope.launch {
            settingsRepository.resetCrashCount("punctuation")
            // Clean up old files to ensure fresh download
            val punctuationDir = File(getApplication<Application>().filesDir, "punctuation-model")
            if (punctuationDir.exists()) {
                punctuationDir.listFiles()?.forEach { it.delete() }
            }

            ModelDownloadManager.downloadModel(
                getApplication(),
                ModelDownloadManager.PUNCTUATION_FILES,
                "punctuation-model"
            ).collect { status ->
                _punctuationDownloadStatus.value = status
                if (status is ModelDownloadManager.DownloadStatus.Completed) {
                    val path = File(getApplication<Application>().filesDir, "punctuation-model").absolutePath
                    val modelFile = File(path, "model.onnx")

                    if (modelFile.exists() && modelFile.length() > 0) {
                        val absPath = modelFile.absolutePath
                        _settings.update { it.copy(punctuationModelPath = absPath) }
                        _punctuationLoadStatus.value = ModelLoadStatus.LOADING
                        settingsRepository.markModelLoading("punctuation")
                        val success = sttManager.initPunctuation(absPath)
                        settingsRepository.markModelLoaded()
                        _punctuationLoadStatus.value = if (success) ModelLoadStatus.LOADED else ModelLoadStatus.FAILED
                    } else {
                        Log.e("MainViewModel", "Punctuation model download failed verification")
                        _punctuationLoadStatus.value = ModelLoadStatus.FAILED
                    }
                }
            }
        }
    }

    private var currentMessageId: String? = null
    // Track messages pending speaker identification: text -> messageId
    private val pendingSpeakerIds = mutableMapOf<String, String>()

    fun toggleListening() {
        if (_settings.value.sttModelPath.isBlank() || !sttManager.isReady()) {
            addMessage(getApplication<Application>().getString(R.string.msg_stt_not_ready), Sender.SYSTEM)
            return
        }

        val newState = !_isListening.value
        _isListening.value = newState

        if (newState) {
            sttManager.startListening(
                onResult = { text, isEndpoint, _ ->
                    viewModelScope.launch {
                        if (text.isNotBlank()) {
                            if (currentMessageId == null) {
                                val newMessage = Message(
                                    id = UUID.randomUUID().toString(),
                                    sender = Sender.USER,
                                    content = text,
                                    speakerName = null
                                )
                                currentMessageId = newMessage.id
                                _currentSession.update { session ->
                                    session.copy(
                                        messages = session.messages + newMessage,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                            } else {
                                updateMessage(currentMessageId!!) { it.copy(content = text) }
                            }

                            if (isEndpoint) {
                                Log.d("MainViewModel", "Endpoint detected, processing: $text")
                                // Save message ID for async speaker identification
                                currentMessageId?.let { msgId ->
                                    pendingSpeakerIds[text] = msgId
                                }
                                currentMessageId = null
                                // Auto-save on endpoint
                                saveCurrentSession()
                            }
                        }
                    }
                },
                onSpeakerIdentified = { messageText, speakerName ->
                    viewModelScope.launch {
                        Log.d("MainViewModel", "Speaker identified: $speakerName for text: ${messageText.take(30)}...")
                        // Find and update the message with the speaker name
                        val messageId = pendingSpeakerIds.remove(messageText)
                        if (messageId != null) {
                            updateMessage(messageId) { it.copy(speakerName = speakerName) }
                        } else {
                            // Fallback: find by content if ID not found
                            _currentSession.update { session ->
                                var found = false
                                session.copy(
                                    messages = session.messages.map {
                                        if (!found && it.content == messageText && it.speakerName == null) {
                                            found = true
                                            it.copy(speakerName = speakerName)
                                        } else it
                                    }
                                )
                            }
                        }
                        saveCurrentSession()
                    }
                }
            )
            addMessage(getApplication<Application>().getString(R.string.msg_listening_started), Sender.SYSTEM)
        } else {
            sttManager.stopListening()
            currentMessageId = null
            pendingSpeakerIds.clear()
            addMessage(getApplication<Application>().getString(R.string.msg_listening_stopped), Sender.SYSTEM)
            saveCurrentSession()
        }
    }

    fun clearMessages() {
        _currentSession.update { it.copy(messages = emptyList(), updatedAt = System.currentTimeMillis()) }
        saveCurrentSession()
    }

    override fun onCleared() {
        super.onCleared()
        // Save session before clearing
        viewModelScope.launch {
            saveCurrentSession()
        }
        sttManager.release()
        ttsManager.release()
    }
}
