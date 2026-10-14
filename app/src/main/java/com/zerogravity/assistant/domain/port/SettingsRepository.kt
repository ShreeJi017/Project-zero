package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.AssistantSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AssistantSettings>

    suspend fun update(transform: AssistantSettings.() -> AssistantSettings)
}