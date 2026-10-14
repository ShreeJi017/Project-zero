package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.AssistantApiConfiguration
import com.zerogravity.assistant.domain.model.ChatMessage
import com.zerogravity.assistant.domain.model.ServiceResult
import kotlinx.coroutines.flow.Flow

interface AssistantApiConfigurationRepository {
    val configuration: Flow<AssistantApiConfiguration>

    suspend fun save(
        endpoint: String,
        model: String,
        apiKey: String,
    ): ServiceResult<Unit>

    suspend fun clear(): ServiceResult<Unit>

    suspend fun readApiKey(): ServiceResult<String>
}

interface AssistantChatService {
    suspend fun respond(messages: List<ChatMessage>): ServiceResult<String>
}
