package com.zerogravity.assistant.platform.capability

import com.zerogravity.assistant.domain.model.AssistantCapability
import com.zerogravity.assistant.domain.model.CapabilityInfo
import com.zerogravity.assistant.domain.model.CapabilityStatus
import com.zerogravity.assistant.domain.port.CapabilityProvider
import com.zerogravity.assistant.domain.port.CapabilityRegistry
import com.zerogravity.assistant.domain.port.PermissionManager

class DefaultCapabilityRegistry(
    providers: Collection<CapabilityProvider>,
    private val permissionManager: PermissionManager,
) : CapabilityRegistry {
    private val providersByCapability = providers.associateBy(CapabilityProvider::capability)

    init {
        require(providersByCapability.size == providers.size) {
            "Each assistant capability must have at most one provider"
        }
    }

    override fun all(): List<CapabilityInfo> = AssistantCapability.entries.map(::status)

    override fun status(capability: AssistantCapability): CapabilityInfo {
        val provider = providersByCapability[capability]
            ?: return CapabilityInfo(
                capability = capability,
                status = CapabilityStatus.Unavailable("No provider is installed"),
            )

        val missingPermission = provider.requiredPermissions.firstOrNull {
            !permissionManager.isGranted(it)
        }
        val status = missingPermission?.let(CapabilityStatus::PermissionRequired)
            ?: CapabilityStatus.Available

        return CapabilityInfo(capability, status)
    }
}