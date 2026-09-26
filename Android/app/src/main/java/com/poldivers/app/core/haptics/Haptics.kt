package com.poldivers.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.poldivers.app.core.prefs.AppPreferences

/**
 * Wraps the system Vibrator so every tap in the app can give the small "tick" the user asked
 * for -- this bypasses HapticFeedbackType, which on many OEM skins is a no-op unless the view
 * hierarchy opts in, and lets us respect the in-app haptics toggle in one place.
 */
class Haptics(context: Context, private val prefs: AppPreferences) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Light tick for taps: list items, tabs, toggles. */
    fun tap() = vibrateOneShot(durationMs = 12, amplitude = 90)

    /** Slightly stronger pulse for confirmations (e.g. pull-to-refresh landed). */
    fun confirm() = vibrateOneShot(durationMs = 20, amplitude = 160)

    /** Double pulse for errors / failed requests. */
    fun warn() {
        if (!isEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 25, 60, 25)
            val amplitudes = intArrayOf(0, 200, 0, 200)
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        if (!isEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun isEnabled(): Boolean = prefs.hapticsEnabled.value
}

val LocalHaptics = staticCompositionLocalOf<Haptics> {
    error("Haptics not provided -- wrap the composable tree in PolDiversApp with a LocalHaptics.Provides")
}
