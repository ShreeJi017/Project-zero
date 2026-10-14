package com.zerogravity.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zerogravity.assistant.core.di.AppContainer
import com.zerogravity.assistant.core.logging.AppLogger
import com.zerogravity.assistant.domain.command.CommandParser
import com.zerogravity.assistant.domain.command.ParsedCommand
import com.zerogravity.assistant.domain.model.AssistantSettings
import com.zerogravity.assistant.domain.model.CapabilityInfo
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.model.SpeechInputState
import com.zerogravity.assistant.domain.model.VoiceConfiguration
import com.zerogravity.assistant.domain.model.VoiceDescriptor
import com.zerogravity.assistant.domain.port.CapabilityRegistry
import com.zerogravity.assistant.domain.port.LocalCommandProcessor
import com.zerogravity.assistant.domain.port.SpeechToTextEngine
import com.zerogravity.assistant.domain.port.SettingsRepository
import com.zerogravity.assistant.domain.port.TextToSpeechEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AssistantUiState(
    val capabilities: List<CapabilityInfo>,
    val settings: AssistantSettings? = null,
    val settingsError: Boolean = false,
    val activity: AssistantActivity = AssistantActivity.IDLE,
    val speechInput: SpeechInputState = SpeechInputState.Idle,
    val responseText: String = "",
    val voiceSessionActive: Boolean = false,
    val voiceOutputError: Boolean = false,
    val installedVoices: List<VoiceDescriptor> = emptyList(),
)

enum class AssistantActivity {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
}

class AssistantViewModel(
    private val capabilityRegistry: CapabilityRegistry,
    private val settingsRepository: SettingsRepository,
    private val commandProcessor: LocalCommandProcessor,
    private val speechToText: SpeechToTextEngine,
    private val textToSpeech: TextToSpeechEngine,
    private val logger: AppLogger,
) : ViewModel() {
    private val commandParser = CommandParser()
    private val capabilities = MutableStateFlow(capabilityRegistry.all())
    private val interaction = MutableStateFlow(VoiceInteraction())

    val uiState = combine(
        settingsRepository.settings,
        speechToText.state,
        textToSpeech.installedVoices,
        interaction,
        capabilities,
    ) { settings, speechInput, voices, currentInteraction, currentCapabilities ->
        AssistantUiState(
            capabilities = currentCapabilities,
            settings = settings,
            activity = when {
                currentInteraction.speaking -> AssistantActivity.SPEAKING
                speechInput is SpeechInputState.Recording -> AssistantActivity.LISTENING
                speechInput is SpeechInputState.Processing ||
                    speechInput is SpeechInputState.Recognized && currentInteraction.sessionActive ->
                    AssistantActivity.PROCESSING
                else -> AssistantActivity.IDLE
            },
            speechInput = speechInput,
            responseText = currentInteraction.responseText,
            voiceSessionActive = currentInteraction.sessionActive,
            voiceOutputError = currentInteraction.voiceOutputError,
            installedVoices = voices,
        )
    }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            logger.error("Unable to load local settings", throwable)
            emit(AssistantUiState(capabilities = capabilities.value, settingsError = true))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AssistantUiState(capabilities = capabilities.value),
        )

    fun refreshCapabilities() {
        capabilities.value = capabilityRegistry.all()
    }

    suspend fun updateSettings(transform: AssistantSettings.() -> AssistantSettings) {
        settingsRepository.update(transform)
    }

    fun submitText(command: String, onResult: (ServiceResult<String>) -> Unit) {
        viewModelScope.launch {
            if (interaction.value.sessionActive) {
                voiceJob?.cancel()
                speechToText.cancel()
                textToSpeech.stop()
                interaction.value = interaction.value.copy(sessionActive = false, speaking = false)
            }
            val result = commandProcessor.process(command)
            onResult(result)
            if (result is ServiceResult.Success) {
                interaction.value = interaction.value.copy(responseText = result.value)
                val settings = uiState.value.settings
                if (settings?.voiceResponsesEnabled == true && !opensExternalActivity(command)) {
                    speak(result.value, settings)
                }
            }
        }
    }

    fun startListening(
        continuous: Boolean,
        onCommandResult: (String, ServiceResult<String>) -> Unit = { _, _ -> },
    ) {
        if (interaction.value.sessionActive) return
        interaction.value = interaction.value.copy(sessionActive = true, voiceOutputError = false)
        voiceJob = viewModelScope.launch {
            try {
                textToSpeech.stop()
                do {
                    val settings = uiState.value.settings ?: settingsRepository.settings.first()
                    val recognition = speechToText.recognizeOnce(settings.voiceLanguageTag)
                    when (recognition) {
                        is ServiceResult.Failure -> {
                            interaction.value = interaction.value.copy(
                                responseText = recognition.reason.voiceMessage(),
                            )
                            break
                        }
                        is ServiceResult.Success -> {
                            interaction.value = interaction.value.copy(
                                responseText = "Heard: ${recognition.value}",
                            )
                            val result = commandProcessor.process(recognition.value)
                            val externalHandoff = opensExternalActivity(recognition.value)
                            if (result is ServiceResult.Failure) {
                                onCommandResult(recognition.value, result)
                                if (result.reason == FailureReason.PERMISSION_DENIED) break
                            }
                            when (result) {
                                is ServiceResult.Failure -> {
                                    interaction.value = interaction.value.copy(
                                        responseText = result.reason.voiceMessage(),
                                    )
                                }
                                is ServiceResult.Success -> {
                                    interaction.value = interaction.value.copy(responseText = result.value)
                                    if (settings.voiceResponsesEnabled && !externalHandoff) {
                                        speak(result.value, settings)
                                    }
                                }
                            }
                            if (externalHandoff) break
                        }
                    }
                } while (continuous && isActive)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                interaction.value = interaction.value.copy(
                    responseText = "Voice interaction failed. Try manual microphone input again.",
                )
                logger.error("Local voice interaction failed", exception)
            } finally {
                interaction.value = interaction.value.copy(sessionActive = false, speaking = false)
            }
        }
    }

    fun stopVoice() {
        val stopSpeech = interaction.value.sessionActive || interaction.value.speaking
        voiceJob?.cancel()
        viewModelScope.launch {
            speechToText.cancel()
            if (stopSpeech) textToSpeech.stop()
            interaction.value = interaction.value.copy(sessionActive = false, speaking = false)
        }
    }

    private suspend fun speak(text: String, settings: AssistantSettings) {
        val configuration = VoiceConfiguration(
            languageTag = settings.voiceLanguageTag,
            voiceName = settings.voiceName,
            speechRate = settings.speechRate,
        )
        when (textToSpeech.configure(configuration)) {
            is ServiceResult.Failure -> {
                interaction.value = interaction.value.copy(voiceOutputError = true)
            }
            is ServiceResult.Success -> {
                interaction.value = interaction.value.copy(speaking = true, voiceOutputError = false)
                try {
                    if (textToSpeech.speak(text) is ServiceResult.Failure) {
                        interaction.value = interaction.value.copy(voiceOutputError = true)
                    }
                } finally {
                    interaction.value = interaction.value.copy(speaking = false)
                }
            }
        }
    }

    private fun opensExternalActivity(command: String): Boolean =
        when (commandParser.parse(command)) {
            is ParsedCommand.OpenApp,
            is ParsedCommand.PlaceCall,
            is ParsedCommand.ComposeTextMessage -> true
            else -> false
        }

    fun reportSettingsError(throwable: Throwable) {
        logger.error("Unable to save local settings", throwable)
    }

    private var voiceJob: Job? = null

    private fun FailureReason.voiceMessage(): String = when (this) {
        FailureReason.PERMISSION_DENIED -> "Microphone permission denied. Allow it in Settings to try again."
        FailureReason.NO_SPEECH -> "I didn't hear speech. Try again in a quieter place."
        FailureReason.UNSUPPORTED_LANGUAGE ->
            "The selected offline speech language is not installed or supported on this device."
        FailureReason.NOT_AVAILABLE ->
            "Offline speech recognition is unavailable on this Android device."
        FailureReason.INVALID_INPUT -> "That voice request could not be processed."
        FailureReason.IO_ERROR -> "Offline speech recognition failed. Try again."
    }

    private data class VoiceInteraction(
        val responseText: String = "",
        val sessionActive: Boolean = false,
        val speaking: Boolean = false,
        val voiceOutputError: Boolean = false,
    )
}

class AssistantViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AssistantViewModel::class.java))
        return AssistantViewModel(
            capabilityRegistry = container.capabilityRegistry,
            settingsRepository = container.settingsRepository,
            commandProcessor = container.commandProcessor,
            speechToText = container.speechToTextEngine,
            textToSpeech = container.textToSpeechEngine,
            logger = container.appLogger,
        ) as T
    }
}