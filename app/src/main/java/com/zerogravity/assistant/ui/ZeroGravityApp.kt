package com.zerogravity.assistant.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zerogravity.assistant.core.di.AppContainer
import com.zerogravity.assistant.domain.model.AssistantSettings
import com.zerogravity.assistant.domain.command.CommandParser
import com.zerogravity.assistant.domain.command.ParsedCommand
import com.zerogravity.assistant.domain.model.AppPermission
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.ui.assistant.AssistantScreen
import com.zerogravity.assistant.ui.history.HistoryScreen
import com.zerogravity.assistant.ui.settings.PersonalisationScreen
import com.zerogravity.assistant.ui.settings.SettingsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class Destination(val route: String, val label: String, val mark: String) {
    HOME("home", "Home", "◉"),
    SETTINGS("settings", "Settings", "⌘"),
    PERSONALISATION("personalisation", "Personalisation", "✳"),
    HISTORY("history", "History", "◷"),
}

@Composable
fun ZeroGravityApp(container: AppContainer) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val assistantViewModel: AssistantViewModel = viewModel(
        factory = AssistantViewModelFactory(container),
    )
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        assistantViewModel.refreshCapabilities()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        assistantViewModel.stopVoice()
    }
    val uiState = assistantViewModel.uiState.collectAsStateWithLifecycle().value
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute != Destination.HOME.route) assistantViewModel.stopVoice()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingContinuousVoice by remember { androidx.compose.runtime.mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        assistantViewModel.refreshCapabilities()
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "Permission granted. Submit the reminder command again."
                else "Permission denied. The reminder was not scheduled.",
            )
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "Permission granted. Submit the torch command again."
                else "Permission denied. The torch was not changed.",
            )
        }
    }
    fun reportCommandResult(command: String, result: ServiceResult<String>) {
        if (result is ServiceResult.Failure && result.reason == FailureReason.PERMISSION_DENIED) {
            when (CommandParser().parse(command)) {
                is ParsedCommand.CreateReminder -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Reminder permission denied; it was not scheduled.") }
                    }
                }
                is ParsedCommand.SetTorch ->
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                else -> scope.launch {
                    snackbarHostState.showSnackbar("Permission denied. The requested action was not completed.")
                }
            }
            return
        }
        val message = when (result) {
            is ServiceResult.Success -> result.value
            is ServiceResult.Failure -> when (result.reason) {
                FailureReason.PERMISSION_DENIED -> "Permission denied. The action was not completed."
                FailureReason.INVALID_INPUT -> "That command could not be completed because its input is invalid."
                FailureReason.NOT_AVAILABLE -> "That feature or app is not available on this device."
                FailureReason.IO_ERROR -> "The device could not complete that action."
                FailureReason.NO_SPEECH -> "No speech was recognized."
                FailureReason.UNSUPPORTED_LANGUAGE -> "That speech language is not installed for offline recognition."
            }
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        assistantViewModel.refreshCapabilities()
        if (granted) {
            assistantViewModel.startListening(pendingContinuousVoice, ::reportCommandResult)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission denied. Voice input was not started.") }
        }
    }

    fun submitCommand(command: String) {
        val permission = when (CommandParser().parse(command)) {
            is ParsedCommand.CreateReminder -> AppPermission.POST_NOTIFICATIONS
            is ParsedCommand.SetTorch -> AppPermission.CAMERA
            else -> null
        }
        if (permission != null && !container.permissionManager.isGranted(permission)) {
            when (permission) {
                AppPermission.POST_NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Notification permission is unavailable.") }
                    }
                }
                AppPermission.CAMERA -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                AppPermission.MICROPHONE, AppPermission.CALENDAR_READ -> Unit
            }
            return
        }
        assistantViewModel.submitText(command) { result ->
            reportCommandResult(command, result)
        }
    }

    fun startListening(continuous: Boolean) {
        if (!container.permissionManager.isGranted(AppPermission.MICROPHONE)) {
            pendingContinuousVoice = continuous
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        assistantViewModel.startListening(continuous, ::reportCommandResult)
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navigateTo(destination.route) },
                        icon = { Text(destination.mark, style = MaterialTheme.typography.titleMedium) },
                        label = { Text(destination.label) },
                        modifier = Modifier.semantics {
                            contentDescription = "${destination.label} navigation"
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(contentPadding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Destination.HOME.route) {
                AssistantScreen(
                    state = uiState,
                    onNavigate = ::navigateTo,
                    onCommand = ::submitCommand,
                    onNewChat = { assistantViewModel.newConversation(); scope.launch { snackbarHostState.showSnackbar("Started a new conversation.") } },
                    onStartListening = ::startListening,
                    onStopListening = assistantViewModel::stopVoice,
                    onNotice = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    settings = uiState.settings,
                    capabilities = uiState.capabilities,
                    loadingError = uiState.settingsError,
                    onSwipeLeft = { navigateTo(Destination.PERSONALISATION.route) },
                    onSwipeRight = { navigateTo(Destination.HOME.route) },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    installedVoices = uiState.installedVoices,
                    apiConfiguration = uiState.apiConfiguration,
                    onSaveApiConfiguration = { endpoint, model, apiKey ->
                        val result = assistantViewModel.saveApiConfiguration(endpoint, model, apiKey)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                when (result) {
                                    is ServiceResult.Success ->
                                        if (apiKey.isBlank() && !uiState.apiConfiguration.hasApiKey) {
                                            "Provider saved. Add an API key to enable online chat."
                                        } else {
                                            "Provider saved. The API key is encrypted on this device."
                                        }
                                    is ServiceResult.Failure -> {
                                        if (result.cause != null) {
                                            container.appLogger.error(
                                                "Unable to save AI provider configuration",
                                                result.cause,
                                            )
                                        }
                                        when (result.reason) {
                                            FailureReason.INVALID_INPUT ->
                                                "Use a valid HTTPS base URL, model name, and API key."
                                            else -> "Provider settings could not be saved."
                                        }
                                    }
                                },
                            )
                        }
                        result
                    },
                    onClearApiConfiguration = {
                        val result = assistantViewModel.clearApiConfiguration()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (result is ServiceResult.Success) {
                                    "Provider settings and saved key removed."
                                } else {
                                    "Provider settings could not be removed."
                                },
                            )
                        }
                        result
                    },
                    onInstallVoice = { languageTag ->
                        scope.launch {
                            when (val result = container.voicePackManager.requestOfflineVoice(languageTag)) {
                                is ServiceResult.Success -> snackbarHostState.showSnackbar(
                                    "Android voice-data settings opened. After installing an offline voice, return and check again.",
                                )
                                is ServiceResult.Failure -> snackbarHostState.showSnackbar(
                                    "Could not open the system voice installer: ${result.reason}.",
                                )
                            }
                        }
                    },
                    onRefreshVoices = {
                        scope.launch {
                            when (val refreshed = container.voicePackManager.refreshInstalledVoices()) {
                                is ServiceResult.Success -> snackbarHostState.showSnackbar(
                                    if (refreshed.value.any {
                                            !it.requiresNetwork &&
                                                it.languageTag.substringBefore('-')
                                                    .equals(
                                                        uiState.settings?.voiceLanguageTag
                                                            ?.substringBefore('-'),
                                                        ignoreCase = true,
                                                    )
                                        }
                                    ) {
                                        "Installed voice list refreshed. Select an offline voice above."
                                    } else {
                                        "No offline voices are installed in the system TTS engine."
                                    },
                                )
                                is ServiceResult.Failure -> snackbarHostState.showSnackbar(
                                    "Could not inspect system voices: ${refreshed.reason}.",
                                )
                            }
                        }
                    },
                    onTestVoice = {
                        scope.launch {
                            when (val result = assistantViewModel.testOfflineVoice()) {
                                is ServiceResult.Success -> snackbarHostState.showSnackbar(
                                    "Offline voice test completed. If you heard nothing, check media volume and Android text-to-speech settings.",
                                )
                                is ServiceResult.Failure -> snackbarHostState.showSnackbar(
                                    when (result.reason) {
                                        FailureReason.NOT_AVAILABLE, FailureReason.UNSUPPORTED_LANGUAGE ->
                                            "No compatible offline voice is ready. Install or select an offline voice in Settings."
                                        else -> "Offline voice test failed: ${result.reason}."
                                    },
                                )
                            }
                        }
                    },
                    onOpenMicrophoneSettings = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                },
                            )
                        } catch (exception: ActivityNotFoundException) {
                            container.appLogger.error("Unable to open Android microphone settings", exception)
                            scope.launch {
                                snackbarHostState.showSnackbar("Android app settings could not be opened.")
                            }
                        }
                    },
                    onSettingsChange = { update ->
                        saveSettings(update, assistantViewModel, snackbarHostState)
                    },
                )
            }
            composable(Destination.PERSONALISATION.route) {
                PersonalisationScreen(
                    settings = uiState.settings,
                    loadingError = uiState.settingsError,
                    onSwipeLeft = { navigateTo(Destination.HISTORY.route) },
                    onSwipeRight = { navigateTo(Destination.SETTINGS.route) },
                    onSettingsChange = { update ->
                        saveSettings(update, assistantViewModel, snackbarHostState)
                    },
                )
            }
            composable(Destination.HISTORY.route) {
                HistoryScreen(
                    repository = container.historyRepository,
                    onOpenConversation = { conversationId ->
                        scope.launch {
                            when (val opened = assistantViewModel.openConversation(conversationId)) {
                                is ServiceResult.Success -> navigateTo(Destination.HOME.route)
                                is ServiceResult.Failure -> snackbarHostState.showSnackbar(
                                    if (opened.reason == FailureReason.NOT_AVAILABLE) "That conversation is no longer available." else "The conversation could not be opened.",
                                )
                            }
                        }
                    },
                    onClearHistory = {
                        scope.launch {
                            when (assistantViewModel.clearHistory()) {
                                is ServiceResult.Success -> snackbarHostState.showSnackbar("Local conversation history cleared.")
                                is ServiceResult.Failure -> snackbarHostState.showSnackbar("Local history could not be cleared.")
                            }
                        }
                    },
                    onSwipeLeft = { navigateTo(Destination.HOME.route) },
                    onSwipeRight = { navigateTo(Destination.PERSONALISATION.route) },
                )
            }
        }
    }
}

private suspend fun saveSettings(
    update: AssistantSettings.() -> AssistantSettings,
    viewModel: AssistantViewModel,
    snackbarHostState: SnackbarHostState,
) {
    try {
        viewModel.updateSettings(update)
        snackbarHostState.showSnackbar("Preference saved on this device")
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        viewModel.reportSettingsError(exception)
        snackbarHostState.showSnackbar("Could not save this preference")
    }
}
