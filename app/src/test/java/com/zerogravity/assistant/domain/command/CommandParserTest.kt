package com.zerogravity.assistant.domain.command

import com.zerogravity.assistant.domain.port.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    private val parser = CommandParser()

    @Test
    fun parsesHelpTimeDateBatteryAndTorchAliases() {
        assertEquals(ParsedCommand.Help, parser.parse("मदद"))
        assertEquals(ParsedCommand.CurrentTime, parser.parse("kitne baje hain"))
        assertEquals(ParsedCommand.CurrentDate, parser.parse("aaj ki date"))
        assertEquals(ParsedCommand.BatteryStatus, parser.parse("charge kitna"))
        assertEquals(ParsedCommand.SetTorch(true), parser.parse("टॉर्च चालू करो"))
        assertEquals(ParsedCommand.SetTorch(false), parser.parse("torch band karo"))
    }

    @Test
    fun parsesNaturalFlashlightPhrasesForVoiceInput() {
        assertEquals(ParsedCommand.SetTorch(true), parser.parse("Please turn my flashlight on"))
        assertEquals(ParsedCommand.SetTorch(true), parser.parse("switch on the torch"))
        assertEquals(ParsedCommand.SetTorch(true), parser.parse("torch chalu kar do"))
        assertEquals(ParsedCommand.SetTorch(false), parser.parse("turn the flashlight off"))
        assertEquals(ParsedCommand.SetTorch(false), parser.parse("torch bandh kar do"))
        assertEquals(ParsedCommand.Unknown, parser.parse("turn the room light on"))
    }

    @Test
    fun extractsOnlyExplicitSupportedWakePhrases() {
        assertEquals("turn on flashlight", WakePhraseParser.commandAfterWakePhrase("Hey Zero, turn on flashlight"))
        assertEquals("battery status", WakePhraseParser.commandAfterWakePhrase("OK Zero battery status"))
        assertEquals("time", WakePhraseParser.commandAfterWakePhrase("Shree! time"))
        assertEquals("", WakePhraseParser.commandAfterWakePhrase("Okay Zero"))
        assertNull(WakePhraseParser.commandAfterWakePhrase("turn on flashlight"))
        assertNull(WakePhraseParser.commandAfterWakePhrase("Shreeji turn on flashlight"))
    }

    @Test
    fun parsesSupportedAppsAcrossLanguages() {
        assertEquals(ParsedCommand.OpenApp(SupportedApp.CAMERA), parser.parse("open the camera"))
        assertEquals(ParsedCommand.OpenApp(SupportedApp.SETTINGS), parser.parse("खोलो सेटिंग्स"))
        assertEquals(ParsedCommand.OpenApp(SupportedApp.CALCULATOR), parser.parse("launch calc"))
    }

    @Test
    fun parsesEnglishAndHindiReminders() {
        assertEquals(
            ParsedCommand.CreateReminder("take medicine", 600_000L),
            parser.parse("remind me to take medicine in 10 minutes"),
        )
        assertEquals(
            ParsedCommand.CreateReminder("दवा लेने", 1_800_000L),
            parser.parse("30 मिनट बाद दवा लेने की याद दिलाना"),
        )
        assertEquals(
            ParsedCommand.CreateReminder("call home", 7_200_000L),
            parser.parse("reminder call home after 2 hrs"),
        )
        assertEquals(
            ParsedCommand.CreateReminder("दवाई", 300_000L),
            parser.parse("5 minute baad दवाई yaad dilana"),
        )
        assertEquals(ParsedCommand.OpenApp(SupportedApp.CAMERA), parser.parse("camera kholo"))
    }

    @Test
    fun doesNotMisclassifyUnsupportedOrInvalidRequests() {
        assertEquals(ParsedCommand.Unknown, parser.parse("open an unsupported app"))
        assertEquals(ParsedCommand.Unknown, parser.parse("remind me to call in 0 minutes"))
        assertEquals(ParsedCommand.Unknown, parser.parse("run arbitrary script"))
        assertEquals(ParsedCommand.Unknown, parser.parse("sometimes later"))
    }

    @Test
    fun parsesOnlyNamedShortcutsAsCommands() {
        assertEquals(
            ParsedCommand.SaveShortcut("morning", "open settings"),
            parser.parse("shortcut morning = open settings"),
        )
        assertEquals(ParsedCommand.RunShortcut("morning"), parser.parse("repeat shortcut morning"))
    }

    @Test
    fun parsesCallAndSmsAsUserConfirmedHandoffs() {
        assertEquals(
            ParsedCommand.PlaceCall("+14155550123"),
            parser.parse("call +1 (415) 555-0123"),
        )
        assertEquals(
            ParsedCommand.ComposeTextMessage("+919876543210", "Please call me"),
            parser.parse("Text to +91 98765 43210: Please call me"),
        )
        assertEquals(
            ParsedCommand.ComposeTextMessage("1234567", "Hello"),
            parser.parse("send sms to 123-4567, Hello"),
        )
    }

    @Test
    fun rejectsMalformedNumbersAndOversizedMessageCommands() {
        assertEquals(ParsedCommand.Unknown, parser.parse("call 12"))
        assertEquals(ParsedCommand.Unknown, parser.parse("text to 12345: "))
        assertEquals(ParsedCommand.Unknown, parser.parse("call 123456789012345678901"))
        assertEquals(
            ParsedCommand.Unknown,
            parser.parse("remind me to ${"x".repeat(201)} in 1 minute"),
        )
        assertEquals(
            ParsedCommand.Unknown,
            parser.parse("text 1234567: ${"x".repeat(2_001)}"),
        )
    }
}
