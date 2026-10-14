package com.zerogravity.assistant.domain.model

data class AssistantApiConfiguration(
    val endpoint: String = DEFAULT_ENDPOINT,
    val model: String = "",
    val hasApiKey: Boolean = false,
) {
    val isConfigured: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank() && hasApiKey

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
    }
}
