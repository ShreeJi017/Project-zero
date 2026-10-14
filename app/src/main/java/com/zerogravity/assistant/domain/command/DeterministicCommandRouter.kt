package com.zerogravity.assistant.domain.command

import com.zerogravity.assistant.domain.model.Reminder
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.model.LocalShortcut
import com.zerogravity.assistant.domain.model.PhoneAction
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.port.LocalDeviceActions
import com.zerogravity.assistant.domain.port.LocalLanguageModel
import com.zerogravity.assistant.domain.port.LocalModelRepository
import com.zerogravity.assistant.domain.port.LocalCommandProcessor
import com.zerogravity.assistant.domain.port.PhoneActionGateway
import com.zerogravity.assistant.domain.port.ReminderRepository
import com.zerogravity.assistant.domain.port.ShortcutRepository
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.flow.first

class DeterministicCommandRouter(
    private val deviceActions: LocalDeviceActions,
    private val reminders: ReminderRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val models: LocalModelRepository? = null,
    private val languageModel: LocalLanguageModel? = null,
    private val shortcuts: ShortcutRepository? = null,
    private val parser: CommandParser = CommandParser(),
    private val phoneActions: PhoneActionGateway? = null,
) : LocalCommandProcessor {
    override suspend fun process(command: String): ServiceResult<String> =
        execute(parser.parse(command), command)

    private suspend fun execute(parsed: ParsedCommand, original: String): ServiceResult<String> =
        when (parsed) {
            ParsedCommand.Help -> ServiceResult.Success(HELP)
            ParsedCommand.CurrentTime -> ServiceResult.Success(
                DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                    .withZone(clock.zone)
                    .format(Instant.now(clock)),
            )
            ParsedCommand.CurrentDate -> ServiceResult.Success(
                DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
                    .withZone(clock.zone)
                    .format(Instant.now(clock)),
            )
            ParsedCommand.BatteryStatus -> deviceActions.batteryStatus()
            is ParsedCommand.OpenApp -> deviceActions.openApp(parsed.app)
            is ParsedCommand.SetTorch -> deviceActions.setTorch(parsed.enabled)
            is ParsedCommand.PlaceCall -> executePhoneAction(PhoneAction.PlaceCall(parsed.phoneNumber))
            is ParsedCommand.ComposeTextMessage -> executePhoneAction(
                PhoneAction.SendTextMessage(parsed.phoneNumber, parsed.message),
            )
            is ParsedCommand.CreateReminder -> createReminder(parsed)
            is ParsedCommand.SaveShortcut -> saveShortcut(parsed)
            is ParsedCommand.RunShortcut -> runShortcut(parsed.name)
            ParsedCommand.Unknown -> respondToUnknown(original)
        }

    private suspend fun saveShortcut(command: ParsedCommand.SaveShortcut): ServiceResult<String> {
        val repository = shortcuts ?: return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        val safeCommand = parser.parse(command.command)
        if (!isRepeatableLocalCommand(safeCommand)) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val shortcut = LocalShortcut(command.name.trim(), command.command.trim())
        return when (val saved = repository.save(shortcut)) {
            is ServiceResult.Failure -> saved
            is ServiceResult.Success -> ServiceResult.Success("Shortcut saved: ${shortcut.name}")
        }
    }

    private suspend fun executePhoneAction(action: PhoneAction): ServiceResult<String> {
        val gateway = phoneActions ?: return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        return gateway.execute(action)
    }

    private suspend fun runShortcut(name: String): ServiceResult<String> {
        val repository = shortcuts ?: return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        val shortcut = repository.find(name.trim())
            ?: return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        val parsed = parser.parse(shortcut.command)
        if (!isRepeatableLocalCommand(parsed)) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        return execute(parsed, shortcut.command)
    }

    private fun isRepeatableLocalCommand(command: ParsedCommand): Boolean = when (command) {
        ParsedCommand.CurrentTime,
        ParsedCommand.CurrentDate,
        ParsedCommand.BatteryStatus -> true
        else -> false
    }

    private suspend fun createReminder(command: ParsedCommand.CreateReminder): ServiceResult<String> {
        val scheduledAt = try {
            Math.addExact(clock.millis(), command.delayMillis)
        } catch (_: ArithmeticException) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            title = command.title,
            scheduledAtEpochMillis = scheduledAt,
        )
        return when (val saved = reminders.save(reminder)) {
            is ServiceResult.Failure -> saved
            is ServiceResult.Success -> ServiceResult.Success("Reminder set: ${reminder.title}")
        }
    }

    private suspend fun respondToUnknown(command: String): ServiceResult<String> {
        if (command.contains("whatsapp", ignoreCase = true)) {
            return ServiceResult.Success(
                "Direct WhatsApp message access is unavailable. Open WhatsApp and review or send messages there; ZeroGravity does not read or send them.",
            )
        }
        if (command.contains("weather", ignoreCase = true) || command.contains("मौसम")) {
            return ServiceResult.Success(
                "Live weather needs an internet connection and a configured weather provider. No provider is configured.",
            )
        }
        val installed = models?.installedModels?.first().orEmpty()
        if (languageModel != null && installed.isNotEmpty()) {
            return languageModel.respond(command)
        }
        return ServiceResult.Success(
            "I don't recognize that as a supported local command. Flexible conversation needs an installed, verified on-device model; no cloud service was contacted.",
        )
    }

    private companion object {
        const val HELP = "I can tell the time or date, report battery status, open Camera/Settings/Calculator/Clock, control the torch, set reminders, open the dialer with “call +14155550123”, and open an SMS composer with “text to +14155550123: Hello”. Calls and texts are not placed or sent until you confirm them in Android's app. Save an informational shortcut with “shortcut time = time”, then repeat it with “run shortcut time”."
    }
}
