package com.sdkdev.sumbingcompanion.core

import android.graphics.Rect

data class CaptureRegion(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    val isEmpty: Boolean get() = w <= 0 || h <= 0
    val rect: Rect get() = Rect(x, y, x + w, y + h)

    companion object {
        val DEFAULT = CaptureRegion(0, 0, 0, 0)
    }
}
