package com.zerogravity.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.zerogravity.assistant.domain.model.AppearanceMode
import com.zerogravity.assistant.domain.model.AssistantApiConfiguration
import com.zerogravity.assistant.domain.model.AppPermission
import com.zerogravity.assistant.domain.model.AssistantCapability
import com.zerogravity.assistant.domain.model.AssistantSettings
import com.zerogravity.assistant.domain.model.CapabilityInfo
import com.zerogravity.assistant.domain.model.CapabilityStatus
import com.zerogravity.assistant.domain.model.VoiceDescriptor
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.ui.navigation.horizontalPageSwipe
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AssistantSettings?,
    capabilities: List<CapabilityInfo>,
    loadingError: Boolean,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    installedVoices: List<VoiceDescriptor> = emptyList(),
    onInstallVoice: (String) -> Unit = {},
    onRefreshVoices: () -> Unit = {},
    onOpenMicrophoneSettings: () -> Unit = {},
    apiConfiguration: AssistantApiConfiguration = AssistantApiConfiguration(),
    onSaveApiConfiguration: suspend (String, String, String) -> ServiceResult<Unit> = { _, _, _ ->
        ServiceResult.Failure(com.zerogravity.assistant.domain.model.FailureReason.NOT_AVAILABLE)
    },
    onClearApiConfiguration: suspend () -> ServiceResult<Unit> = {
        ServiceResult.Failure(com.zerogravity.assistant.domain.model.FailureReason.NOT_AVAILABLE)
    },
    onSettingsChange: suspend (AssistantSettings.() -> AssistantSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        PageHeading(
            "Settings",
            "A considered setup for your assistant.",
            modifier = Modifier.horizontalPageSwipe(onSwipeLeft, onSwipeRight),
        )
        if (settings == null) {
            SettingsLoadState(loadingError)
            return@Column
        }
        SettingsSection("APPEARANCE", "Make the interface feel at home.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.appearance == mode,
                        onClick = {
                            scope.launch {
                                onSettingsChange { copy(appearance = mode) }
                            }
                        },
                        label = {
                            Text(
                                when (mode) {
                                    AppearanceMode.SYSTEM -> "System"
                                    AppearanceMode.LIGHT -> "Light"
                                    AppearanceMode.DARK -> "Dark"
                                },
                            )
                        },
                    )
                }
            }
        }
        SettingsSection(
            "AI CHAT",
            "Optional OpenAI-compatible chat over HTTPS. Free-form messages and recent conversation context are sent to the configured provider.",
        ) {
            var endpoint by remember(apiConfiguration.endpoint) {
                mutableStateOf(apiConfiguration.endpoint)
            }
            var model by remember(apiConfiguration.model) {
                mutableStateOf(apiConfiguration.model.ifBlank { "gpt-4o-mini" })
            }
            var apiKey by remember { mutableStateOf("") }

            Text(
                if (apiConfiguration.isConfigured) {
                    "Provider ready · API key encrypted with Android Keystore"
                } else if (apiConfiguration.hasApiKey) {
                    "Saved API key present · complete the endpoint and model configuration"
                } else {
                    "No API configured · offline commands remain available"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (apiConfiguration.isConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HTTPS API base URL") },
                supportingText = { Text("Example: https://api.openai.com/v1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (apiConfiguration.hasApiKey) "Replace API key (optional)" else "API key") },
                supportingText = {
                    Text("Leave blank to keep the currently saved key. The key is never displayed after saving.")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        when (onSaveApiConfiguration(endpoint, model, apiKey)) {
                            is ServiceResult.Success -> apiKey = ""
                            is ServiceResult.Failure -> Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save provider")
            }
            if (apiConfiguration.hasApiKey || apiConfiguration.model.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            when (onClearApiConfiguration()) {
                                is ServiceResult.Success -> apiKey = ""
                                is ServiceResult.Failure -> Unit
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove provider and saved key")
                }
            }
            Text(
                "Only free-form questions use this provider. Device commands stay local. Provider terms and data handling apply to anything you send.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsSection(
            "VOICE",
            "On Android 12+, voice input uses only the on-device recognizer. No online fallback is used; an offline Hindi/English language pack must be installed.",
        ) {
            var speechRate by remember(settings.speechRate) {
                mutableFloatStateOf(settings.speechRate)
            }
            SettingsSwitch(
                title = "Spoken responses",
                description = "Uses an installed system voice marked as offline. Phone calls and Do Not Disturb may suppress speech.",
                checked = settings.voiceResponsesEnabled,
                onCheckedChange = {
                    scope.launch {
                        onSettingsChange { copy(voiceResponsesEnabled = it) }
                    }
                },
            )
            OutlinedButton(onClick = onOpenMicrophoneSettings) {
                Text("Review microphone permission")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en-IN" to "English", "hi-IN" to "हिन्दी").forEach { (tag, label) ->
                    FilterChip(
                        selected = settings.voiceLanguageTag == tag,
                        onClick = {
                            scope.launch {
                                onSettingsChange { copy(voiceLanguageTag = tag, voiceName = "") }
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
            val language = settings.voiceLanguageTag.substringBefore('-')
            val offlineVoices = installedVoices.filter {
                !it.requiresNetwork && it.languageTag.substringBefore('-').equals(language, true)
            }
            Text(
                if (offlineVoices.isEmpty()) {
                    "No installed offline ${if (language == "hi") "Hindi" else "English"} voice is available on this device."
                } else {
                    "Installed offline voices"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            offlineVoices.forEach { voice ->
                FilterChip(
                    selected = settings.voiceName == voice.id ||
                        (settings.voiceName.isBlank() && offlineVoices.firstOrNull()?.id == voice.id),
                    onClick = {
                        scope.launch {
                            onSettingsChange {
                                copy(
                                    voiceLanguageTag = voice.languageTag,
                                    voiceName = voice.id,
                                )
                            }
                        }
                    },
                    label = { Text(voice.displayName) },
                )
            }
            if (settings.voiceName.isNotBlank() && offlineVoices.none { it.id == settings.voiceName }) {
                Text(
                    "The previously selected offline voice is unavailable. Select an installed voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Slider(
                value = speechRate,
                onValueChange = { speechRate = it },
                onValueChangeFinished = {
                    scope.launch { onSettingsChange { copy(speechRate = speechRate) } }
                },
                valueRange = 0.5f..2f,
                steps = 14,
            )
            Text("Speech rate: ${"%.1f".format(speechRate)}×", style = MaterialTheme.typography.bodySmall)
            if (offlineVoices.isEmpty()) {
                OutlinedButton(onClick = { onInstallVoice(settings.voiceLanguageTag) }) {
                    Text("Open Android offline voice installer")
                }
                OutlinedButton(onClick = onRefreshVoices) {
                    Text("Check installed voices")
                }
            }
        }
        SettingsSection(
            "WAKE WORD",
            "Background listening is not implemented. “Hey Zero”, “OK Zero”, and “Shree” are not supported; the microphone runs only after you tap it.",
        ) {
            Text(
                "No always-listening recognizer or microphone foreground service is running. This avoids pretending to detect wake phrases and avoids hidden background recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsSection("DOWNLOADS", "No third-party voice packs are bundled in the APK.") {
            UnavailableLine(
                title = "Language models",
                detail = "No model is offered until its Android runtime, license, and verified download metadata can be confirmed.",
            )
            UnavailableLine(
                title = "System voice data",
                detail = "Android manages voice downloads. The OS installer owns transfer progress, integrity, and licensing; this app can only refresh the installed offline voice list.",
            )
        }
        SettingsSection("OPTIONAL INTEGRATIONS", "Unavailable features are not simulated or granted hidden access.") {
            UnavailableLine(
                title = "Spoken notifications",
                detail = "Not implemented. No notification-listener service or notification-access permission is requested.",
            )
            UnavailableLine(
                title = "WhatsApp messaging",
                detail = "Direct reading or sending is unavailable. Use WhatsApp itself; ZeroGravity does not access its messages.",
            )
            UnavailableLine(
                title = "Live weather",
                detail = "Unavailable offline. A legitimate online provider is not configured.",
            )
            UnavailableLine(
                title = "Calendar integration",
                detail = "Unavailable; calendar permission is not requested.",
            )
        }
        SettingsSection("DEVICE", "Local controls that do not request extra access.") {
            var threshold by remember(settings.batteryWarningThresholdPercent) {
                mutableFloatStateOf(settings.batteryWarningThresholdPercent.toFloat())
            }
            SettingsSwitch(
                title = "Battery warnings",
                description = "One warning per low-battery cycle while the app process can observe battery events and speech is permitted.",
                checked = settings.batteryWarningsEnabled,
                onCheckedChange = {
                    scope.launch {
                        onSettingsChange { copy(batteryWarningsEnabled = it) }
                    }
                },
            )
            Text("Speak once at or below ${threshold.toInt()}% battery", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                onValueChangeFinished = {
                    scope.launch {
                        onSettingsChange {
                            copy(batteryWarningThresholdPercent = threshold.toInt())
                        }
                    }
                },
                valueRange = 5f..30f,
                steps = 24,
            )
            UnavailableLine(
                title = microphoneStatus(capabilities).first,
                detail = microphoneStatus(capabilities).second,
            )
            val reminderCapability = capabilities.firstOrNull {
                it.capability == AssistantCapability.REMINDERS
            }
            UnavailableLine(
                title = if (
                    (reminderCapability?.status as? CapabilityStatus.PermissionRequired)
                        ?.permission == AppPermission.POST_NOTIFICATIONS
                ) {
                    "Notifications permission denied"
                } else {
                    "Notifications"
                },
                detail = when (val status = reminderCapability?.status) {
                    is CapabilityStatus.PermissionRequired ->
                        "Notification access is required by the reminder provider but has not been granted."
                    CapabilityStatus.Available ->
                        "Permission is granted for the installed reminder provider."
                    else -> "No notification permission is needed by the current build."
                },
            )
            if ((reminderCapability?.status as? CapabilityStatus.PermissionRequired)
                    ?.permission == AppPermission.POST_NOTIFICATIONS
            ) {
                OutlinedButton(onClick = onRequestNotificationPermission) {
                    Text("Allow reminder notifications")
                }
            }
        }
        SettingsSection("REMINDERS & SHORTCUTS", "Supported actions are local and require explicit user commands.") {
            UnavailableLine(
                title = "Reminders",
                detail = "Create a local reminder by command. Android may defer its alarm, and notification permission is required where the OS requires it.",
            )
            UnavailableLine(
                title = "Safe shortcuts",
                detail = "Create and repeat supported local commands. Arbitrary code or unattended automation is not supported.",
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsLoadState(error: Boolean) {
    SettingsSection(if (error) "PREFERENCES UNAVAILABLE" else "LOADING", "") {
        Text(
            text = if (error) {
                "Your saved preferences could not be read. Settings are unavailable until storage recovers."
            } else {
                "Loading preferences stored on this device…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PageHeading(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 24.dp, bottom = 18.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Zero") }
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Gravity") }
            },
            modifier = Modifier.padding(bottom = 18.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            subtitle,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.4.sp,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun UnavailableLine(title: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun microphoneStatus(capabilities: List<CapabilityInfo>): Pair<String, String> {
    return when (
        val status = capabilities.firstOrNull {
            it.capability == AssistantCapability.SPEECH_TO_TEXT
        }?.status
    ) {
        is CapabilityStatus.PermissionRequired -> "Microphone permission denied" to
            "Allow microphone access in Android settings to use the installed speech provider."
        CapabilityStatus.Available -> "Microphone permission granted" to
            "Android grants microphone access; on-device speech recognition availability is still device-dependent."
        is CapabilityStatus.Unavailable, null -> "Microphone permission" to
            "Speech input is unavailable in this build."
    }
}
