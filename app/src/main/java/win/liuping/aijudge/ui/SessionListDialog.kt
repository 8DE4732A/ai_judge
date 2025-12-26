package win.liuping.aijudge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import win.liuping.aijudge.R
import win.liuping.aijudge.data.model.Session
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionListDialog(
    sessions: List<Session>,
    currentSessionId: String,
    onSessionSelect: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_list_title)) },
        text = {
            Column {
                // New Session Button
                OutlinedButton(
                    onClick = onNewSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.session_new))
                }

                Spacer(Modifier.height(16.dp))

                if (sessions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.session_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(sessions) { session ->
                            SessionListItem(
                                session = session,
                                isSelected = session.id == currentSessionId,
                                onSelect = { onSessionSelect(session.id) },
                                onDelete = { showDeleteConfirm = session.id }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_done))
            }
        }
    )

    // Delete confirmation dialog
    showDeleteConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.session_delete)) },
            text = { Text(stringResource(R.string.session_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(sessionId)
                        showDeleteConfirm = null
                    }
                ) {
                    Text(stringResource(R.string.session_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun SessionListItem(
    session: Session,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.getDisplayTitle().ifBlank {
                        stringResource(R.string.session_untitled)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(session.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${session.messages.size} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.session_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
