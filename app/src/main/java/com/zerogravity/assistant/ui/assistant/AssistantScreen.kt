package com.zerogravity.assistant.ui.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerogravity.assistant.ui.AssistantUiState
import com.zerogravity.assistant.ui.AssistantActivity
import com.zerogravity.assistant.ui.navigation.horizontalPageSwipe
import com.zerogravity.assistant.ui.theme.ZeroGravityAccent
import com.zerogravity.assistant.domain.model.ChatMessage
import com.zerogravity.assistant.domain.model.ChatRole

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onNavigate: (String) -> Unit,
    onNotice: (String) -> Unit,
    onCommand: (String) -> Unit,
    onNewChat: () -> Unit,
    onStartListening: (Boolean) -> Unit,
    onStopListening: () -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var continuousMode by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.speechInput) {
        val recognized = state.speechInput as? com.zerogravity.assistant.domain.model.SpeechInputState.Recognized
        if (recognized != null) prompt = recognized.text
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandWordmark()
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    state.settingsError -> "OFFLINE"
                    state.apiConfiguration.isConfigured -> "ONLINE · API"
                    else -> "LOCAL · PRIVATE"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Your space,",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "your intelligence.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "An assistant designed around you.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val diameter = minOf(maxWidth * 0.74f, 272.dp)
            Orb(
                modifier = Modifier
                    .size(diameter)
                    .horizontalPageSwipe(
                        onSwipeLeft = { onNavigate("settings") },
                        onSwipeRight = { onNavigate("history") },
                    ),
            )
        }
        ResponsePanel(
            loading = state.settings == null && !state.settingsError,
            error = state.settingsError,
            activity = state.activity,
            chatBusy = state.chatBusy,
            speechInput = state.speechInput,
            responseText = state.responseText,
            voiceOutputError = state.voiceOutputError,
            modifier = Modifier.fillMaxWidth(),
        )
        ChatTranscript(
            messages = state.chatMessages,
            busy = state.chatBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.historySaveError) {
            Text(
                text = "This conversation could not be saved locally and may be lost when the app closes.",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = if (state.apiConfiguration.isConfigured) {
                "AI provider configured. Messages and recent conversation turns are sent to its HTTPS endpoint when you ask a free-form question."
            } else {
                "Offline commands work without a network. AI chat is off until you configure an HTTPS provider in Settings."
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (prompt.isEmpty()) {
                        Text(
                            "Try “help” or a local command…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    BasicTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Message for Zero" },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (!state.chatBusy) {
                                    submitPrompt(prompt, onNotice, onCommand)
                                    if (prompt.isNotBlank()) prompt = ""
                                }
                                keyboardController?.hide()
                            },
                        ),
                    )
                }
                Button(
                    onClick = {
                        if (!state.chatBusy) {
                            submitPrompt(prompt, onNotice, onCommand)
                            if (prompt.isNotBlank()) prompt = ""
                        }
                        keyboardController?.hide()
                    },
                    enabled = !state.chatBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Send message" },
                    contentPadding = ButtonDefaults.ContentPadding,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (state.chatBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("↑", fontSize = 23.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    if (state.voiceSessionActive) onStopListening()
                    else onStartListening(continuousMode)
                },
                modifier = Modifier
                    .size(56.dp)
                    .semantics {
                        contentDescription = if (state.voiceSessionActive) {
                            "Stop voice session"
                        } else {
                            "Start offline voice input"
                        }
                    },
                shape = CircleShape,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(
                    if (state.voiceSessionActive) "STOP" else "MIC",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        FilterChip(
            selected = continuousMode,
            onClick = { continuousMode = !continuousMode },
            label = { Text(if (continuousMode) "Continuous turns on" else "Continuous turns off") },
            enabled = !state.voiceSessionActive,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Text(
            text = "Microphone is used only after you tap. Speech is processed by an installed on-device recognizer; no online fallback is used.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp, bottom = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickLink(
                label = "New chat",
                modifier = Modifier.weight(1f),
                onClick = { prompt = ""; onNewChat() },
            )
            QuickLink(
                label = "Personalise",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate("personalisation") },
            )
            QuickLink(
                label = "Settings",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate("settings") },
            )
        }
    }
}

@Composable
private fun BrandWordmark() {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Zero") }
            withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Gravity") }
        },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = (-0.5).sp,
    )
}

@Composable
private fun Orb(modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0.94f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }
    val accent = ZeroGravityAccent
    Canvas(
        modifier = modifier
            .semantics { contentDescription = "ZeroGravity glowing orbital core" }
            .shadow(1.dp, CircleShape, ambientColor = accent.copy(alpha = 0.3f)),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.37f * scale.value
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF192B53),
                    Color(0xFF111D38),
                    Color(0xFF0C1222),
                    Color.Transparent,
                ),
                center = center,
                radius = size.minDimension * 0.56f,
            ),
            radius = size.minDimension * 0.49f,
            center = center,
        )
        drawCircle(
            color = Color(0xFFB7C7FF).copy(alpha = 0.56f),
            radius = radius * 1.12f,
            center = center,
            style = Stroke(width = size.minDimension * 0.003f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF5879D8).copy(alpha = 0.55f),
                    Color(0xFF26375E).copy(alpha = 0.84f),
                    Color(0xFF0B0F19),
                ),
                center = Offset(center.x - radius * 0.32f, center.y - radius * 0.38f),
                radius = radius * 1.7f,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = radius * 0.82f,
            center = center,
            style = Stroke(width = size.minDimension * 0.004f),
        )
        drawArc(
            color = Color(0xFFDCE5FF).copy(alpha = 0.84f),
            startAngle = 206f,
            sweepAngle = 104f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.21f, center.y - radius * 1.21f),
            size = Size(radius * 2.42f, radius * 2.42f),
            style = Stroke(width = size.minDimension * 0.007f, cap = StrokeCap.Round),
        )
        drawArc(
            color = accent.copy(alpha = 0.92f),
            startAngle = 22f,
            sweepAngle = 76f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.3f, center.y - radius * 1.3f),
            size = Size(radius * 2.6f, radius * 2.6f),
            style = Stroke(width = size.minDimension * 0.009f, cap = StrokeCap.Round),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = size.minDimension * 0.012f,
            center = Offset(center.x - radius * 0.44f, center.y - radius * 0.48f),
        )
        drawCircle(
            color = accent.copy(alpha = 0.9f),
            radius = size.minDimension * 0.018f,
            center = Offset(center.x + radius * 1.2f, center.y - radius * 0.15f),
        )
    }
}

@Composable
private fun ResponsePanel(
    loading: Boolean,
    error: Boolean,
    activity: AssistantActivity,
    chatBusy: Boolean,
    speechInput: com.zerogravity.assistant.domain.model.SpeechInputState,
    responseText: String,
    voiceOutputError: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (error) MaterialTheme.colorScheme.error else ZeroGravityAccent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        error -> "SETUP NEEDS ATTENTION"
                        chatBusy -> "ZERO · CONNECTING"
                        activity == AssistantActivity.LISTENING -> "ZERO · LISTENING"
                        activity == AssistantActivity.PROCESSING -> "ZERO · PROCESSING"
                        activity == AssistantActivity.SPEAKING -> "ZERO · SPEAKING"
                        speechInput is com.zerogravity.assistant.domain.model.SpeechInputState.PermissionDenied ->
                            "MICROPHONE PERMISSION"
                        speechInput is com.zerogravity.assistant.domain.model.SpeechInputState.NoSpeech ->
                            "NO SPEECH DETECTED"
                        else -> "ZERO · RESPONSE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.1.sp,
                )
            }
            Text(
                text = when {
                    loading -> "Preparing your local preferences…"
                    error -> "Your local preferences could not be loaded."
                    chatBusy -> "Contacting your configured AI provider…"
                    activity == AssistantActivity.LISTENING -> "Listening for your voice…"
                    activity == AssistantActivity.PROCESSING -> "Processing on this device…"
                    activity == AssistantActivity.SPEAKING -> "Speaking a response…"
                    speechInput is com.zerogravity.assistant.domain.model.SpeechInputState.PermissionDenied ->
                        "Microphone permission was denied."
                    speechInput is com.zerogravity.assistant.domain.model.SpeechInputState.NoSpeech ->
                        "I didn't hear speech. Try again."
                    speechInput is com.zerogravity.assistant.domain.model.SpeechInputState.Error ->
                        if (speechInput.reason == com.zerogravity.assistant.domain.model.FailureReason.NOT_AVAILABLE) {
                            "Offline speech recognition is unavailable on this device."
                        } else {
                            "Offline speech recognition could not complete."
                        }
                    voiceOutputError -> "The response is shown here, but offline speech output is unavailable."
                    else -> "Ready for offline commands."
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    loading -> "Just a moment."
                    error -> "Restart the app after checking available device storage."
                    chatBusy -> "Your message is being sent securely over HTTPS."
                    activity == AssistantActivity.LISTENING -> "The microphone is active for this request."
                    activity == AssistantActivity.PROCESSING -> "No audio or transcript leaves this device."
                    activity == AssistantActivity.SPEAKING -> "Tap Stop to interrupt speech."
                    else -> "Try help, time/date, battery, torch, supported apps, or a reminder."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (speechInput as? com.zerogravity.assistant.domain.model.SpeechInputState.Recognized)
                ?.let { recognized ->
                    Text(
                        text = "Heard: ${recognized.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            if (responseText.isNotBlank() &&
                !(speechInput is com.zerogravity.assistant.domain.model.SpeechInputState.Recognized &&
                    responseText == "Heard: ${speechInput.text}")
            ) {
                Text(
                    text = responseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ChatTranscript(
    messages: List<ChatMessage>,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty() && !busy) return
    Column(
        modifier = modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        messages.takeLast(10).forEach { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.role == ChatRole.USER) {
                    Arrangement.End
                } else {
                    Arrangement.Start
                },
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    shape = RoundedCornerShape(16.dp),
                    color = if (message.role == ChatRole.USER) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text(
                            if (message.role == ChatRole.USER) "YOU" else "ZERO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            message.content,
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    "Waiting for the provider…",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickLink(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun submitPrompt(prompt: String, onNotice: (String) -> Unit, onCommand: (String) -> Unit) {
    if (prompt.isBlank()) {
        onNotice("Enter a message before sending.")
    } else {
        onCommand(prompt.trim())
    }
}
