package com.zerogravity.assistant.domain.model

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
)

enum class ChatRole {
    USER,
    ASSISTANT,
}
