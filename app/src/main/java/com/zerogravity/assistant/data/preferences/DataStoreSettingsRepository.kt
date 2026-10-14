package com.zerogravity.assistant.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zerogravity.assistant.domain.model.AppearanceMode
import com.zerogravity.assistant.domain.model.AssistantSettings
import com.zerogravity.assistant.domain.model.ResponseStyle
import com.zerogravity.assistant.domain.port.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "assistant_settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AssistantSettings> =
        dataStore.data.map { preferences -> preferences.toAssistantSettings() }

    override suspend fun update(transform: AssistantSettings.() -> AssistantSettings) {
        dataStore.edit { preferences ->
            val settings = transform(preferences.toAssistantSettings())
            preferences[HAS_COMPLETED_SETUP] = settings.hasCompletedSetup
            preferences[APPEARANCE] = settings.appearance.name
            preferences[RESPONSE_STYLE] = settings.responseStyle.name
            preferences[ASSISTANT_NAME] = settings.assistantName
            preferences[VOICE_RESPONSES_ENABLED] = settings.voiceResponsesEnabled
            preferences[WAKE_WORD_ENABLED] = settings.wakeWordEnabled
            preferences[BATTERY_WARNINGS_ENABLED] = settings.batteryWarningsEnabled
            preferences[VOICE_LANGUAGE_TAG] = settings.voiceLanguageTag
            preferences[VOICE_NAME] = settings.voiceName
            preferences[SPEECH_RATE] = settings.speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
            preferences[BATTERY_WARNING_THRESHOLD] =
                settings.batteryWarningThresholdPercent.coerceIn(1, 100)
            preferences[REMINDERS_ENABLED] = settings.remindersEnabled
            preferences[AUTOMATION_ENABLED] = settings.automationEnabled
        }
    }

    private fun Preferences.toAssistantSettings() = AssistantSettings(
        hasCompletedSetup = this[HAS_COMPLETED_SETUP] ?: false,
        appearance = this[APPEARANCE]?.let { value ->
            AppearanceMode.entries.firstOrNull { it.name == value }
        } ?: AppearanceMode.SYSTEM,
        responseStyle = this[RESPONSE_STYLE]?.let { value ->
            ResponseStyle.entries.firstOrNull { it.name == value }
        } ?: ResponseStyle.BALANCED,
        assistantName = this[ASSISTANT_NAME] ?: "Zero",
        voiceResponsesEnabled = this[VOICE_RESPONSES_ENABLED] ?: true,
        wakeWordEnabled = this[WAKE_WORD_ENABLED] ?: false,
        batteryWarningsEnabled = this[BATTERY_WARNINGS_ENABLED] ?: true,
        voiceLanguageTag = this[VOICE_LANGUAGE_TAG] ?: "en-IN",
        voiceName = this[VOICE_NAME] ?: "",
        speechRate = (this[SPEECH_RATE] ?: 1f).coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
        batteryWarningThresholdPercent =
            (this[BATTERY_WARNING_THRESHOLD] ?: 15).coerceIn(1, 100),
        remindersEnabled = this[REMINDERS_ENABLED] ?: true,
        automationEnabled = this[AUTOMATION_ENABLED] ?: false,
    )

    private companion object {
        val HAS_COMPLETED_SETUP = booleanPreferencesKey("has_completed_setup")
        val APPEARANCE = stringPreferencesKey("appearance")
        val RESPONSE_STYLE = stringPreferencesKey("response_style")
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val VOICE_RESPONSES_ENABLED = booleanPreferencesKey("voice_responses_enabled")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val BATTERY_WARNINGS_ENABLED = booleanPreferencesKey("battery_warnings_enabled")
        val VOICE_LANGUAGE_TAG = stringPreferencesKey("voice_language_tag")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val BATTERY_WARNING_THRESHOLD = intPreferencesKey("battery_warning_threshold")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation_enabled")
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2f
    }
}