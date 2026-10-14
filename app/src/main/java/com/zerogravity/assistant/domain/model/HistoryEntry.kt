package com.zerogravity.assistant.domain.model

data class HistoryEntry(
    val id: String,
    val title: String,
    val updatedAtEpochMillis: Long,
)
