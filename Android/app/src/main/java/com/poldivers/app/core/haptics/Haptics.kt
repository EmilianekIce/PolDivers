package com.poldivers.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.poldivers.app.core.prefs.AppPreferences
import com.poldivers.app.core.prefs.HapticStrength

/**
 * Vibration for every touch in the app, independent of the (often disabled or OEM-muted)
 * system touch feedback, switchable and with a strength setting in the app's settings.
 *
 * We drive the motor ourselves (duration + amplitude) instead of the system "click" effect,
 * which many phones play too faintly to notice. On Android 13+ the vibration is tagged as
 * media feedback so it is not scaled down by the "touch vibration" intensity slider.
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
     * Tick for taps. The activity fires it for every tap on the screen and components may call
     * it too -- taps closer than [DEBOUNCE_MS] are merged so one touch never buzzes twice.
     */
    fun tap() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTapMs < DEBOUNCE_MS) return
        lastTapMs = now
        val s = prefs.hapticStrength.value
        oneShot(s.tapMs, s.tapAmplitude)
    }

    /** Stronger pulse for confirmations (e.g. pull-to-refresh landed). */
    fun confirm() {
        val s = prefs.hapticStrength.value
        oneShot(s.tapMs * 2, 255)
    }

    /** Double pulse for errors / failed requests. */
    fun warn() {
        val s = prefs.hapticStrength.value
        play(VibrationEffect.createWaveform(longArrayOf(0, s.tapMs + 20, 80, s.tapMs + 20), -1))
    }

    /** Sample for the settings screen, ignores debounce. */
    fun preview(strength: HapticStrength) = oneShot(strength.tapMs, strength.tapAmplitude, force = true)

    private fun oneShot(durationMs: Long, amplitude: Int, force: Boolean = false) {
        val v = vibrator ?: return
        val amp = if (v.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        play(VibrationEffect.createOneShot(durationMs, amp), force)
    }

    private fun play(effect: VibrationEffect, force: Boolean = false) {
        if (!force && !prefs.hapticsEnabled.value) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                v.vibrate(effect)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 120L
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> {
    error("Haptics not provided -- wrap the composable tree in PolDiversApp with a LocalHaptics.Provides")
}
