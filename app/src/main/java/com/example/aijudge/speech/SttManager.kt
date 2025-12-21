package com.example.aijudge.speech

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

    // Configuration - hardcoded for demo, normally from settings/assets
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun initRecognizer() {
        try {
            // NOTE: This assumes models are in assets/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20
            // You would need to actually put these files in assets or download them.
            // For the sake of this code compiling, we use placeholders.
//            val config = OnlineRecognizerConfig(
//                featConfig = FeatureConfig(sampleRate = 16000.0f, featureDim = 80),
//                modelConfig = OnlineModelConfig(
//                    transducer = OnlineTransducerModelConfig(
//                        encoder = "encoder-epoch-99-avg-1.onnx",
//                        decoder = "decoder-epoch-99-avg-1.onnx",
//                        joiner = "joiner-epoch-99-avg-1.onnx",
//                    ),
//                    tokens = "tokens.txt",
//                    numThreads = 1,
//                    debug = false,
//                ),
//                decodingMethod = "greedy_search",
//                maxActivePaths = 4,
//            )
            // recognizer = OnlineRecognizer(context.assets, config)
            Log.d("SttManager", "Recognizer initialized (Mock)")
        } catch (e: Exception) {
            Log.e("SttManager", "Failed to init recognizer", e)
        }
    }

    fun startListening(onResult: (String) -> Unit) {
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
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Pass samples to recognizer
                        val samples = FloatArray(read)
                        for (i in 0 until read) {
                            samples[i] = buffer[i] / 32768.0f
                        }
                        
                        // recognizer?.acceptWaveform(samples, sampleRateInHz)
                        // while (recognizer?.isReady() == true) {
                        //     recognizer?.decode()
                        // }
                        // val result = recognizer?.getResult()
                        // if (result != null && result.text.isNotEmpty()) {
                        //     val text = result.text
                        //     if (text.isNotBlank()) {
                        //         onResult(text)
                        //     }
                        // }
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
        recognizer?.release()
        recognizer = null
    }
}
