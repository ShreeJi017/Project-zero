package com.zerogravity.assistant.domain.command

import com.zerogravity.assistant.domain.port.SupportedApp
import com.zerogravity.assistant.domain.model.Reminder
import java.util.Locale

sealed interface ParsedCommand {
    data object Help : ParsedCommand
    data object CurrentTime : ParsedCommand
    data object CurrentDate : ParsedCommand
    data object BatteryStatus : ParsedCommand
    data class OpenApp(val app: SupportedApp) : ParsedCommand
    data class SetTorch(val enabled: Boolean) : ParsedCommand
    data class PlaceCall(val phoneNumber: String) : ParsedCommand
    data class ComposeTextMessage(val phoneNumber: String, val message: String) : ParsedCommand
    data class CreateReminder(val title: String, val delayMillis: Long) : ParsedCommand
    data class SaveShortcut(val name: String, val command: String) : ParsedCommand
    data class RunShortcut(val name: String) : ParsedCommand
    data object Unknown : ParsedCommand
}

class CommandParser {
    fun parse(input: String): ParsedCommand {
        val text = input.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")
        if (text.isEmpty()) return ParsedCommand.Unknown

        if (text in HELP_PHRASES) return ParsedCommand.Help
        if (text in TIME_PHRASES || text.containsAny("what time", "current time", "समय बताओ", "समय बताएं", "kitne baje")) {
            return ParsedCommand.CurrentTime
        }
        if (text in DATE_PHRASES || text.containsAny("today's date", "today date", "current date", "आज की तारीख", "aaj ki date")) {
            return ParsedCommand.CurrentDate
        }
        if (text in BATTERY_PHRASES ||
            text.startsWith("बैटरी ") ||
            text.startsWith("charge kitna ")
        ) {
            return ParsedCommand.BatteryStatus
        }
        parseTorch(text)?.let { return it }
        val originalText = input.trim()
        CALL_COMMAND.matchEntire(originalText)?.let { match ->
            return normalizedPhoneNumber(match.groupValues[1])?.let(ParsedCommand::PlaceCall)
                ?: ParsedCommand.Unknown
        }
        TEXT_COMMAND.matchEntire(originalText)?.let { match ->
            val phoneNumber = normalizedPhoneNumber(match.groupValues[1])
            val message = match.groupValues[2].trim()
            if (phoneNumber != null && message.isNotBlank() && message.length <= MAX_MESSAGE_LENGTH) {
                return ParsedCommand.ComposeTextMessage(phoneNumber, message)
            }
        }

        SAVE_SHORTCUT.matchEntire(text)?.let { match ->
            return ParsedCommand.SaveShortcut(match.groupValues[1].trim(), match.groupValues[2].trim())
        }
        RUN_SHORTCUT.matchEntire(text)?.let { match ->
            return ParsedCommand.RunShortcut(match.groupValues[1].trim())
        }
        parseReminder(text)?.let { return it }
        parseOpenApp(text)?.let { return it }
        return ParsedCommand.Unknown
    }

    private fun parseTorch(text: String): ParsedCommand.SetTorch? = when {
        text in TORCH_ON_PHRASES || TORCH_ON_PATTERNS.any { it.matches(text) } -> ParsedCommand.SetTorch(true)
        text in TORCH_OFF_PHRASES || TORCH_OFF_PATTERNS.any { it.matches(text) } -> ParsedCommand.SetTorch(false)
        else -> null
    }

    private fun parseOpenApp(text: String): ParsedCommand.OpenApp? {
        val target = (OPEN_PREFIX.find(text)?.groupValues?.get(1)
            ?: OPEN_SUFFIX.find(text)?.groupValues?.get(1))
            ?.removePrefix("the ")
            ?.removePrefix("my ")
            ?.removeSuffix(" kholo")
            ?.removeSuffix(" khol do")
            ?.trim()
            ?: return null
        val app = SupportedApp.entries.firstOrNull { supported ->
            target == supported.name.lowercase(Locale.ROOT) ||
                supported.aliases.any { alias -> target == alias.lowercase(Locale.ROOT) }
        } ?: return null
        return ParsedCommand.OpenApp(app)
    }

    private fun parseReminder(text: String): ParsedCommand.CreateReminder? {
        val english = ENGLISH_REMINDER.matchEntire(text)
        if (english != null) {
            val title = english.groupValues[1].trim().trimEnd('.', '!')
            val delay = durationMillis(english.groupValues[2].toLongOrNull(), english.groupValues[3])
            if (title.isNotBlank() && title.length <= Reminder.MAX_TITLE_LENGTH && delay != null) {
                return ParsedCommand.CreateReminder(title, delay)
            }
        }

        val hindi = HINDI_REMINDER.matchEntire(text)
        if (hindi != null) {
            val delay = durationMillis(hindi.groupValues[1].toLongOrNull(), hindi.groupValues[2])
            val title = hindi.groupValues[3].trim().removeSuffix(" की").trim()
            if (title.isNotBlank() && title.length <= Reminder.MAX_TITLE_LENGTH && delay != null) {
                return ParsedCommand.CreateReminder(title, delay)
            }
        }
        val hinglish = HINGLISH_REMINDER.matchEntire(text)
        if (hinglish != null) {
            val delay = durationMillis(hinglish.groupValues[1].toLongOrNull(), hinglish.groupValues[2])
            val title = hinglish.groupValues[3].trim()
            if (title.isNotBlank() && title.length <= Reminder.MAX_TITLE_LENGTH && delay != null) {
                return ParsedCommand.CreateReminder(title, delay)
            }
        }
        return null
    }

    private fun durationMillis(amount: Long?, unit: String): Long? {
        if (amount == null || amount <= 0) return null
        val multiplier = when (unit.lowercase(Locale.ROOT)) {
            "second", "seconds", "sec", "secs", "सेकंड" -> 1_000L
            "minute", "minutes", "min", "mins", "मिनट" -> 60_000L
            "hour", "hours", "hr", "hrs", "घंटा", "घंटे" -> 3_600_000L
            "day", "days", "दिन" -> 86_400_000L
            else -> return null
        }
        return amount.takeIf { it <= Long.MAX_VALUE / multiplier }?.times(multiplier)
    }

    private fun normalizedPhoneNumber(input: String): String? {
        if (!PHONE_NUMBER_PATTERN.matches(input)) return null
        val digits = input.filter(Char::isDigit)
        if (digits.length !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS) return null
        return (if (input.trimStart().startsWith("+")) "+" else "") + digits
    }

    private fun String.containsAny(vararg phrases: String): Boolean =
        phrases.any { phrase ->
            this == phrase || startsWith("$phrase ") || endsWith(" $phrase") || contains(" $phrase ")
        }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val OPEN_PREFIX = Regex("^(?:open|launch|start|khol|kholo|khol do|खोलो|खोल|chalao)\\s+(.+)$")
        val OPEN_SUFFIX = Regex("^(.+?)\\s+(?:khol|kholo|खोलो|खोल)$")
        val ENGLISH_REMINDER = Regex(
            "^(?:remind me to|reminder|set a reminder to)\\s+(.+?)\\s+(?:in|after)\\s+(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)$",
        )
        val HINDI_REMINDER = Regex(
            "^(\\d+)\\s*(मिनट|घंटे|घंटा|दिन|सेकंड)\\s*(?:बाद|baad)\\s+(.+?)\\s+(?:की\\s+)?(?:याद दिलाना|yaad dilana)$",
        )
        val HINGLISH_REMINDER = Regex(
            "^(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)\\s*(?:baad|बाद)\\s+(.+?)\\s+(?:ki\\s+)?(?:yaad dilana|yaad dila dena)$",
        )
        val SAVE_SHORTCUT = Regex("^shortcut\\s+([\\p{L}][\\p{L}\\p{N}_ -]{0,31})\\s*=\\s*(.+)$")
        val RUN_SHORTCUT = Regex("^(?:run|repeat)\\s+shortcut\\s+([\\p{L}][\\p{L}\\p{N}_ -]{0,31})$")
        val CALL_COMMAND = Regex(
            "^(?:call|dial|phone)\\s+([+]?\\d[\\d ()-]{2,22})$",
            RegexOption.IGNORE_CASE,
        )
        val TEXT_COMMAND = Regex(
            "^(?:text|sms|message|send sms to)\\s+(?:to\\s+)?([+]?\\d[\\d ()-]{2,22})\\s*[:,]\\s*(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val PHONE_NUMBER_PATTERN = Regex("\\+?\\d[\\d ()-]{2,22}")
        const val MIN_PHONE_DIGITS = 3
        const val MAX_PHONE_DIGITS = 20
        const val MAX_MESSAGE_LENGTH = 2_000
        val HELP_PHRASES = setOf("help", "commands", "मदद", "सहायता", "madad")
        val TIME_PHRASES = setOf("time", "what time is it", "समय बताओ", "समय बताएं", "kitne baje hain")
        val DATE_PHRASES = setOf("date", "today", "आज की तारीख", "aaj ki tareekh")
        val BATTERY_PHRASES = setOf(
            "battery",
            "battery status",
            "battery level",
            "बैटरी",
            "बैटरी स्टेटस",
            "charge kitna",
        )
        val TORCH_ON_PHRASES = setOf(
            "turn on torch", "torch on", "flashlight on", "turn on flashlight",
            "torch chalao", "टॉर्च चालू करो", "फ्लैशलाइट चालू करो",
            "torch on kar do", "flashlight on kar do",
        )
        val TORCH_OFF_PHRASES = setOf(
            "turn off torch", "torch off", "flashlight off", "turn off flashlight",
            "torch band karo", "टॉर्च बंद करो", "फ्लैशलाइट बंद करो",
            "torch off kar do", "torch band kar do", "flashlight off kar do",
        )
        val TORCH_ON_PATTERNS = listOf(
            Regex("^(?:please\\s+)?(?:(?:turn|switch)\\s+(?:(?:the|my)\\s+)?(?:torch|flashlight|flash\\s+light)\\s+on|(?:turn|switch)\\s+on\\s+(?:(?:the|my)\\s+)?(?:torch|flashlight|flash\\s+light)|(?:torch|flashlight|flash\\s+light)\\s+on)(?:\\s+please)?$"),
            Regex("^(?:please\\s+)?(?:torch|flashlight|flash\\s+light)\\s+(?:chalao|jalao|chalu\\s+(?:karo|kar\\s+do)|on\\s+kar\\s+do|चालू(?:\\s+करो)?|जलाओ)$"),
        )
        val TORCH_OFF_PATTERNS = listOf(
            Regex("^(?:please\\s+)?(?:(?:turn|switch)\\s+(?:(?:the|my)\\s+)?(?:torch|flashlight|flash\\s+light)\\s+off|(?:turn|switch)\\s+off\\s+(?:(?:the|my)\\s+)?(?:torch|flashlight|flash\\s+light)|(?:torch|flashlight|flash\\s+light)\\s+off|(?:torch|flashlight|flash\\s+light)\\s+bandh?(?:\\s+(?:karo|kar\\s+do))?)(?:\\s+please)?$"),
            Regex("^(?:please\\s+)?(?:टॉर्च|फ्लैशलाइट)\\s+(?:बंद|ऑफ)(?:\\s+करो|\\s+कर\\s+दो)?$"),
        )
    }
}
