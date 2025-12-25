package win.liuping.aijudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import win.liuping.aijudge.data.model.Message
import win.liuping.aijudge.data.model.Sender

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ChatScreen(
    messages: List<Message>,
    onMessageClick: (Message) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB or bottom bar
    ) {
        items(messages) { message ->
            MessageBubble(message = message, onClick = { onMessageClick(message) })
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onClick: () -> Unit
) {
    val align = when (message.sender) {
        Sender.USER -> Alignment.End
        Sender.JUDGE -> Alignment.Start
        Sender.SYSTEM -> Alignment.CenterHorizontally
    }

    val backgroundColor = when (message.sender) {
        Sender.USER -> MaterialTheme.colorScheme.primaryContainer
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
            Text(
                text = message.sender.name,
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
