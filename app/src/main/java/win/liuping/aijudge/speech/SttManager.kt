package win.liuping.aijudge.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.OfflinePunctuation
import com.k2fsa.sherpa.onnx.OfflinePunctuationConfig
import com.k2fsa.sherpa.onnx.OfflinePunctuationModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class SttManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var recognizer: OnlineRecognizer? = null
    private var speakerExtractor: SpeakerEmbeddingExtractor? = null
    private var punctuation: OfflinePunctuation? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null

    // Speaker Identification State
    private val speakerRegistry = mutableListOf<Pair<String, FloatArray>>() // Name -> Embedding
    private val currentAudioChunks = mutableListOf<FloatArray>()
    private var nextSpeakerId = 1
    private val SIMILARITY_THRESHOLD = 0.45f // Threshold for same speaker (0.0 to 1.0)

    // Coroutine scope for async speaker identification
    private val speakerIdScope = CoroutineScope(Dispatchers.Default)

    // Configuration - hardcoded for demo, normally from settings/assets
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun initRecognizer(modelDir: String, endpointTimeout: Float = 1.5f): Boolean {
        return try {
            Log.d("SttManager", "Initializing recognizer with modelDir: $modelDir, timeout: $endpointTimeout")
            val config = OnlineRecognizerConfig(
                featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = com.k2fsa.sherpa.onnx.OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$modelDir/encoder-epoch-99-avg-1.onnx",
                        decoder = "$modelDir/decoder-epoch-99-avg-1.onnx",
                        joiner = "$modelDir/joiner-epoch-99-avg-1.onnx",
                    ),
                    tokens = "$modelDir/tokens.txt",
                    numThreads = 1,
                    debug = false,
                ),
                decodingMethod = "greedy_search",
                maxActivePaths = 4,
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, endpointTimeout, 0.0f),
                    rule2 = EndpointRule(true, 1.2f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 60.0f)
                )
            )
             recognizer = OnlineRecognizer(assetManager = null, config = config)
             stream = recognizer?.createStream()
            Log.d("SttManager", "Recognizer initialized")
            true
        } catch (e: Exception) {
            Log.e("SttManager", "Failed to init recognizer", e)
            false
        }
    }

    fun initSpeakerRecognizer(modelPath: String): Boolean {
        return try {
            Log.d("SttManager", "Initializing speaker extractor with model: $modelPath")
            val config = SpeakerEmbeddingExtractorConfig(
                model = modelPath,
                numThreads = 1,
                debug = false,
                provider = "cpu"
            )
            speakerExtractor = SpeakerEmbeddingExtractor(config = config)
            Log.d("SttManager", "Speaker extractor initialized")
            true
        } catch (e: Exception) {
            Log.e("SttManager", "Failed to init speaker extractor", e)
            false
        }
    }

    fun initPunctuation(modelPath: String): Boolean {
        return try {
            val modelFile = java.io.File(modelPath)
            if (!modelFile.exists()) {
                Log.e("SttManager", "Punctuation model file not found: $modelPath")
                return false
            }

            // Check model file size (int8 model is ~75MB)
            val modelSize = modelFile.length()
            if (modelSize < 50_000_000) { // Less than 50MB is suspicious
                Log.e("SttManager", "Punctuation model file too small: $modelSize bytes, expected ~75MB")
                return false
            }

            // Note: sherpa-onnx only requires model.onnx for punctuation
            // tokens.json is NOT required according to official documentation
            Log.d("SttManager", "Initializing punctuation with: $modelPath (model: ${modelSize/1024/1024}MB)")
            val modelConfig = OfflinePunctuationModelConfig(
                ctTransformer = modelPath,
                numThreads = 1,
                debug = false,
                provider = "cpu"
            )
            val config = OfflinePunctuationConfig(model = modelConfig)
            punctuation = OfflinePunctuation(assetManager = null, config = config)
            Log.d("SttManager", "Punctuation initialized")
            true
        } catch (e: Exception) {
            Log.e("SttManager", "Failed to init punctuation", e)
            false
        } catch (e: Error) {
            // Catch UnsatisfiedLinkError and other Errors from JNI
            Log.e("SttManager", "Native error initializing punctuation", e)
            false
        }
    }

    private fun computeCosineSimilarity(u: FloatArray, v: FloatArray): Float {
        var dot = 0.0f
        var normU = 0.0f
        var normV = 0.0f
        for (i in u.indices) {
            dot += u[i] * v[i]
            normU += u[i] * u[i]
            normV += v[i] * v[i]
        }
        return dot / (sqrt(normU) * sqrt(normV))
    }

    private fun identifySpeaker(samples: FloatArray): String {
        val extractor = speakerExtractor ?: return "User" // Fallback if no model

        val stream = extractor.createStream()
        stream.acceptWaveform(samples, sampleRateInHz)
        
        // Check if stream has enough audio? Extractor usually handles it.
        if (!extractor.isReady(stream)) {
            stream.release()
            return "User"
        }
        
        val embedding = extractor.compute(stream)
        stream.release()

        // Compare with registry
        var bestScore = -1.0f
        var bestSpeaker = ""

        for ((name, knownEmbedding) in speakerRegistry) {
            val score = computeCosineSimilarity(embedding, knownEmbedding)
            if (score > bestScore) {
                bestScore = score
                bestSpeaker = name
            }
        }

        Log.d("SttManager", "Speaker Sim: $bestScore with $bestSpeaker")

        return if (bestScore > SIMILARITY_THRESHOLD) {
            bestSpeaker
        } else {
            val newName = "Speaker $nextSpeakerId"
            nextSpeakerId++
            speakerRegistry.add(newName to embedding)
            Log.i("SttManager", "New speaker registered: $newName")
            newName
        }
    }

    fun isReady(): Boolean {
        return stream != null
    }

    /**
     * Start listening for speech.
     * @param onResult Called for each recognition result: (text, isEndpoint, speakerName)
     *                 speakerName is null during streaming; for endpoint, it will be updated async
     * @param onSpeakerIdentified Called asynchronously when speaker is identified: (messageText, speakerName)
     */
    fun startListening(
        onResult: (String, Boolean, String?) -> Unit,
        onSpeakerIdentified: ((String, String) -> Unit)? = null
    ) {
        if (isRecording) return

        // Reset state
        synchronized(currentAudioChunks) {
            currentAudioChunks.clear()
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInHz,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )
            audioRecord?.startRecording()
            isRecording = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ShortArray(minBufferSize)
                var loopCount = 0

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Calculate RMS for debugging
                        if (loopCount++ % 20 == 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = Math.sqrt(sum / read)
                            // Log.d("SttManager", "Audio RMS: $rms")
                        }

                        // Pass samples to recognizer
                        val samples = FloatArray(read)
                        for (i in 0 until read) {
                            samples[i] = buffer[i] / 32768.0f
                        }

                        // Buffer for speaker ID
                        // Only buffer if we have an extractor, to save memory otherwise
                        if (speakerExtractor != null) {
                            synchronized(currentAudioChunks) {
                                currentAudioChunks.add(samples)
                            }
                        }

                        val s = stream
                        if (s != null) {
                            s.acceptWaveform(samples, sampleRateInHz)
                            while (recognizer?.isReady(s) == true) {
                                recognizer?.decode(s)
                            }

                            val isEndpoint = recognizer?.isEndpoint(s) ?: false
                            val result = recognizer?.getResult(s)

                            if (result != null) {
                                val text = result.text
                                if (text.isNotBlank()) {
                                    var finalText = text

                                    if (isEndpoint) {
                                        Log.i("SttManager", "Endpoint detected: '$text'")

                                        // Apply punctuation synchronously (fast)
                                        if (punctuation != null) {
                                            try {
                                                finalText = punctuation!!.addPunctuation(text)
                                            } catch (e: Exception) {
                                                Log.e("SttManager", "Punctuation failed", e)
                                            }
                                        }

                                        // Copy audio for async speaker identification
                                        if (speakerExtractor != null && onSpeakerIdentified != null) {
                                            val audioForSpeakerId: FloatArray
                                            synchronized(currentAudioChunks) {
                                                var totalSize = 0
                                                currentAudioChunks.forEach { totalSize += it.size }
                                                audioForSpeakerId = FloatArray(totalSize)
                                                var offset = 0
                                                currentAudioChunks.forEach {
                                                    System.arraycopy(it, 0, audioForSpeakerId, offset, it.size)
                                                    offset += it.size
                                                }
                                                currentAudioChunks.clear()
                                            }

                                            // Async speaker identification
                                            val textForCallback = finalText
                                            speakerIdScope.launch {
                                                Log.d("SttManager", "Starting async speaker identification...")
                                                val speakerName = identifySpeaker(audioForSpeakerId)
                                                Log.d("SttManager", "Async speaker identified: $speakerName")
                                                onSpeakerIdentified(textForCallback, speakerName)
                                            }
                                        } else {
                                            // Clear audio buffer if not doing speaker ID
                                            synchronized(currentAudioChunks) {
                                                currentAudioChunks.clear()
                                            }
                                        }

                                        recognizer?.reset(s)
                                    }

                                    // Send result immediately (speakerName will be updated async)
                                    onResult(finalText, isEndpoint, null)
                                }
                            }
                        } else {
                            Log.e("SttManager", "Stream is null during recording")
                        }
                    } else {
                        Log.w("SttManager", "AudioRecord read returned $read")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("SttManager", "Permission denied", e)
        }
    }

    fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null
    }

    fun release() {
        stopListening()
        stream?.release()
        stream = null
        recognizer?.release()
        recognizer = null
        
        speakerExtractor?.release()
        speakerExtractor = null
        
        punctuation?.release()
        punctuation = null
    }
}
