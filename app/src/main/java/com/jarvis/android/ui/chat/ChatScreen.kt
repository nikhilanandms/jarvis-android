package com.jarvis.android.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.android.assistant.OrchestratorState
import com.jarvis.android.data.db.entities.MessageEntity
import com.jarvis.android.ui.theme.Gold
import com.jarvis.android.ui.theme.GoldDim
import com.jarvis.android.ui.theme.ObsidianBg
import com.jarvis.android.ui.theme.ObsidianBorder
import com.jarvis.android.ui.theme.ObsidianVariant
import com.jarvis.android.ui.theme.TextPrimary
import com.jarvis.android.ui.theme.TextSecondary

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val state by viewModel.orchestratorState.collectAsState()
    val voiceActive by viewModel.voiceActive.collectAsState()
    val currentResponse by viewModel.currentResponse.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, currentResponse.length) {
        val itemCount = messages.size + if (currentResponse.isNotEmpty()) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Header
        TopBar()

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = 12.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageRow(msg)
            }
            if (currentResponse.isNotEmpty()) {
                item(key = "streaming") {
                    MessageRow(
                        MessageEntity(
                            id = -1, conversationId = -1,
                            role = "assistant",
                            content = currentResponse
                        ),
                        isStreaming = true
                    )
                }
            }
            // Thinking dots
            if (state == OrchestratorState.THINKING && currentResponse.isEmpty()) {
                item(key = "thinking") { ThinkingDots() }
            }
        }

        // State label
        StateLabel(state = state, voiceActive = voiceActive)

        // Input bar
        InputBar(
            text = textInput,
            onTextChange = { textInput = it },
            onSend = { viewModel.sendTextMessage(textInput); textInput = "" },
            onMicToggle = {
                if (voiceActive) viewModel.stopVoiceChat() else viewModel.startVoiceChat()
            },
            isVoiceActive = voiceActive
        )
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gold accent dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Gold, CircleShape)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "JARVIS",
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary
        )
    }
    // Hair-line divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(ObsidianBorder)
    )
}

@Composable
private fun MessageRow(message: MessageEntity, isStreaming: Boolean = false) {
    val isUser = message.role == "user"

    if (isUser) {
        // User: compact gold pill, right-aligned
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                    .background(GoldDim)
                    .border(0.5.dp, Gold.copy(alpha = 0.3f),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gold
                )
            }
        }
    } else {
        // Assistant: raw text, no bubble — with optional streaming cursor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            // Tiny gold indicator line on left
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(2.dp)
                    .height(14.dp)
                    .background(Gold.copy(alpha = 0.5f))
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = if (isStreaming) "${message.content}|" else message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        lineHeight = 26.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition("thinking")
    Row(
        modifier = Modifier.padding(start = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(0, 150, 300).forEach { delayMs ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = delayMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$delayMs"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Gold.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@Composable
private fun StateLabel(state: OrchestratorState, voiceActive: Boolean) {
    val label = when (state) {
        OrchestratorState.LISTENING    -> if (voiceActive) "LISTENING" else null
        OrchestratorState.TRANSCRIBING -> "TRANSCRIBING"
        OrchestratorState.THINKING     -> "THINKING"
        OrchestratorState.SPEAKING     -> "SPEAKING"
        OrchestratorState.IDLE         -> null
    }
    if (label == null) {
        Spacer(Modifier.height(4.dp))
        return
    }

    val transition = rememberInfiniteTransition(label)
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "statePulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(Gold.copy(alpha = alpha), CircleShape)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Gold.copy(alpha = alpha)
        )
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicToggle: () -> Unit,
    isVoiceActive: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBg)
    ) {
        // Top hairline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(ObsidianBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mic button
            VoiceButton(isActive = isVoiceActive, onClick = onMicToggle)

            // Text input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianVariant)
                    .border(0.5.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        "Message…",
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = TextSecondary
                        )
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = TextPrimary,
                        lineHeight = 22.sp,
                        fontFamily = FontFamily.Default
                    ),
                    cursorBrush = SolidColor(Gold),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Send button — only when text is present
            if (text.isNotBlank()) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Gold)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Send",
                        tint = ObsidianBg,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceButton(isActive: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition("voice")

    // Ripple rings when active
    val ring1 = remember { Animatable(0f) }
    val ring2 = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            // Ring 1 — continuous ripple
            launch {
                while (true) {
                    ring1.snapTo(0f)
                    ring1.animateTo(1f, tween(1200, easing = LinearEasing))
                }
            }
            // Ring 2 — offset by half period
            launch {
                delay(600)
                while (true) {
                    ring2.snapTo(0f)
                    ring2.animateTo(1f, tween(1200, easing = LinearEasing))
                }
            }
        } else {
            ring1.animateTo(0f, tween(200))
            ring2.animateTo(0f, tween(200))
        }
    }

    val ring1Alpha = if (isActive) (1f - ring1.value) * 0.5f else 0f
    val ring1Scale = 0.6f + ring1.value * 0.6f
    val ring2Alpha = if (isActive) (1f - ring2.value) * 0.35f else 0f
    val ring2Scale = 0.6f + ring2.value * 0.8f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .drawBehind {
                val cx = size.width / 2
                val cy = size.height / 2
                val baseR = size.minDimension / 2
                if (ring2Alpha > 0f) {
                    drawCircle(
                        color = Gold,
                        radius = baseR * ring2Scale,
                        center = Offset(cx, cy),
                        alpha = ring2Alpha,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                if (ring1Alpha > 0f) {
                    drawCircle(
                        color = Gold,
                        radius = baseR * ring1Scale,
                        center = Offset(cx, cy),
                        alpha = ring1Alpha,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isActive) Gold else ObsidianVariant)
                .border(0.5.dp, if (isActive) Gold else ObsidianBorder, CircleShape)
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isActive) "Stop listening" else "Start voice",
                tint = if (isActive) ObsidianBg else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

