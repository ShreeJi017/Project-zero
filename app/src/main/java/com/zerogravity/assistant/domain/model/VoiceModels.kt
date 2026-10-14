package com.zerogravity.assistant.domain.model

data class VoiceDescriptor(
    val id: String,
    val languageTag: String,
    val displayName: String,
    val requiresNetwork: Boolean,
    val quality: Int,
)

sealed interface SpeechInputState {
    data object Idle : SpeechInputState
    data object Recording : SpeechInputState
    data object Processing : SpeechInputState
    data class Recognized(val text: String) : SpeechInputState
    data object NoSpeech : SpeechInputState
    data object PermissionDenied : SpeechInputState
    data class Error(val reason: FailureReason) : SpeechInputState
}

object SpeechInputTransitions {
    fun failed(reason: FailureReason): SpeechInputState = when (reason) {
        FailureReason.NO_SPEECH -> SpeechInputState.NoSpeech
        FailureReason.PERMISSION_DENIED -> SpeechInputState.PermissionDenied
        else -> SpeechInputState.Error(reason)
    }

    fun result(text: String?): SpeechInputState =
        text?.takeIf(String::isNotBlank)?.let { SpeechInputState.Recognized(it) }
            ?: SpeechInputState.NoSpeech
}

data class VoiceConfiguration(
    val languageTag: String = "en-IN",
    val voiceName: String = "",
    val speechRate: Float = 1f,
)

sealed interface BatteryWarningDecision {
    data object None : BatteryWarningDecision
    data object SpeakLowBatteryWarning : BatteryWarningDecision
    data object RearmWarning : BatteryWarningDecision
}

class BatteryWarningPolicy(
    private val thresholdPercent: Int = 15,
    private val rearmPercent: Int = minOf(100, maxOf(thresholdPercent + 5, 20)),
) {
    init {
        require(thresholdPercent in 1..100)
        require(rearmPercent in thresholdPercent..100)
    }

    fun evaluate(batteryPercent: Int, warningAlreadyDelivered: Boolean): BatteryWarningDecision =
        when {
            batteryPercent >= rearmPercent && warningAlreadyDelivered ->
                BatteryWarningDecision.RearmWarning
            batteryPercent in 0..thresholdPercent && !warningAlreadyDelivered ->
                BatteryWarningDecision.SpeakLowBatteryWarning
            else -> BatteryWarningDecision.None
        }
}
