package com.zerogravity.assistant.domain.model

sealed interface ServiceResult<out T> {
    data class Success<T>(val value: T) : ServiceResult<T>

    data class Failure(
        val reason: FailureReason,
        val cause: Throwable? = null,
    ) : ServiceResult<Nothing>
}

enum class FailureReason {
    NOT_AVAILABLE,
    PERMISSION_DENIED,
    INVALID_INPUT,
    IO_ERROR,
    NO_SPEECH,
    UNSUPPORTED_LANGUAGE,
}