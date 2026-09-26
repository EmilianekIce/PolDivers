package com.poldivers.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.poldivers.app.core.prefs.AppPreferences

/**
 * Vibration for every touch in the app, independent of the (often disabled or OEM-muted)
 * system touch feedback, and switchable in the app's settings.
 *
 * Taps use the predefined "click" effect where available -- the same crisp tick keyboards use --
 * because very short custom one-shots (the old 12 ms / low amplitude) are below what most
 * vibration motors can render and simply were not felt.
 */
class Haptics(context: Context, private val prefs: AppPreferences) {

    private val appContext = context.applicationContext
    private var lastTapMs = 0L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Light tick for taps. The activity fires it for every tap on the screen and components may
     * call it too -- taps closer than [DEBOUNCE_MS] are merged so one touch never buzzes twice.
     */
    fun tap() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTapMs < DEBOUNCE_MS) return
        lastTapMs = now
        play(predefined = PREDEFINED_CLICK, fallbackMs = 25)
    }

    /** Stronger pulse for confirmations (e.g. pull-to-refresh landed). */
    fun confirm() = play(predefined = PREDEFINED_HEAVY_CLICK, fallbackMs = 45)

    /** Double pulse for errors / failed requests. */
    fun warn() {
        if (!isEnabled()) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40), -1)) }
    }

    private fun play(predefined: Int, fallbackMs: Long) {
        if (!isEnabled()) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(predefined)
            } else {
                VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        }
    }

    private fun isEnabled(): Boolean = prefs.hapticsEnabled.value

    private companion object {
        const val DEBOUNCE_MS = 120L
        // VibrationEffect.EFFECT_CLICK / EFFECT_HEAVY_CLICK (API 29), inlined so minSdk 26 compiles cleanly.
        const val PREDEFINED_CLICK = 0
        const val PREDEFINED_HEAVY_CLICK = 5
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> {
    error("Haptics not provided -- wrap the composable tree in PolDiversApp with a LocalHaptics.Provides")
}
