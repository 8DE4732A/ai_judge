package win.liuping.aijudge.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"

    // STT Model URLs (Sherpa-onnx streaming zipformer bilingual)
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

    // Punctuation Model (CT-Transformer int8 quantized)
    // Downloads tar.bz2 archive and extracts model.int8.onnx
    const val PUNCTUATION_ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8.tar.bz2"
    const val PUNCTUATION_MODEL_NAME = "model.int8.onnx"

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

    /**
     * Download and extract punctuation model from tar.bz2 archive
     */
    fun downloadPunctuationModel(context: Context, outputDirName: String): Flow<DownloadStatus> = flow {
        val outputDir = File(context.filesDir, outputDirName)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val tempFile = File(context.cacheDir, "punctuation-model.tar.bz2")

        try {
            // Step 1: Download archive (0% - 80%)
            emit(DownloadStatus.Progress(0f))
            Log.d(TAG, "Downloading punctuation model from: $PUNCTUATION_ARCHIVE_URL")

            var url = URL(PUNCTUATION_ARCHIVE_URL)
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
                throw Exception("Failed to connect: $responseCode")
            }

            val contentLength = connection.contentLength.toLong()
            val input = connection.inputStream
            val output = FileOutputStream(tempFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    val downloadProgress = (totalBytesRead.toFloat() / contentLength) * 0.8f
                    emit(DownloadStatus.Progress(downloadProgress))
                }
            }

            output.close()
            input.close()
            Log.d(TAG, "Download complete: ${tempFile.length()} bytes")

            // Step 2: Extract tar.bz2 (80% - 100%)
            emit(DownloadStatus.Progress(0.8f))
            Log.d(TAG, "Extracting archive...")

            extractTarBz2(tempFile, outputDir, PUNCTUATION_MODEL_NAME)

            // Verify extraction
            val modelFile = File(outputDir, PUNCTUATION_MODEL_NAME)
            if (!modelFile.exists()) {
                throw Exception("Model file not found after extraction: ${modelFile.absolutePath}")
            }
            Log.d(TAG, "Extraction complete: ${modelFile.absolutePath} (${modelFile.length()} bytes)")

            // Cleanup
            tempFile.delete()

            emit(DownloadStatus.Progress(1f))
            emit(DownloadStatus.Completed)
        } catch (e: Exception) {
            Log.e(TAG, "Punctuation model download/extract failed", e)
            tempFile.delete()
            emit(DownloadStatus.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract specific file from tar.bz2 archive
     */
    private fun extractTarBz2(archiveFile: File, outputDir: File, targetFileName: String) {
        val fileInput = FileInputStream(archiveFile)
        val bufferedInput = BufferedInputStream(fileInput)
        val bzipInput = BZip2CompressorInputStream(bufferedInput)
        val tarInput = TarArchiveInputStream(bzipInput)

        var entry = tarInput.nextTarEntry
        var found = false

        while (entry != null) {
            val name = entry.name
            Log.d(TAG, "Archive entry: $name")

            // Look for the target model file
            if (name.endsWith(targetFileName) && !entry.isDirectory) {
                val outputFile = File(outputDir, targetFileName)
                val fos = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var len: Int

                while (tarInput.read(buffer).also { len = it } != -1) {
                    fos.write(buffer, 0, len)
                }

                fos.close()
                found = true
                Log.d(TAG, "Extracted: $name -> ${outputFile.absolutePath}")
                break
            }

            entry = tarInput.nextTarEntry
        }

        tarInput.close()
        bzipInput.close()
        bufferedInput.close()
        fileInput.close()

        if (!found) {
            throw Exception("Target file '$targetFileName' not found in archive")
        }
    }
}
