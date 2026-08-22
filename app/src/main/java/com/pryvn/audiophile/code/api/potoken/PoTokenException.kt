package com.pryvn.audiophile.code.api.potoken

import android.util.Log

class PoTokenException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class BrokenWebViewException(
    message: String,
) : Exception(message)

fun classifyJsError(error: String): Exception =
    if (error.contains("SyntaxError")) {
        BrokenWebViewException(error)
    } else {
        PoTokenException(error)
    }

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
