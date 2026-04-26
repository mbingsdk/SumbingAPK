package com.sdkdev.sumbingcompanion.core

import android.util.Log
import com.sdkdev.sumbingcompanion.BuildConfig

object SumbingLog {
    private const val GLOBAL_TAG = "SumbingCompanion"
    
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg, tr)
        } else {
            // In production, we might want to log only critical errors 
            // without full stacktrace or sensitive info, or use Crashlytics
            Log.e(tag, "Critical Error: ${tr?.message ?: msg}")
        }
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg)
        }
    }
}
