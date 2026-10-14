package com.zerogravity.assistant.core.logging

import android.util.Log

interface AppLogger {
    fun error(event: String, throwable: Throwable)
}

class AndroidAppLogger : AppLogger {
    override fun error(event: String, throwable: Throwable) {
        Log.e(TAG, event, throwable)
    }

    private companion object {
        const val TAG = "ZeroGravity"
    }
}