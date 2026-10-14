package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.AppPermission

interface PermissionManager {
    fun isGranted(permission: AppPermission): Boolean
}