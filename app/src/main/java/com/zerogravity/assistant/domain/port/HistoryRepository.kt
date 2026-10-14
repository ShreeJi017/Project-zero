package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.ChatMessage
import com.zerogravity.assistant.domain.model.HistoryEntry
import com.zerogravity.assistant.domain.model.ServiceResult
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    val recentEntries: Flow<List<HistoryEntry>>
    /** Searches titles and every saved message body; an empty query returns all conversations. */
    fun search(query: String): Flow<List<HistoryEntry>>
    suspend fun messages(conversationId: String): List<ChatMessage>
    suspend fun saveMessage(conversationId: String, title: String, message: ChatMessage): ServiceResult<Unit>
    suspend fun clear(): ServiceResult<Unit>
}
