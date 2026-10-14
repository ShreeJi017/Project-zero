package com.zerogravity.assistant

import android.app.Application
import com.zerogravity.assistant.core.di.AppContainer
import com.zerogravity.assistant.platform.voice.BatteryWarningMonitor

class ZeroGravityApplication : Application() {
    lateinit var container: AppContainer
        private set
    private lateinit var batteryWarningMonitor: BatteryWarningMonitor

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        batteryWarningMonitor = BatteryWarningMonitor(
            this,
            container.settingsRepository,
            container.textToSpeechEngine,
        )
        batteryWarningMonitor.start()
    }
}