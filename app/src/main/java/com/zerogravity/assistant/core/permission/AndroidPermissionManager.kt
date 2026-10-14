package com.zerogravity.assistant.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.zerogravity.assistant.domain.model.AppPermission
import com.zerogravity.assistant.domain.port.PermissionManager

class AndroidPermissionManager(context: Context) : PermissionManager {
    private val appContext = context.applicationContext

    override fun isGranted(permission: AppPermission): Boolean = when (permission) {
        AppPermission.MICROPHONE -> appContext.isGranted(Manifest.permission.RECORD_AUDIO)
        AppPermission.POST_NOTIFICATIONS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.isGranted(Manifest.permission.POST_NOTIFICATIONS)
        AppPermission.CAMERA -> appContext.isGranted(Manifest.permission.CAMERA)
        AppPermission.CALENDAR_READ -> appContext.isGranted(Manifest.permission.READ_CALENDAR)
    }

    private fun Context.isGranted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}