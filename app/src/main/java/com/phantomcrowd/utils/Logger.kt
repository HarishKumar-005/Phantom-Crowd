package com.phantomcrowd.utils

import android.util.Log

object Logger {
    private const val TAG = "PhantomCrowd"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
