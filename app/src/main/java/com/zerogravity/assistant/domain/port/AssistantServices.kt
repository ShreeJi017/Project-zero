package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.LocalModel
import com.zerogravity.assistant.domain.model.LocalShortcut
import com.zerogravity.assistant.domain.model.CalendarEvent
import com.zerogravity.assistant.domain.model.PhoneAction
import com.zerogravity.assistant.domain.model.Reminder
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.WeatherObservation
import com.zerogravity.assistant.domain.model.SpeechInputState
import com.zerogravity.assistant.domain.model.VoiceConfiguration
import com.zerogravity.assistant.domain.model.VoiceDescriptor
import com.zerogravity.assistant.domain.model.ServiceResult
import kotlinx.coroutines.flow.Flow

interface LocalCommandProcessor {
    suspend fun process(command: String): ServiceResult<String>
}

interface LocalModelRepository {
    val installedModels: Flow<List<LocalModel>>

    suspend fun download(model: LocalModel): ServiceResult<Unit>

    suspend fun delete(modelId: String): ServiceResult<Unit>

    suspend fun activate(modelId: String): ServiceResult<Unit> =
        ServiceResult.Failure(FailureReason.NOT_AVAILABLE)

    suspend fun cancelDownload(modelId: String): ServiceResult<Unit> =
        ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
}

interface LocalLanguageModel {
    suspend fun respond(prompt: String): ServiceResult<String>
}

interface ShortcutRepository {
    val shortcuts: Flow<List<LocalShortcut>>

    suspend fun save(shortcut: LocalShortcut): ServiceResult<Unit>

    suspend fun find(name: String): LocalShortcut?

    suspend fun delete(name: String): ServiceResult<Unit>
}

interface WeatherProvider {
    suspend fun currentWeather(city: String): ServiceResult<WeatherObservation>
}

interface CalendarProvider {
    suspend fun events(
        fromEpochMillis: Long,
        untilEpochMillis: Long,
    ): ServiceResult<List<CalendarEvent>>
}

interface LocalDeviceActions {
    suspend fun openApp(app: SupportedApp): ServiceResult<String>

    suspend fun setTorch(enabled: Boolean): ServiceResult<String>

    suspend fun batteryStatus(): ServiceResult<String>
}

enum class SupportedApp(val packageName: String, vararg val aliases: String) {
    CAMERA("com.android.camera2", "camera", "कैमरा"),
    SETTINGS("com.android.settings", "settings", "सेटिंग्स", "setting"),
    CALCULATOR("com.google.android.calculator", "calculator", "calc", "कैलकुलेटर"),
    CLOCK("com.google.android.deskclock", "clock", "घड़ी", "alarm"),
}

interface SpeechToTextEngine {
    val state: Flow<SpeechInputState>

    suspend fun recognizeOnce(languageTag: String): ServiceResult<String>

    suspend fun cancel()
}

interface TextToSpeechEngine {
    val installedVoices: Flow<List<VoiceDescriptor>>

    suspend fun configure(configuration: VoiceConfiguration): ServiceResult<Unit>

    suspend fun speak(text: String): ServiceResult<Unit>

    suspend fun stop()
}

interface VoicePackManager {
    suspend fun requestOfflineVoice(languageTag: String): ServiceResult<Unit>

    suspend fun refreshInstalledVoices(): ServiceResult<List<VoiceDescriptor>>
}

interface WakeWordDetector {
    val detections: Flow<Unit>

    suspend fun start(): ServiceResult<Unit>

    suspend fun stop()
}

interface ReminderRepository {
    val reminders: Flow<List<Reminder>>

    suspend fun save(reminder: Reminder): ServiceResult<Unit>

    suspend fun delete(reminderId: String): ServiceResult<Unit>
}

interface PhoneActionGateway {
    suspend fun execute(action: PhoneAction): ServiceResult<String>
}