package win.liuping.aijudge.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"

    // STT Model URLs (Sherpa-onnx streaming zipformer bilingual)
    // STT Model URLs (Sherpa-onnx streaming zipformer bilingual)
    // Using Hugging Face direct download links
    val STT_FILES = mapOf(
        "encoder-epoch-99-avg-1.onnx" to "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/main/encoder-epoch-99-avg-1.onnx",
        "decoder-epoch-99-avg-1.onnx" to "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/main/decoder-epoch-99-avg-1.onnx",
        "joiner-epoch-99-avg-1.onnx" to "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/main/joiner-epoch-99-avg-1.onnx",
        "tokens.txt" to "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/main/tokens.txt"
    )

    // TTS Model Files (vits-zh-aishell3)
    val TTS_FILES = mapOf(
        "vits-aishell3.onnx" to "https://huggingface.co/csukuangfj/vits-zh-aishell3/resolve/main/vits-aishell3.onnx",
        "tokens.txt" to "https://huggingface.co/csukuangfj/vits-zh-aishell3/resolve/main/tokens.txt",
        "lexicon.txt" to "https://huggingface.co/csukuangfj/vits-zh-aishell3/resolve/main/lexicon.txt"
    )

    // Speaker Diarization / Embedding Model
    val DIARIZATION_FILES = mapOf(
        "speaker_model.onnx" to "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    )

    // Punctuation Model (CT-Transformer)
    val PUNCTUATION_FILES = mapOf(
        "model.onnx" to "https://huggingface.co/csukuangfj/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12/resolve/main/model.onnx",
        "tokens.json" to "https://huggingface.co/csukuangfj/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12/resolve/main/tokens.json"
    )

    sealed class DownloadStatus {
        data class Progress(val progress: Float) : DownloadStatus()
        object Completed : DownloadStatus()
        data class Error(val message: String) : DownloadStatus()
    }

    fun downloadModel(context: Context, files: Map<String, String>, outputDirName: String): Flow<DownloadStatus> = flow {
        val outputDir = File(context.filesDir, outputDirName)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        var totalFiles = files.size
        var completedFiles = 0

        try {
            for ((fileName, urlString) in files) {
                val outputFile = File(outputDir, fileName)
                // Skip if already exists? For now, force download or check size? 
                // Let's overwrite to ensure correctness.
                
                emit(DownloadStatus.Progress(completedFiles.toFloat() / totalFiles))
                
                var url = URL(urlString)
                var connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connect()

                var responseCode = connection.responseCode
                var redirects = 0
                while ((responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                        responseCode == 307) && redirects < 5) {
                    val location = connection.getHeaderField("Location")
                    url = URL(location)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    responseCode = connection.responseCode
                    redirects++
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Failed to connect to $urlString: $responseCode")
                }

                val input = connection.inputStream
                val output = FileOutputStream(outputFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                
                // We could track individual file progress, but keeping it simple with file count for now
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }

                output.close()
                input.close()
                completedFiles++
                emit(DownloadStatus.Progress(completedFiles.toFloat() / totalFiles))
            }
            emit(DownloadStatus.Completed)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            emit(DownloadStatus.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}
