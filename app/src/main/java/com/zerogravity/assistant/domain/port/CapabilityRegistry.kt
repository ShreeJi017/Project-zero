package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.AppPermission
import com.zerogravity.assistant.domain.model.AssistantCapability
import com.zerogravity.assistant.domain.model.CapabilityInfo

interface CapabilityProvider {
    val capability: AssistantCapability
    val requiredPermissions: Set<AppPermission>
}

interface CapabilityRegistry {
    fun all(): List<CapabilityInfo>

    fun status(capability: AssistantCapability): CapabilityInfo
}