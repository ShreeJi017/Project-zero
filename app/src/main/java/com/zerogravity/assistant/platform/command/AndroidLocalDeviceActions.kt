package com.zerogravity.assistant.platform.command

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.Settings
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.LocalDeviceActions
import com.zerogravity.assistant.domain.port.SupportedApp

class AndroidLocalDeviceActions(context: Context) : LocalDeviceActions {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)

    override suspend fun openApp(app: SupportedApp): ServiceResult<String> {
        val intent = if (app == SupportedApp.SETTINGS) {
            Intent(Settings.ACTION_SETTINGS)
        } else {
            val packageNames = when (app) {
                SupportedApp.CAMERA -> listOf(
                    app.packageName,
                    "com.android.camera",
                    "com.google.android.GoogleCamera",
                )
                SupportedApp.CLOCK -> listOf(app.packageName, "com.android.deskclock")
                SupportedApp.CALCULATOR -> listOf(app.packageName, "com.android.calculator2")
                else -> listOf(app.packageName)
            }
            packageNames.firstNotNullOfOrNull { packageName ->
                appContext.packageManager.getLaunchIntentForPackage(packageName)
                    ?.takeIf { it.resolveActivity(appContext.packageManager) != null }
            }
                ?: return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ServiceResult.Success("Opened ${app.name.lowercase().replaceFirstChar(Char::uppercase)}.")
        } catch (exception: SecurityException) {
            ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception)
        } catch (exception: android.content.ActivityNotFoundException) {
            ServiceResult.Failure(FailureReason.NOT_AVAILABLE, exception)
        }
    }

    override suspend fun setTorch(enabled: Boolean): ServiceResult<String> {
        val cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        } catch (exception: CameraAccessException) {
            return ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        } catch (exception: SecurityException) {
            return ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception)
        }
        return try {
            cameraManager.setTorchMode(cameraId, enabled)
            ServiceResult.Success(if (enabled) "Torch turned on." else "Torch turned off.")
        } catch (exception: CameraAccessException) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        } catch (exception: SecurityException) {
            ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception)
        } catch (exception: IllegalArgumentException) {
            ServiceResult.Failure(FailureReason.NOT_AVAILABLE, exception)
        }
    }

    override suspend fun batteryStatus(): ServiceResult<String> {
        val battery = appContext.registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return ServiceResult.Failure(FailureReason.IO_ERROR)
        val percent = (level * 100 / scale)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return ServiceResult.Success("$percent%${if (charging) " · charging" else ""}")
    }
}
