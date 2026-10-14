package com.zerogravity.assistant.domain.model

data class LocalShortcut(
    val name: String,
    val command: String,
)

data class WeatherObservation(
    val summary: String,
    val city: String,
    val source: String,
    val retrievedAtEpochMillis: Long,
)

data class CalendarEvent(
    val title: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
)
