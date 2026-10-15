package com.zerogravity.assistant.domain.command

/** Extracts a command only after one of the explicit voice wake phrases. */
object WakePhraseParser {
    private val WAKE_PHRASE_PREFIX = Regex(
        "^(?:hey\\s+zero|ok(?:ay)?\\s+zero|shree)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun commandAfterWakePhrase(recognizedText: String): String? {
        val text = recognizedText.trim()
        val match = WAKE_PHRASE_PREFIX.find(text) ?: return null
        return text.substring(match.range.last + 1)
            .trim()
            .trimStart(',', '.', '!', '?', ';', ':', '-', '—')
            .trim()
    }
}
