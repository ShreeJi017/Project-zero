package com.zerogravity.assistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantApiEndpointTest {
    @Test
    fun normalizesHttpsBaseUrlAndBuildsChatCompletionsPath() {
        assertEquals(
            "https://api.example.com/v1",
            AssistantApiEndpoint.normalizeBaseUrl(" https://api.example.com/v1/ "),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AssistantApiEndpoint.chatCompletionsUrl("https://api.example.com/v1/"),
        )
    }

    @Test
    fun rejectsInsecureOrAmbiguousEndpoints() {
        listOf(
            "http://api.example.com/v1",
            "https:///v1",
            "https://user:password@api.example.com/v1",
            "https://api.example.com/v1?token=secret",
            "https://api.example.com/v1#fragment",
            "not a url",
        ).forEach { endpoint ->
            assertNull(AssistantApiEndpoint.normalizeBaseUrl(endpoint))
        }
    }

    @Test
    fun validatesModelAndHeaderSafeApiKeyInputs() {
        assertTrue(AssistantApiEndpoint.isValidModel("gpt-4o-mini"))
        assertFalse(AssistantApiEndpoint.isValidModel(" \n "))
        assertFalse(AssistantApiEndpoint.isValidModel("x".repeat(129)))
        assertTrue(AssistantApiEndpoint.isValidApiKey("provider-key"))
        assertFalse(AssistantApiEndpoint.isValidApiKey("key\r\nInjected: value"))
        assertFalse(AssistantApiEndpoint.isValidApiKey("key\tvalue"))
        assertFalse(AssistantApiEndpoint.isValidApiKey("x".repeat(4097)))
    }
}
