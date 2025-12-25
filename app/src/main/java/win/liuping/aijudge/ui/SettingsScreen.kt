package win.liuping.aijudge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onDownloadStt: () -> Unit,
    onDownloadTts: () -> Unit,
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
        Text("LLM Settings", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

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
                label = { Text("LLM Provider") },
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
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text(if (selectedProvider == win.liuping.aijudge.data.model.LlmProvider.CUSTOM) "API Endpoint" else "API Endpoint (Default)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            // Allow editing even for presets as user might use a proxy, but hint it's a default
        )
        
        OutlinedTextField(
            value = currentModel,
            onValueChange = { currentModel = it },
            label = { Text("Model Name") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            minLines = 3
        )

        Text("Speech Models", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))

        // STT Download
        Text("STT Model: ${if (currentSettings.sttModelPath.isNotEmpty()) "Downloaded" else "Not Downloaded"}")
        if (sttDownloadStatus is ModelDownloadManager.DownloadStatus.Progress) {
             LinearProgressIndicator(progress = sttDownloadStatus.progress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        } else if (sttDownloadStatus is ModelDownloadManager.DownloadStatus.Completed) {
            Text("Download Complete!", color = androidx.compose.ui.graphics.Color.Green)
        }
        
        Button(
            onClick = onDownloadStt,
            enabled = sttDownloadStatus !is ModelDownloadManager.DownloadStatus.Progress,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text("Download STT Model")
        }

        // TTS Download
        Text("TTS Model: ${if (currentSettings.ttsModelPath.isNotEmpty()) "Downloaded" else "Not Downloaded"}", modifier = Modifier.padding(top = 8.dp))
        if (ttsDownloadStatus is ModelDownloadManager.DownloadStatus.Progress) {
             LinearProgressIndicator(progress = ttsDownloadStatus.progress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        } else if (ttsDownloadStatus is ModelDownloadManager.DownloadStatus.Completed) {
             Text("Download Complete!", color = androidx.compose.ui.graphics.Color.Green)
        }

        Button(
            onClick = onDownloadTts,
            enabled = ttsDownloadStatus !is ModelDownloadManager.DownloadStatus.Progress,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text("Download TTS Model")
        }
        
        Button(
            onClick = {
                onSave(
                    currentSettings.copy(
                        llmApiKey = apiKey,
                        llmEndpoint = endpoint,
                        llmModel = currentModel,
                        llmProvider = selectedProvider,
                        systemPrompt = systemPrompt
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Save Settings")
        }
    }
}
