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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SttManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var recognizer: OnlineRecognizer? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null

    // Configuration - hardcoded for demo, normally from settings/assets
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun initRecognizer(modelDir: String) {
        try {
            Log.d("SttManager", "Initializing recognizer with modelDir: $modelDir")
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
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
            )
             recognizer = OnlineRecognizer(assetManager = null, config = config)
             stream = recognizer?.createStream()
            Log.d("SttManager", "Recognizer initialized")
        } catch (e: Exception) {
            Log.e("SttManager", "Failed to init recognizer", e)
        }
    }

    fun isReady(): Boolean {
        return stream != null
    }

    fun startListening(onResult: (String, Boolean) -> Unit) {
        if (isRecording) return
        
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
                            Log.d("SttManager", "Audio RMS: $rms")
                            if (rms < 10.0) {
                                Log.w("SttManager", "Audio input is extremely quiet (RMS: $rms). Check microphone settings.")
                            }
                        }

                        // Pass samples to recognizer
                        val samples = FloatArray(read)
                        for (i in 0 until read) {
                            samples[i] = buffer[i] / 32768.0f
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
                                    Log.d("SttManager", "Partial result: '$text' (Endpoint: $isEndpoint)")
                                    onResult(text, isEndpoint)
                                    if (isEndpoint) {
                                        Log.i("SttManager", "Endpoint detected, resetting stream. Text: $text")
                                        recognizer?.reset(s)
                                    }
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
    }
}
