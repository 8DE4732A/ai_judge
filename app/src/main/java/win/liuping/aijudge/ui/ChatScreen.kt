package win.liuping.aijudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import win.liuping.aijudge.R
import win.liuping.aijudge.data.model.Message
import win.liuping.aijudge.data.model.ModelLoadStatus
import win.liuping.aijudge.data.model.Sender

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ChatScreen(
    messages: List<Message>,
    speakerAliases: Map<String, String> = emptyMap(),
    onMessageClick: (Message) -> Unit,
    sttLoadStatus: ModelLoadStatus,
    ttsLoadStatus: ModelLoadStatus,
    diarizationLoadStatus: ModelLoadStatus,
    punctuationLoadStatus: ModelLoadStatus,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Model Status Bar
        ModelStatusBar(
            sttStatus = sttLoadStatus,
            ttsStatus = ttsLoadStatus,
            diarizationStatus = diarizationLoadStatus,
            punctuationStatus = punctuationLoadStatus
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    speakerAliases = speakerAliases,
                    onClick = { onMessageClick(message) }
                )
            }
        }
    }
}

@Composable
fun ModelStatusBar(
    sttStatus: ModelLoadStatus,
    ttsStatus: ModelLoadStatus,
    diarizationStatus: ModelLoadStatus,
    punctuationStatus: ModelLoadStatus
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModelStatusIndicator("STT", sttStatus)
            ModelStatusIndicator("TTS", ttsStatus)
            ModelStatusIndicator("SPK", diarizationStatus)
            ModelStatusIndicator("PNC", punctuationStatus)
        }
    }
}

@Composable
fun ModelStatusIndicator(name: String, status: ModelLoadStatus) {
    val (color, statusText) = when (status) {
        ModelLoadStatus.NOT_DOWNLOADED -> Color.Gray to stringResource(R.string.status_not_downloaded)
        ModelLoadStatus.LOADING -> Color(0xFFFFA000) to stringResource(R.string.status_loading)
        ModelLoadStatus.LOADED -> Color(0xFF4CAF50) to stringResource(R.string.status_loaded)
        ModelLoadStatus.FAILED -> Color(0xFFF44336) to stringResource(R.string.status_failed)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (status == ModelLoadStatus.LOADING) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = color
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun MessageBubble(
    message: Message,
    speakerAliases: Map<String, String> = emptyMap(),
    onClick: () -> Unit
) {
    val align = when (message.sender) {
        Sender.USER -> Alignment.End
        Sender.JUDGE -> Alignment.Start
        Sender.SYSTEM -> Alignment.CenterHorizontally
    }

    // Get display name: alias > speakerName > sender name
    val speakerDisplayName = if (!message.speakerName.isNullOrBlank()) {
        speakerAliases[message.speakerName] ?: message.speakerName
    } else null

    val backgroundColor = when (message.sender) {
        Sender.USER -> {
            if (!message.speakerName.isNullOrBlank()) {
                // Simple deterministic color generation based on original speaker ID
                val hash = message.speakerName.hashCode()
                val colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.surfaceVariant,
                    Color(0xFFE1F5FE), // Light Blue
                    Color(0xFFF3E5F5), // Light Purple
                    Color(0xFFFFF3E0), // Light Orange
                    Color(0xFFE8F5E9)  // Light Green
                )
                colors[kotlin.math.abs(hash) % colors.size]
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        }
        Sender.JUDGE -> MaterialTheme.colorScheme.secondaryContainer
        Sender.SYSTEM -> Color.LightGray.copy(alpha = 0.5f)
    }

    val bubbleModifier = if (message.sender == Sender.JUDGE) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        if (message.sender != Sender.SYSTEM) {
            val label = if (message.sender == Sender.USER && speakerDisplayName != null) {
                speakerDisplayName
            } else {
                message.sender.name
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .then(bubbleModifier)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
