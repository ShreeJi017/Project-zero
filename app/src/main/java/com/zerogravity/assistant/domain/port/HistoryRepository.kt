package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    val recentEntries: Flow<List<HistoryEntry>>
}
