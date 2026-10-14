package com.zerogravity.assistant.platform.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.zerogravity.assistant.domain.model.BatteryWarningDecision
import com.zerogravity.assistant.domain.model.VoiceConfiguration
import com.zerogravity.assistant.domain.port.SettingsRepository
import com.zerogravity.assistant.domain.port.TextToSpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BatteryWarningMonitor(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val speechEngine: TextToSpeechEngine,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    evaluateBattery(intent)
                } catch (exception: Exception) {
                    android.util.Log.e(TAG, "Unable to process battery warning state", exception)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val sticky = ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) {
            scope.launch {
                try {
                    evaluateBattery(sticky)
                } catch (exception: Exception) {
                    android.util.Log.e(TAG, "Unable to read initial battery state", exception)
                }
            }
        }
    }

    private suspend fun evaluateBattery(intent: Intent) = mutex.withLock {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return@withLock
        val percent = level * 100 / scale
        val settings = settingsRepository.settings.first()
        val alreadyWarned = preferences.getBoolean(KEY_WARNING_ATTEMPTED, false)
        val decision = com.zerogravity.assistant.domain.model.BatteryWarningPolicy(
            settings.batteryWarningThresholdPercent,
        ).evaluate(percent, alreadyWarned)
        when (decision) {
            BatteryWarningDecision.None -> Unit
            BatteryWarningDecision.RearmWarning -> {
                if (!preferences.edit().putBoolean(KEY_WARNING_ATTEMPTED, false).commit()) {
                    android.util.Log.e(TAG, "Unable to persist low-battery warning re-arming.")
                }
            }
            BatteryWarningDecision.SpeakLowBatteryWarning -> {
                if (!settings.batteryWarningsEnabled) return@withLock
                if (!preferences.edit().putBoolean(KEY_WARNING_ATTEMPTED, true).commit()) {
                    android.util.Log.e(TAG, "Unable to persist low-battery warning attempt; speech was skipped.")
                    return@withLock
                }
                val configuration = VoiceConfiguration(
                    languageTag = settings.voiceLanguageTag,
                    voiceName = settings.voiceName,
                    speechRate = settings.speechRate,
                )
                when (val configured = speechEngine.configure(configuration)) {
                    is com.zerogravity.assistant.domain.model.ServiceResult.Failure -> {
                        android.util.Log.w(TAG, "Low-battery voice is not available: ${configured.reason}")
                    }
                    is com.zerogravity.assistant.domain.model.ServiceResult.Success -> {
                        val message = if (settings.voiceLanguageTag.startsWith("hi")) {
                            "बैटरी ${settings.batteryWarningThresholdPercent} प्रतिशत या उससे कम है।"
                        } else {
                            "Battery is at or below ${settings.batteryWarningThresholdPercent} percent."
                        }
                        when (val spoken = speechEngine.speak(message)) {
                            is com.zerogravity.assistant.domain.model.ServiceResult.Failure ->
                                android.util.Log.w(TAG, "Low-battery warning could not be spoken: ${spoken.reason}")
                            is com.zerogravity.assistant.domain.model.ServiceResult.Success -> Unit
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "battery_warning_state"
        const val KEY_WARNING_ATTEMPTED = "warning_attempted_this_cycle"
        const val TAG = "ZeroGravity"
    }
}
