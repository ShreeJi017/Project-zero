package com.zerogravity.assistant.domain.model

data class AssistantSettings(
    val hasCompletedSetup: Boolean,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val responseStyle: ResponseStyle = ResponseStyle.BALANCED,
    val assistantName: String = "Zero",
    val voiceResponsesEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val batteryWarningsEnabled: Boolean = true,
    val voiceLanguageTag: String = "en-IN",
    val voiceName: String = "",
    val speechRate: Float = 1f,
    val batteryWarningThresholdPercent: Int = 15,
    val remindersEnabled: Boolean = true,
    val automationEnabled: Boolean = false,
)

enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ResponseStyle {
    CONCISE,
    BALANCED,
    DETAILED,
}