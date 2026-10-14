package com.zerogravity.assistant.platform.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.model.SpeechInputState
import com.zerogravity.assistant.domain.model.SpeechInputTransitions
import com.zerogravity.assistant.domain.port.PermissionManager
import com.zerogravity.assistant.domain.port.SpeechToTextEngine
import com.zerogravity.assistant.domain.model.AppPermission
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class AndroidOnDeviceSpeechRecognizer(
    context: Context,
    private val permissions: PermissionManager,
) : SpeechToTextEngine {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow<SpeechInputState>(SpeechInputState.Idle)
    override val state: StateFlow<SpeechInputState> = mutableState.asStateFlow()
    private val sessionMutex = Mutex()
    private var recognizer: SpeechRecognizer? = null
    private var activeContinuation: kotlinx.coroutines.CancellableContinuation<ServiceResult<String>>? = null

    override suspend fun recognizeOnce(languageTag: String): ServiceResult<String> {
        if (languageTag !in SUPPORTED_LANGUAGES) {
            mutableState.value = SpeechInputState.Error(FailureReason.UNSUPPORTED_LANGUAGE)
            return ServiceResult.Failure(FailureReason.UNSUPPORTED_LANGUAGE)
        }
        if (!permissions.isGranted(AppPermission.MICROPHONE)) {
            mutableState.value = SpeechInputState.PermissionDenied
            return ServiceResult.Failure(FailureReason.PERMISSION_DENIED)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            val failure = ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
            mutableState.value = SpeechInputState.Error(failure.reason)
            return failure
        }
        if (!sessionMutex.tryLock()) {
            mutableState.value = SpeechInputState.Error(FailureReason.IO_ERROR)
            return ServiceResult.Failure(FailureReason.IO_ERROR)
        }

        return try {
            withTimeout(RECOGNITION_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    activeContinuation = continuation
                    continuation.invokeOnCancellation {
                        mutableState.value = SpeechInputState.Idle
                        android.os.Handler(appContext.mainLooper).post {
                            recognizer?.cancel()
                        }
                    }
                    android.os.Handler(appContext.mainLooper).post {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            startRecognition(languageTag, continuation)
                        } else {
                            val failure = ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
                            mutableState.value = SpeechInputState.Error(failure.reason)
                            if (continuation.isActive) continuation.resume(failure)
                        }
                    }
                }
            }
        } catch (exception: TimeoutCancellationException) {
            mutableState.value = SpeechInputState.Error(FailureReason.IO_ERROR)
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        } catch (exception: CancellationException) {
            throw exception
        } finally {
            activeContinuation = null
            sessionMutex.unlock()
        }
    }

    override suspend fun cancel() {
        mutableState.value = SpeechInputState.Idle
        withContext(Dispatchers.Main.immediate) {
            activeContinuation?.cancel(CancellationException("Voice input cancelled"))
            recognizer?.cancel()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startRecognition(
        languageTag: String,
        continuation: kotlinx.coroutines.CancellableContinuation<ServiceResult<String>>,
    ) {
        if (!continuation.isActive) return
        try {
            val engine = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                .also { recognizer = it }
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    mutableState.value = SpeechInputState.Recording
                }

                override fun onBeginningOfSpeech() {
                    mutableState.value = SpeechInputState.Recording
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    mutableState.value = SpeechInputState.Processing
                }

                override fun onError(error: Int) {
                    if (!continuation.isActive) return
                    val reason = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> FailureReason.NO_SPEECH
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> FailureReason.PERMISSION_DENIED
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> FailureReason.UNSUPPORTED_LANGUAGE
                        else -> FailureReason.IO_ERROR
                    }
                    mutableState.value = SpeechInputTransitions.failed(reason)
                    if (continuation.isActive) continuation.resume(ServiceResult.Failure(reason))
                }

                override fun onResults(results: Bundle?) {
                    if (!continuation.isActive) return
                    mutableState.value = SpeechInputState.Processing
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                    when (val inputState = SpeechInputTransitions.result(text)) {
                        SpeechInputState.NoSpeech -> {
                            mutableState.value = inputState
                            if (continuation.isActive) {
                                continuation.resume(ServiceResult.Failure(FailureReason.NO_SPEECH))
                            }
                        }
                        is SpeechInputState.Recognized -> {
                            mutableState.value = inputState
                            if (continuation.isActive) {
                                continuation.resume(ServiceResult.Success(inputState.text))
                            }
                        }
                        else -> error("Recognition result transition returned an invalid state")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(languageTag).toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            mutableState.value = SpeechInputState.Recording
            engine.startListening(intent)
        } catch (exception: SecurityException) {
            mutableState.value = SpeechInputState.PermissionDenied
            if (continuation.isActive) {
                continuation.resume(ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception))
            }
        } catch (exception: UnsupportedOperationException) {
            mutableState.value = SpeechInputState.Error(FailureReason.NOT_AVAILABLE)
            if (continuation.isActive) {
                continuation.resume(ServiceResult.Failure(FailureReason.NOT_AVAILABLE, exception))
            }
        } catch (exception: RuntimeException) {
            mutableState.value = SpeechInputState.Error(FailureReason.IO_ERROR)
            if (continuation.isActive) {
                continuation.resume(ServiceResult.Failure(FailureReason.IO_ERROR, exception))
            }
        }
    }

    private companion object {
        const val RECOGNITION_TIMEOUT_MILLIS = 60_000L
        val SUPPORTED_LANGUAGES = setOf("en-IN", "hi-IN")
    }
}
