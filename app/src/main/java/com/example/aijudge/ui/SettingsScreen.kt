package com.example.aijudge.ui

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
import com.example.aijudge.data.model.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
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
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("API Endpoint") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            minLines = 3
        )
        
        Button(
            onClick = {
                onSave(
                    currentSettings.copy(
                        llmApiKey = apiKey,
                        llmEndpoint = endpoint,
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
