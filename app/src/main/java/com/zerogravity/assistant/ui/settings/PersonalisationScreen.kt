package com.zerogravity.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerogravity.assistant.domain.model.AssistantSettings
import com.zerogravity.assistant.domain.model.ResponseStyle
import com.zerogravity.assistant.ui.navigation.horizontalPageSwipe
import kotlinx.coroutines.launch

@Composable
fun PersonalisationScreen(
    settings: AssistantSettings?,
    loadingError: Boolean,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onSettingsChange: suspend (AssistantSettings.() -> AssistantSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var assistantName by rememberSaveable(settings?.assistantName) {
        mutableStateOf(settings?.assistantName.orEmpty())
    }
    var responseStyle by rememberSaveable(settings?.responseStyle) {
        mutableStateOf(settings?.responseStyle ?: ResponseStyle.BALANCED)
    }
    var assistantNameHasError by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        PageHeading(
            "Personalisation",
            "Shape the way Zero feels to you.",
            modifier = Modifier.horizontalPageSwipe(onSwipeLeft, onSwipeRight),
        )
        if (settings == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = if (loadingError) {
                        "Preferences could not be loaded. Personalisation is unavailable."
                    } else {
                        "Loading your saved preferences…"
                    },
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ASSISTANT NAME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = assistantName,
                        onValueChange = {
                            assistantName = it
                            assistantNameHasError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Name") },
                        isError = assistantNameHasError,
                        supportingText = if (assistantNameHasError) {
                            { Text("Enter a name up to 24 characters.") }
                        } else {
                            { Text("Saved locally. Voice features are not connected yet.") }
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RESPONSE STYLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    ResponseStyle.entries.forEach { style ->
                        FilterChip(
                            selected = responseStyle == style,
                            onClick = { responseStyle = style },
                            label = {
                                Column {
                                    Text(style.title)
                                    Text(
                                        style.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Button(
                    onClick = {
                        val trimmedName = assistantName.trim()
                        if (trimmedName.isEmpty() || trimmedName.length > MAX_ASSISTANT_NAME_LENGTH) {
                            assistantNameHasError = true
                        } else {
                            scope.launch {
                                onSettingsChange {
                                    copy(
                                        assistantName = trimmedName,
                                        responseStyle = responseStyle,
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Save preferences")
                }
            }
        }
        Text(
            "These preferences are ready for the future conversation provider; they do not change responses until one is connected.",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}

private val ResponseStyle.title: String
    get() = when (this) {
        ResponseStyle.CONCISE -> "Concise"
        ResponseStyle.BALANCED -> "Balanced"
        ResponseStyle.DETAILED -> "Detailed"
    }

private val ResponseStyle.description: String
    get() = when (this) {
        ResponseStyle.CONCISE -> "Keep it brief and direct."
        ResponseStyle.BALANCED -> "A little context, without the extra."
        ResponseStyle.DETAILED -> "More explanation and useful detail."
    }

private const val MAX_ASSISTANT_NAME_LENGTH = 24
