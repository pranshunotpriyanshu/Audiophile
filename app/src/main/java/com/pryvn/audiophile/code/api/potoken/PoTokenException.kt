package com.pryvn.audiophile.code.api.potoken

import android.util.Log

/**
 * Thrown when BotGuard PoToken generation fails in a recoverable way
 * (e.g. timeout, network error). Callers should fall back to unauthenticated playback.
 */
class PoTokenException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when the system WebView is fundamentally broken (e.g. throws JavaScript SyntaxErrors).
 * Once this is thrown the generator marks itself as permanently unavailable for the process lifetime.
 */
class BrokenWebViewException(
    message: String,
) : Exception(message)

/**
 * Classifies a JavaScript error string into the appropriate exception type.
 */
fun classifyJsError(error: String): Exception =
    if (error.contains("SyntaxError")) {
        BrokenWebViewException(error)
    } else {
        PoTokenException(error)
    }

/**
 * Simple logging wrapper for PoToken operations.
 */
object PoTokenLog {
    private const val TAG = "PoTokenGen"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}