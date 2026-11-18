package io.nekohasekai.sagernet.ktx

import android.view.MotionEvent
import android.view.View

/**
 * Press-to-shrink feedback for TabLayout tabs (v1.4.32).
 *
 * Replaces the Material tab ripple, which washed a big square over the
 * whole tab rect while the selection pill is a small rounded rect.
 * Shrinks the pressed tab to [scale] and springs it back on release.
 * The listener returns false so TabLayout still handles selection
 * and scrolling.
 */
fun View.applyTabPressScale(scale: Float = 0.95f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
        false
    }
}
