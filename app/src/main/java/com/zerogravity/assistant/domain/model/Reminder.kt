package com.zerogravity.assistant.domain.model

data class Reminder(
    val id: String,
    val title: String,
    val scheduledAtEpochMillis: Long,
) {
    companion object {
        const val MAX_TITLE_LENGTH = 200
    }
}