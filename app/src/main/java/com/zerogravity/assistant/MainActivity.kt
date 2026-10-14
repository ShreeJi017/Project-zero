package com.zerogravity.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.zerogravity.assistant.domain.model.AppearanceMode
import com.zerogravity.assistant.ui.theme.ZeroGravityTheme
import com.zerogravity.assistant.ui.ZeroGravityApp
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ZeroGravityApplication).container

        setContent {
            val appearanceFlow = remember(container.settingsRepository) {
                container.settingsRepository.settings.map { it.appearance }
            }
            val appearance by appearanceFlow
                .collectAsState(initial = AppearanceMode.SYSTEM)
            val darkTheme = when (appearance) {
                AppearanceMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars = !darkTheme
            }
            ZeroGravityTheme(darkTheme = darkTheme) {
                ZeroGravityApp(container)
            }
        }
    }
}