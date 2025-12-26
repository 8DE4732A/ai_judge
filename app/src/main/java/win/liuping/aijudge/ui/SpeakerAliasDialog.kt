package win.liuping.aijudge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import win.liuping.aijudge.R

@Composable
fun SpeakerAliasDialog(
    speakers: List<String>,
    currentAliases: Map<String, String>,
    onUpdateAlias: (speakerId: String, alias: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Local state for editing
    val editingAliases = remember(currentAliases) {
        mutableStateMapOf<String, String>().apply {
            putAll(currentAliases)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speaker_aliases_title)) },
        text = {
            if (speakers.isEmpty()) {
                Text(
                    text = stringResource(R.string.speaker_no_speakers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(speakers) { speakerId ->
                        SpeakerAliasItem(
                            speakerId = speakerId,
                            alias = editingAliases[speakerId] ?: "",
                            onAliasChange = { newAlias ->
                                editingAliases[speakerId] = newAlias
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Save all aliases
                    speakers.forEach { speakerId ->
                        val alias = editingAliases[speakerId] ?: ""
                        onUpdateAlias(speakerId, alias)
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.btn_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun SpeakerAliasItem(
    speakerId: String,
    alias: String,
    onAliasChange: (String) -> Unit
) {
    Column {
        Text(
            text = speakerId,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = alias,
            onValueChange = onAliasChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.speaker_alias_hint, speakerId))
            },
            singleLine = true
        )
    }
}
