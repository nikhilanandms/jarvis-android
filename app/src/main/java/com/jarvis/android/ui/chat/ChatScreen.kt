package com.jarvis.android.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.android.assistant.OrchestratorState
import com.jarvis.android.data.db.entities.MessageEntity

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val state by viewModel.orchestratorState.collectAsState()
    val currentResponse by viewModel.currentResponse.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }

    // Auto-scroll to bottom when messages or streaming response changes
    LaunchedEffect(messages.size, currentResponse.length) {
        if (messages.isNotEmpty() || currentResponse.isNotEmpty()) {
            listState.animateScrollToItem((messages.size + if (currentResponse.isNotEmpty()) 1 else 0).coerceAtLeast(0))
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                StateIndicatorBar(state)
                InputBar(
                    text = textInput,
                    onTextChange = { textInput = it },
                    onSend = {
                        viewModel.sendTextMessage(textInput)
                        textInput = ""
                    },
                    onMicToggle = {
                        if (state == OrchestratorState.IDLE) viewModel.startVoiceChat()
                        else viewModel.stopVoiceChat()
                    },
                    isActive = state != OrchestratorState.IDLE
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
            // Live streaming response bubble
            if (currentResponse.isNotEmpty()) {
                item(key = "streaming") {
                    MessageBubble(
                        MessageEntity(
                            id = -1, conversationId = -1,
                            role = "assistant",
                            content = "$currentResponse▌"
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StateIndicatorBar(state: OrchestratorState) {
    val label = when (state) {
        OrchestratorState.LISTENING     -> "Listening…"
        OrchestratorState.TRANSCRIBING  -> "Transcribing…"
        OrchestratorState.THINKING      -> "Thinking…"
        OrchestratorState.SPEAKING      -> "Speaking…"
        OrchestratorState.IDLE          -> ""
    }
    if (label.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label)
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicToggle: () -> Unit,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type or tap mic…") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.width(6.dp))
        if (text.isNotBlank()) {
            IconButton(onClick = onSend) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
        MicButton(isActive = isActive, onClick = onMicToggle)
    }
}

@Composable
private fun MicButton(isActive: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition("mic")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "micPulse"
    )

    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .then(if (isActive) Modifier.scale(scale) else Modifier),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isActive) "Stop" else "Start voice",
            tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
