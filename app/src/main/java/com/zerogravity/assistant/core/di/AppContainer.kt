package com.zerogravity.assistant.core.di

import android.content.Context
import com.zerogravity.assistant.core.logging.AndroidAppLogger
import com.zerogravity.assistant.core.permission.AndroidPermissionManager
import com.zerogravity.assistant.data.preferences.DataStoreSettingsRepository
import com.zerogravity.assistant.domain.command.DeterministicCommandRouter
import com.zerogravity.assistant.domain.model.AppPermission
import com.zerogravity.assistant.domain.port.CapabilityProvider
import com.zerogravity.assistant.domain.port.CapabilityRegistry
import com.zerogravity.assistant.domain.port.HistoryRepository
import com.zerogravity.assistant.domain.port.LocalCommandProcessor
import com.zerogravity.assistant.domain.port.SettingsRepository
import com.zerogravity.assistant.platform.command.AndroidLocalDeviceActions
import com.zerogravity.assistant.platform.command.AndroidPhoneActionGateway
import com.zerogravity.assistant.platform.command.AndroidShortcutRepository
import com.zerogravity.assistant.platform.capability.DefaultCapabilityRegistry
import com.zerogravity.assistant.platform.reminder.AndroidReminderRepository
import com.zerogravity.assistant.platform.reminder.AndroidReminderScheduler
import com.zerogravity.assistant.domain.port.SpeechToTextEngine
import com.zerogravity.assistant.platform.voice.AndroidOnDeviceSpeechRecognizer
import com.zerogravity.assistant.platform.voice.AndroidTextToSpeechEngine
import com.zerogravity.assistant.domain.port.TextToSpeechEngine
import com.zerogravity.assistant.domain.port.VoicePackManager

class AppContainer(
    context: Context,
    val historyRepository: HistoryRepository? = null,
) {
    private val appContext = context.applicationContext
    private val logger = AndroidAppLogger()
    val permissionManager = AndroidPermissionManager(appContext)
    private val reminderScheduler = AndroidReminderScheduler(appContext)
    private val reminderRepository = AndroidReminderRepository(
        appContext,
        reminderScheduler,
        permissionManager,
    )
    private val shortcutRepository = AndroidShortcutRepository(appContext)
    val textToSpeechEngine = AndroidTextToSpeechEngine(appContext)
    val voicePackManager: VoicePackManager = textToSpeechEngine
    val speechToTextEngine: SpeechToTextEngine =
        AndroidOnDeviceSpeechRecognizer(appContext, permissionManager)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext)
    val commandProcessor: LocalCommandProcessor = DeterministicCommandRouter(
        deviceActions = AndroidLocalDeviceActions(appContext),
        reminders = reminderRepository,
        shortcuts = shortcutRepository,
        phoneActions = AndroidPhoneActionGateway(appContext),
    )
    val capabilityRegistry: CapabilityRegistry = DefaultCapabilityRegistry(
        providers = listOf(
            object : CapabilityProvider {
                override val capability = com.zerogravity.assistant.domain.model.AssistantCapability.LOCAL_COMMANDS
                override val requiredPermissions = emptySet<AppPermission>()
            },
            object : CapabilityProvider {
                override val capability = com.zerogravity.assistant.domain.model.AssistantCapability.REMINDERS
                override val requiredPermissions = setOf(AppPermission.POST_NOTIFICATIONS)
            },
            object : CapabilityProvider {
                override val capability = com.zerogravity.assistant.domain.model.AssistantCapability.SPEECH_TO_TEXT
                override val requiredPermissions = setOf(AppPermission.MICROPHONE)
            },
            object : CapabilityProvider {
                override val capability = com.zerogravity.assistant.domain.model.AssistantCapability.TEXT_TO_SPEECH
                override val requiredPermissions = emptySet<AppPermission>()
            },
        ),
        permissionManager = permissionManager,
    )
    val appLogger = logger
}