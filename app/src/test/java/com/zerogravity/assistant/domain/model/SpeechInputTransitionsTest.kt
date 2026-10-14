package com.zerogravity.assistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechInputTransitionsTest {
    @Test
    fun noSpeechAndPermissionFailuresHaveDistinctStates() {
        assertEquals(
            SpeechInputState.NoSpeech,
            SpeechInputTransitions.failed(FailureReason.NO_SPEECH),
        )
        assertEquals(
            SpeechInputState.PermissionDenied,
            SpeechInputTransitions.failed(FailureReason.PERMISSION_DENIED),
        )
        assertEquals(
            SpeechInputState.Error(FailureReason.IO_ERROR),
            SpeechInputTransitions.failed(FailureReason.IO_ERROR),
        )
    }

    @Test
    fun emptyRecognitionIsNoSpeechAndMixedLanguageTextIsPreserved() {
        assertEquals(SpeechInputState.NoSpeech, SpeechInputTransitions.result(null))
        assertEquals(SpeechInputState.NoSpeech, SpeechInputTransitions.result("  "))
        assertEquals(
            SpeechInputState.Recognized("kal meeting open settings"),
            SpeechInputTransitions.result("kal meeting open settings"),
        )
    }
}
