package win.liuping.aijudge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import win.liuping.aijudge.R
import win.liuping.aijudge.data.model.AppSettings

import androidx.compose.material3.LinearProgressIndicator
import win.liuping.aijudge.util.ModelDownloadManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    sttDownloadStatus: ModelDownloadManager.DownloadStatus?,
    ttsDownloadStatus: ModelDownloadManager.DownloadStatus?,
    diarizationDownloadStatus: ModelDownloadManager.DownloadStatus?,
    punctuationDownloadStatus: ModelDownloadManager.DownloadStatus?,
    onDownloadStt: () -> Unit,
    onDownloadTts: () -> Unit,
    onDownloadDiarization: () -> Unit,
    onDownloadPunctuation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKey by remember { mutableStateOf(currentSettings.llmApiKey) }
    var endpoint by remember { mutableStateOf(currentSettings.llmEndpoint) }
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    
    // Simple state containers for now
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.llm_settings_header), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        // Provider Selection
        var expanded by remember { mutableStateOf(false) }
        val providers = win.liuping.aijudge.data.model.LlmProvider.values()
        var selectedProvider by remember { mutableStateOf(currentSettings.llmProvider) }
        var currentModel by remember { mutableStateOf(currentSettings.llmModel) }

        androidx.compose.material3.ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = selectedProvider.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.label_llm_provider)) },
                trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                providers.forEach { provider ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(provider.displayName) },
                        onClick = {
                            selectedProvider = provider
                            expanded = false
                            // Auto-fill defaults if switching
                            if (provider != win.liuping.aijudge.data.model.LlmProvider.CUSTOM) {
                                endpoint = provider.defaultEndpoint
                                currentModel = provider.defaultModel
                            }
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.label_api_key)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text(if (selectedProvider == win.liuping.aijudge.data.model.LlmProvider.CUSTOM) stringResource(R.string.label_api_endpoint) else stringResource(R.string.label_api_endpoint_default)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            // Allow editing even for presets as user might use a proxy, but hint it's a default
        )
        
        OutlinedTextField(
            value = currentModel,
            onValueChange = { currentModel = it },
            label = { Text(stringResource(R.string.label_model_name)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text(stringResource(R.string.label_system_prompt)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            minLines = 3
        )

        var timeoutText by remember { mutableStateOf(currentSettings.llmTimeoutSeconds.toString()) }
        OutlinedTextField(
            value = timeoutText,
            onValueChange = { 
                timeoutText = it.filter { char -> char.isDigit() }
            },
            label = { Text(stringResource(R.string.label_timeout_seconds)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        )

        var endpointTimeout by remember { mutableStateOf(currentSettings.sttEndpointTimeout) }
        Text(
            text = stringResource(R.string.label_stt_endpoint_timeout, endpointTimeout),
            modifier = Modifier.padding(top = 8.dp)
        )
        Slider(
            value = endpointTimeout,
            onValueChange = { endpointTimeout = it },
            valueRange = 0.5f..5.0f,
            steps = 44 // (5.0 - 0.5) / 0.1 = 45 steps -> 44 intervals
        )

        Text(stringResource(R.string.speech_models_header), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))

        // STT Download
        val sttStatusText = if (currentSettings.sttModelPath.isNotEmpty()) stringResource(R.string.status_downloaded) else stringResource(R.string.status_not_downloaded)
        Text(stringResource(R.string.stt_model_status_prefix, sttStatusText))
        if (sttDownloadStatus is ModelDownloadManager.DownloadStatus.Progress) {
             LinearProgressIndicator(progress = sttDownloadStatus.progress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        } else if (sttDownloadStatus is ModelDownloadManager.DownloadStatus.Completed) {
            Text(stringResource(R.string.msg_download_complete), color = androidx.compose.ui.graphics.Color.Green)
        }
        
        Button(
            onClick = onDownloadStt,
            enabled = sttDownloadStatus !is ModelDownloadManager.DownloadStatus.Progress,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(stringResource(R.string.btn_download_stt))
        }

        // TTS Download
        val ttsStatusText = if (currentSettings.ttsModelPath.isNotEmpty()) stringResource(R.string.status_downloaded) else stringResource(R.string.status_not_downloaded)
        Text(stringResource(R.string.tts_model_status_prefix, ttsStatusText), modifier = Modifier.padding(top = 8.dp))
        if (ttsDownloadStatus is ModelDownloadManager.DownloadStatus.Progress) {
             LinearProgressIndicator(progress = ttsDownloadStatus.progress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        } else if (ttsDownloadStatus is ModelDownloadManager.DownloadStatus.Completed) {
             Text(stringResource(R.string.msg_download_complete), color = androidx.compose.ui.graphics.Color.Green)
        }

        Button(
            onClick = onDownloadTts,
            enabled = ttsDownloadStatus !is ModelDownloadManager.DownloadStatus.Progress,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(stringResource(R.string.btn_download_tts))
        }

        // Diarization Download
        val diarizationStatusText = if (currentSettings.speakerDiarizationModelPath.isNotEmpty()) stringResource(R.string.status_downloaded) else stringResource(R.string.status_not_downloaded)
        Text(stringResource(R.string.diarization_model_status_prefix, diarizationStatusText), modifier = Modifier.padding(top = 8.dp))
        if (diarizationDownloadStatus is ModelDownloadManager.DownloadStatus.Progress) {
             LinearProgressIndicator(progress = diarizationDownloadStatus.progress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        } else if (diarizationDownloadStatus is ModelDownloadManager.DownloadStatus.Completed) {
             Text(stringResource(R.string.msg_download_complete), color = androidx.compose.ui.graphics.Color.Green)
        }

        Button(
            onClick = onDownloadDiarization,
            enabled = diarizationDownloadStatus !is ModelDownloadManager.DownloadStatus.Progress,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(stringResource(R.string.btn_download_diarization))
        }

        // Punctuation Download
        val punctStatusText = if (currentSettings.punctuationModelPath.isNotEmpty()) stringResource(R.string.status_downloaded) else stringResource(R.string.status_not_downloaded)
        Text(stringResource(R.string.punct_model_status_prefix, punctStatusText), modifier = Modifier.padding(top = 8.dp))
        if (punctuationDownloadStatus is ModelDownloadManager.DownloadStatus.Progress) {
             LinearProgressIndicator(progress = punctuationDownloadStatus.progress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        } else if (punctuationDownloadStatus is ModelDownloadManager.DownloadStatus.Completed) {
             Text(stringResource(R.string.msg_download_complete), color = androidx.compose.ui.graphics.Color.Green)
        }

        Button(
            onClick = onDownloadPunctuation,
            enabled = punctuationDownloadStatus !is ModelDownloadManager.DownloadStatus.Progress,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(stringResource(R.string.btn_download_punctuation))
        }
        
        Button(
            onClick = {
                onSave(
                    currentSettings.copy(
                        llmApiKey = apiKey,
                        llmEndpoint = endpoint,
                        llmModel = currentModel,
                        llmProvider = selectedProvider,
                        systemPrompt = systemPrompt,
                        llmTimeoutSeconds = timeoutText.toLongOrNull() ?: 60,
                        sttEndpointTimeout = endpointTimeout
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.btn_save_settings))
        }
    }
}
