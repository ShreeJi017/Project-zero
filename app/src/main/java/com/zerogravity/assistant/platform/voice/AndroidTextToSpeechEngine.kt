package com.zerogravity.assistant.platform.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.model.VoiceConfiguration
import com.zerogravity.assistant.domain.model.VoiceDescriptor
import com.zerogravity.assistant.domain.port.TextToSpeechEngine
import com.zerogravity.assistant.domain.port.VoicePackManager
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidTextToSpeechEngine(context: Context) : TextToSpeechEngine, VoicePackManager {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val ready = CompletableDeferred<Int>()
    private val mutableInstalledVoices = MutableStateFlow<List<VoiceDescriptor>>(emptyList())
    override val installedVoices = mutableInstalledVoices.asStateFlow()
    private val speechMutex = Mutex()
    private var engine: TextToSpeech? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var configuredOfflineVoice = false
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            engine?.stop()
        }
    }
    private var activeUtteranceId: String? = null

    init {
        Handler(appContext.mainLooper).post {
            engine = TextToSpeech(appContext) { status ->
                if (!ready.isCompleted) ready.complete(status)
                if (status == TextToSpeech.SUCCESS) refreshVoices()
            }
        }
    }

    override suspend fun configure(configuration: VoiceConfiguration): ServiceResult<Unit> {
        if (!awaitInitialized()) return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        return withContext(Dispatchers.Main.immediate) {
            val tts = engine ?: return@withContext ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
            val requestedLanguage = Locale.forLanguageTag(configuration.languageTag).language
            val languageVoices = tts.voices.orEmpty().filter {
                it.locale.language.equals(requestedLanguage, ignoreCase = true) &&
                    !it.isNetworkConnectionRequired
            }
            val selected = if (configuration.voiceName.isNotBlank()) {
                languageVoices.firstOrNull { it.name == configuration.voiceName }
            } else {
                languageVoices.firstOrNull()
            }
                ?: return@withContext ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
            if (tts.setVoice(selected) != TextToSpeech.SUCCESS) {
                return@withContext ServiceResult.Failure(FailureReason.UNSUPPORTED_LANGUAGE)
            }
            val rate = configuration.speechRate.coerceIn(MIN_RATE, MAX_RATE)
            if (tts.setSpeechRate(rate) != TextToSpeech.SUCCESS) {
                return@withContext ServiceResult.Failure(FailureReason.INVALID_INPUT)
            }
            configuredOfflineVoice = true
            ServiceResult.Success(Unit)
        }
    }

    override suspend fun speak(text: String): ServiceResult<Unit> {
        if (text.isBlank()) return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        if (!awaitInitialized()) return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        if (!configuredOfflineVoice) return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        return speechMutex.withLock {
            if (isCallActive() || isDoNotDisturbActive()) {
                return@withLock ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
            }
            if (!requestAudioFocus()) {
                return@withLock ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
            }
            try {
                withContext(Dispatchers.Main.immediate) {
                    val tts = engine
                        ?: return@withContext ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
                    val id = UUID.randomUUID().toString()
                    activeUtteranceId = id
                    val outcome = suspendCancellableCoroutine<ServiceResult<Unit>> { continuation ->
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == id && continuation.isActive) {
                                    continuation.resume(ServiceResult.Success(Unit))
                                }
                            }

                            @Deprecated("Deprecated by Android; onError(String, int) is used on API 21+.")
                            override fun onError(utteranceId: String?) {
                                if (utteranceId == id && continuation.isActive) {
                                    continuation.resume(ServiceResult.Failure(FailureReason.IO_ERROR))
                                }
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                if (utteranceId == id && continuation.isActive) {
                                    continuation.resume(ServiceResult.Failure(FailureReason.IO_ERROR))
                                }
                            }

                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                if (utteranceId == id && continuation.isActive) {
                                    continuation.resume(ServiceResult.Failure(FailureReason.NOT_AVAILABLE))
                                }
                            }
                        })
                        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                            continuation.resume(ServiceResult.Failure(FailureReason.IO_ERROR))
                        }
                        continuation.invokeOnCancellation { tts.stop() }
                    }
                    if (activeUtteranceId == id) activeUtteranceId = null
                    outcome
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                ServiceResult.Failure(FailureReason.IO_ERROR, exception)
            } finally {
                abandonAudioFocus()
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main.immediate) {
            engine?.stop()
            activeUtteranceId = null
        }
    }

    override suspend fun refreshInstalledVoices(): ServiceResult<List<VoiceDescriptor>> {
        if (!awaitInitialized()) {
            return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        }
        withContext(Dispatchers.Main.immediate) { refreshVoices() }
        return ServiceResult.Success(mutableInstalledVoices.value)
    }

    override suspend fun requestOfflineVoice(languageTag: String): ServiceResult<Unit> {
        if (languageTag !in SUPPORTED_VOICE_LANGUAGES) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        if (!awaitInitialized()) return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        return withContext(Dispatchers.Main.immediate) {
            try {
                appContext.startActivity(
                    Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                ServiceResult.Success(Unit)
            } catch (exception: ActivityNotFoundException) {
                ServiceResult.Failure(FailureReason.NOT_AVAILABLE, exception)
            } catch (exception: SecurityException) {
                ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception)
            }
        }
    }

    suspend fun shutdown() {
        withContext(Dispatchers.Main.immediate) {
            engine?.stop()
            engine?.shutdown()
            engine = null
        }
    }

    private suspend fun awaitInitialized(): Boolean = try {
        withTimeout(INIT_TIMEOUT_MILLIS) { ready.await() == TextToSpeech.SUCCESS }
    } catch (exception: TimeoutCancellationException) {
        false
    }

    private fun refreshVoices() {
        val available = engine?.voices.orEmpty()
            .map { voice ->
                VoiceDescriptor(
                    id = voice.name,
                    languageTag = voice.locale.toLanguageTag(),
                    displayName = "${voice.locale.getDisplayLanguage(Locale.getDefault())} · ${voice.name}",
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    quality = voice.quality,
                )
            }
            .sortedWith(compareBy<VoiceDescriptor> { it.languageTag }.thenBy { it.displayName })
        mutableInstalledVoices.value = available
    }

    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun isCallActive(): Boolean =
        audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

    private fun isDoNotDisturbActive(): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            false
        } else if (!notificationManager.isNotificationPolicyAccessGranted) {
            false
        } else {
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        }
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
        const val INIT_TIMEOUT_MILLIS = 10_000L
        val SUPPORTED_VOICE_LANGUAGES = setOf("en-IN", "hi-IN")
    }
}
