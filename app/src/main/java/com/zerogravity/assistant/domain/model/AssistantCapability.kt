package com.zerogravity.assistant.domain.model

enum class AssistantCapability(val displayName: String) {
    LOCAL_COMMANDS("Local commands"),
    DOWNLOADABLE_MODELS("Downloadable local models"),
    SPEECH_TO_TEXT("Speech to text"),
    TEXT_TO_SPEECH("Text to speech"),
    WAKE_WORD("Wake word detection"),
    REMINDERS("Reminders"),
    PHONE_ACTIONS("Phone actions"),
    CALENDAR("Calendar"),
    WEATHER("Weather"),
}

enum class AppPermission {
    MICROPHONE,
    POST_NOTIFICATIONS,
    CAMERA,
    CALENDAR_READ,
}

sealed interface CapabilityStatus {
    data object Available : CapabilityStatus

    data class PermissionRequired(val permission: AppPermission) : CapabilityStatus

    data class Unavailable(val reason: String) : CapabilityStatus
}

data class CapabilityInfo(
    val capability: AssistantCapability,
    val status: CapabilityStatus,
)