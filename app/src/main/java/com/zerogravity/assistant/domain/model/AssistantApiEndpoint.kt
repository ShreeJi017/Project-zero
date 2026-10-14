package com.zerogravity.assistant.domain.model

import java.net.URI
import java.net.URISyntaxException

object AssistantApiEndpoint {
    fun normalizeBaseUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null

        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null ||
            uri.rawQuery != null ||
            uri.rawFragment != null
        ) {
            return null
        }
        return uri.normalize().toASCIIString().trimEnd('/')
    }

    fun chatCompletionsUrl(baseUrl: String): String? =
        normalizeBaseUrl(baseUrl)?.let { "$it/chat/completions" }

    fun isValidModel(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_MODEL_LENGTH &&
            value.none { it.isISOControl() }

    fun isValidApiKey(value: String): Boolean =
        value.length <= MAX_API_KEY_LENGTH &&
            value.none { it.isISOControl() }

    const val MAX_URL_LENGTH = 512
    const val MAX_MODEL_LENGTH = 128
    const val MAX_API_KEY_LENGTH = 4096
}
