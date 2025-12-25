package win.liuping.aijudge.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class TtsManager(private val context: Context) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null

    fun initTts(modelDir: String) {
        try {
            Log.d("TtsManager", "Initializing TTS with modelDir: $modelDir")
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "$modelDir/vits-aishell3.onnx",
                        tokens = "$modelDir/tokens.txt",
                        lexicon = "$modelDir/lexicon.txt",
                    ),
                    numThreads = 1,
                    debug = false
                )
            )
            tts = OfflineTts(assetManager = null, config = config)
            Log.d("TtsManager", "TTS initialized")
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to init TTS", e)
        }
    }

    fun speak(text: String) {
        if (tts == null) {
            Log.w("TtsManager", "TTS not initialized")
            return
        }
        // Implementation for generating and playing audio
        val audio = tts?.generate(text, sid=0, speed=1.0f)
        if (audio != null) {
           playAudio(audio.samples, audio.sampleRate)
        }
        Log.d("TtsManager", "Speaking: $text")
    }
    private fun playAudio(samples: FloatArray, sampleRate: Int) {
         val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()
        
        // Normalize volume
        var maxAmplitude = 0.0f
        for (sample in samples) {
            val abs = Math.abs(sample)
            if (abs > maxAmplitude) {
                maxAmplitude = abs
            }
        }
        
        val targetPeak = 0.9f
        val scale = if (maxAmplitude > 0.01f) targetPeak / maxAmplitude else 1.0f
        
        Log.d("TtsManager", "Original Max Amplitude: $maxAmplitude, Applying Scale: $scale")

        val buffer = ShortArray(samples.size)
        for(i in samples.indices) {
            var scaled = samples[i] * scale
            // Soft clipping
            if (scaled > 1.0f) scaled = 1.0f
            if (scaled < -1.0f) scaled = -1.0f
            
            buffer[i] = (scaled * 32767).toInt().toShort()
        }
        audioTrack?.write(buffer, 0, buffer.size)
        audioTrack?.stop()
        audioTrack?.release()
    }

    fun release() {
        tts?.release()
        tts = null
    }
}
