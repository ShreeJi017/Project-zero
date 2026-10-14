package com.zerogravity.assistant.platform.capability

import com.zerogravity.assistant.domain.model.AppPermission
import com.zerogravity.assistant.domain.model.AssistantCapability
import com.zerogravity.assistant.domain.model.CapabilityStatus
import com.zerogravity.assistant.domain.port.CapabilityProvider
import com.zerogravity.assistant.domain.port.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCapabilityRegistryTest {
    @Test
    fun missingProviderIsUnavailable() {
        val registry = DefaultCapabilityRegistry(emptyList(), permissionsGranted(true))

        assertEquals(
            CapabilityStatus.Unavailable("No provider is installed"),
            registry.status(AssistantCapability.LOCAL_COMMANDS).status,
        )
    }

    @Test
    fun providerWithDeniedPermissionIsNotAvailable() {
        val registry = registryWithMicrophoneProvider(permissionsGranted(false))

        assertEquals(
            CapabilityStatus.PermissionRequired(AppPermission.MICROPHONE),
            registry.status(AssistantCapability.SPEECH_TO_TEXT).status,
        )
    }

    @Test
    fun providerWithGrantedPermissionsIsAvailable() {
        val registry = registryWithMicrophoneProvider(permissionsGranted(true))

        assertEquals(
            CapabilityStatus.Available,
            registry.status(AssistantCapability.SPEECH_TO_TEXT).status,
        )
    }

    private fun registryWithMicrophoneProvider(permissionManager: PermissionManager) =
        DefaultCapabilityRegistry(
            providers = listOf(
                object : CapabilityProvider {
                    override val capability = AssistantCapability.SPEECH_TO_TEXT
                    override val requiredPermissions = setOf(AppPermission.MICROPHONE)
                },
            ),
            permissionManager = permissionManager,
        )

    private fun permissionsGranted(granted: Boolean) = object : PermissionManager {
        override fun isGranted(permission: AppPermission): Boolean = granted
    }
}