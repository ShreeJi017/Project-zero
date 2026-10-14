package com.zerogravity.assistant.domain.command

import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.LocalModel
import com.zerogravity.assistant.domain.model.LocalShortcut
import com.zerogravity.assistant.domain.model.PhoneAction
import com.zerogravity.assistant.domain.model.Reminder
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.LocalDeviceActions
import com.zerogravity.assistant.domain.port.PhoneActionGateway
import com.zerogravity.assistant.domain.port.LocalLanguageModel
import com.zerogravity.assistant.domain.port.LocalModelRepository
import com.zerogravity.assistant.domain.port.ReminderRepository
import com.zerogravity.assistant.domain.port.SupportedApp
import com.zerogravity.assistant.domain.port.ShortcutRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicCommandRouterTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-10-14T13:04:00Z"), ZoneOffset.UTC)

    @Test
    fun reportsDeterministicTimeAndDate() = runBlocking {
        val router = router()

        assertEquals(ServiceResult.Success("1:04 PM"), router.process("time"))
        assertEquals(ServiceResult.Success("Wednesday, 14 October 2026"), router.process("date"))
    }

    @Test
    fun onlyReportsDeviceActionAfterGatewayConfirmsIt() = runBlocking {
        val actions = FakeDeviceActions(
            torchResult = ServiceResult.Failure(FailureReason.PERMISSION_DENIED),
        )
        val router = router(deviceActions = actions)

        assertEquals(ServiceResult.Failure(FailureReason.PERMISSION_DENIED), router.process("torch on"))
        assertTrue(actions.torchRequested)
        assertEquals(
            ServiceResult.Success("Opened Camera."),
            router.process("open camera"),
        )
    }

    @Test
    fun persistsLocallyScheduledReminderAndReportsFailure() = runBlocking {
        val reminders = FakeReminders()
        val router = router(reminders = reminders)

        assertEquals(
            ServiceResult.Success("Reminder set: take medicine"),
            router.process("remind me to take medicine in 10 minutes"),
        )
        assertEquals(fixedClock.millis() + 600_000L, reminders.saved.single().scheduledAtEpochMillis)

        reminders.result = ServiceResult.Failure(FailureReason.IO_ERROR)
        assertEquals(
            ServiceResult.Failure(FailureReason.IO_ERROR),
            router.process("remind me to call home in 2 minutes"),
        )
        assertEquals(
            ServiceResult.Failure(FailureReason.INVALID_INPUT),
            router.process("remind me to wait in 9223372036854775 seconds"),
        )
    }

    @Test
    fun unknownRequestIsHonestAndDoesNotCallCloudOrAnUninstalledModel() = runBlocking {
        val models = FakeModels()
        var inferenceCalled = false
        val model = object : LocalLanguageModel {
            override suspend fun respond(prompt: String): ServiceResult<String> {
                inferenceCalled = true
                return ServiceResult.Success("local response")
            }
        }
        val router = router(models = models, languageModel = model)

        val result = router.process("tell me a story")

        assertTrue(result is ServiceResult.Success)
        assertTrue((result as ServiceResult.Success).value.contains("no cloud service was contacted"))
        assertFalse(inferenceCalled)
    }

    @Test
    fun explicitlyExplainsWhatsAppIsNotDirectlyAccessible() = runBlocking {
        val result = router().process("read my WhatsApp messages")

        assertTrue(result is ServiceResult.Success)
        assertTrue((result as ServiceResult.Success).value.contains("does not read or send them"))
    }

    @Test
    fun liveWeatherNeverUsesAnUnconfiguredModelAsAProvider() = runBlocking {
        val models = FakeModels(listOf(LocalModel("test", "Test model", 1)))
        var inferenceCalled = false
        val model = object : LocalLanguageModel {
            override suspend fun respond(prompt: String): ServiceResult<String> {
                inferenceCalled = true
                return ServiceResult.Success("model guess")
            }
        }

        val response = router(models = models, languageModel = model).process("weather in Delhi")

        assertTrue(response is ServiceResult.Success)
        assertTrue((response as ServiceResult.Success).value.contains("No provider is configured"))
        assertFalse(inferenceCalled)
    }

    @Test
    fun propagatesLocalInferenceFailuresAndCancellation() = runBlocking {
        val models = FakeModels(listOf(LocalModel("test", "Test model", 1)))
        val failure = ServiceResult.Failure(FailureReason.IO_ERROR)
        val model = object : LocalLanguageModel {
            override suspend fun respond(prompt: String): ServiceResult<String> = failure
        }
        assertEquals(failure, router(models = models, languageModel = model).process("tell me a story"))

        val cancellingActions = object : LocalDeviceActions {
            override suspend fun openApp(app: SupportedApp) = ServiceResult.Success("opened")
            override suspend fun setTorch(enabled: Boolean) = ServiceResult.Success("changed")
            override suspend fun batteryStatus(): ServiceResult<String> {
                throw CancellationException("cancelled")
            }
        }
        try {
            router(deviceActions = cancellingActions).process("battery")
            throw AssertionError("Expected cancellation to propagate")
        } catch (_: CancellationException) {
            assertTrue(true)
        }
    }

    @Test
    fun shortcutsRepeatOnlySupportedLocalCommands() = runBlocking {
        val shortcuts = FakeShortcuts()
        val actions = FakeDeviceActions()
        val router = router(deviceActions = actions, shortcuts = shortcuts)

        assertEquals(ServiceResult.Success("Shortcut saved: desk"), router.process("shortcut desk = time"))
        assertEquals(ServiceResult.Success("1:04 PM"), router.process("repeat shortcut desk"))
        assertTrue(shortcuts.find("desk") != null)
        assertEquals(ServiceResult.Failure(FailureReason.INVALID_INPUT), router.process("shortcut unsafe = run arbitrary script"))
    }

    @Test
    fun routesCallsAndMessagesOnlyToRegisteredSystemHandoff() = runBlocking {
        val gateway = FakePhoneActions()
        val router = router(phoneActions = gateway)

        assertEquals(
            ServiceResult.Success("Dialer opened; confirm in Android."),
            router.process("call +14155550123"),
        )
        assertEquals(
            PhoneAction.PlaceCall("+14155550123"),
            gateway.actions.single(),
        )
        assertEquals(
            ServiceResult.Success("SMS composer opened; confirm in Android."),
            router.process("text to +14155550123: Hello there"),
        )
        assertEquals(
            PhoneAction.SendTextMessage("+14155550123", "Hello there"),
            gateway.actions.last(),
        )
        assertEquals(
            ServiceResult.Failure(FailureReason.NOT_AVAILABLE),
            router().process("call +14155550123"),
        )
    }

    @Test
    fun propagatesSystemPhoneHandoffFailures() = runBlocking {
        val gateway = FakePhoneActions(
            result = ServiceResult.Failure(FailureReason.NOT_AVAILABLE),
        )

        assertEquals(
            ServiceResult.Failure(FailureReason.NOT_AVAILABLE),
            router(phoneActions = gateway).process("call +14155550123"),
        )
    }

    private fun router(
        deviceActions: LocalDeviceActions = FakeDeviceActions(),
        reminders: FakeReminders = FakeReminders(),
        models: FakeModels? = null,
        languageModel: LocalLanguageModel? = null,
        shortcuts: ShortcutRepository? = null,
        phoneActions: PhoneActionGateway? = null,
    ) = DeterministicCommandRouter(
        deviceActions = deviceActions,
        reminders = reminders,
        clock = fixedClock,
        models = models,
        languageModel = languageModel,
        shortcuts = shortcuts,
        phoneActions = phoneActions,
    )

    private class FakeDeviceActions(
        private val torchResult: ServiceResult<String> = ServiceResult.Success("Torch turned on."),
    ) : LocalDeviceActions {
        var torchRequested = false

        override suspend fun openApp(app: SupportedApp) =
            ServiceResult.Success("Opened ${app.name.lowercase().replaceFirstChar(Char::uppercase)}.")

        override suspend fun setTorch(enabled: Boolean): ServiceResult<String> {
            torchRequested = true
            return torchResult
        }

        override suspend fun batteryStatus() = ServiceResult.Success("50%")
    }

    private class FakePhoneActions(
        private val result: ServiceResult<String> = ServiceResult.Success(""),
    ) : PhoneActionGateway {
        val actions = mutableListOf<PhoneAction>()

        override suspend fun execute(action: PhoneAction): ServiceResult<String> {
            actions += action
            if (result is ServiceResult.Failure) return result
            return ServiceResult.Success(
                when (action) {
                    is PhoneAction.PlaceCall -> "Dialer opened; confirm in Android."
                    is PhoneAction.SendTextMessage -> "SMS composer opened; confirm in Android."
                },
            )
        }
    }

    private class FakeReminders : ReminderRepository {
        private val flow = MutableStateFlow<List<Reminder>>(emptyList())
        override val reminders: Flow<List<Reminder>> = flow
        val saved = mutableListOf<Reminder>()
        var result: ServiceResult<Unit> = ServiceResult.Success(Unit)

        override suspend fun save(reminder: Reminder): ServiceResult<Unit> {
            if (result is ServiceResult.Success) {
                saved += reminder
                flow.value = saved.toList()
            }
            return result
        }

        override suspend fun delete(reminderId: String) = ServiceResult.Success(Unit)
    }

    private class FakeModels(installed: List<LocalModel> = emptyList()) : LocalModelRepository {
        override val installedModels: Flow<List<LocalModel>> = MutableStateFlow(installed)
        override suspend fun download(model: LocalModel) = ServiceResult.Success(Unit)
        override suspend fun delete(modelId: String) = ServiceResult.Success(Unit)
    }

    private class FakeShortcuts : ShortcutRepository {
        private val saved = linkedMapOf<String, LocalShortcut>()
        override val shortcuts: Flow<List<LocalShortcut>>
            get() = MutableStateFlow(saved.values.toList())

        override suspend fun save(shortcut: LocalShortcut): ServiceResult<Unit> {
            saved[shortcut.name.lowercase()] = shortcut
            return ServiceResult.Success(Unit)
        }

        override suspend fun find(name: String): LocalShortcut? = saved[name.lowercase()]
        override suspend fun delete(name: String): ServiceResult<Unit> =
            if (saved.remove(name.lowercase()) != null) ServiceResult.Success(Unit)
            else ServiceResult.Failure(FailureReason.INVALID_INPUT)
    }
}
