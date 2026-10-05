package com.poldivers.app.core.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Languages the community API is known to localize (see LocalizedMessage in the OpenAPI spec). */
enum class ApiLanguage(val tag: String, val label: String) {
    POLISH("pl-PL", "Polski"),
    ENGLISH("en-US", "English"),
}

enum class HapticStrength(private val pl: String, private val en: String, val tapMs: Long, val tapAmplitude: Int) {
    LIGHT("Słabe", "Light", 18, 110),
    MEDIUM("Średnie", "Medium", 28, 190),
    STRONG("Mocne", "Strong", 40, 255);

    val label: String get() = com.poldivers.app.core.i18n.tr(pl, en)
}

/**
 * Tiny SharedPreferences-backed settings store. No DataStore dependency needed for
 * two booleans/enums worth of state.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("poldivers_prefs", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<ApiLanguage> = _language

    init {
        com.poldivers.app.core.i18n.UiLang.english = _language.value == ApiLanguage.ENGLISH
    }

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled

    private val _hapticStrength = MutableStateFlow(
        runCatching { HapticStrength.valueOf(prefs.getString(KEY_HAPTIC_STRENGTH, null) ?: "") }
            .getOrDefault(HapticStrength.STRONG),
    )
    val hapticStrength: StateFlow<HapticStrength> = _hapticStrength

    /** Translate English-only texts (wiki, campaigns) automatically instead of on tap. */
    private val _alwaysTranslate = MutableStateFlow(prefs.getBoolean(KEY_ALWAYS_TRANSLATE, false))
    val alwaysTranslate: StateFlow<Boolean> = _alwaysTranslate

    fun setAlwaysTranslate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALWAYS_TRANSLATE, enabled).apply()
        _alwaysTranslate.value = enabled
    }

    /** Transitions, bounces, map waves... (settings -> "Animacje"). */
    private val _animations = MutableStateFlow(prefs.getBoolean(KEY_ANIMATIONS, true))
    val animations: StateFlow<Boolean> = _animations

    fun setAnimations(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS, enabled).apply()
        _animations.value = enabled
    }

    /** Galaxy map in 3D (WebGL) instead of the flat 2D map. */
    private val _map3d = MutableStateFlow(prefs.getBoolean(KEY_MAP_3D, true))
    val map3d: StateFlow<Boolean> = _map3d

    fun setMap3d(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_3D, enabled).apply()
        _map3d.value = enabled
    }

    /** True until the first launch has finished loading once (shows the "first start" notice). */
    val isFirstLaunch: Boolean get() = !prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)

    fun markFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, true).apply()
    }

    fun setHapticStrength(strength: HapticStrength) {
        prefs.edit().putString(KEY_HAPTIC_STRENGTH, strength.name).apply()
        _hapticStrength.value = strength
    }

    fun setLanguage(lang: ApiLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, lang.name).apply()
        _language.value = lang
        com.poldivers.app.core.i18n.UiLang.english = lang == ApiLanguage.ENGLISH
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _hapticsEnabled.value = enabled
    }

    private fun loadLanguage(): ApiLanguage {
        val saved = prefs.getString(KEY_LANGUAGE, null) ?: return ApiLanguage.POLISH
        return runCatching { ApiLanguage.valueOf(saved) }.getOrDefault(ApiLanguage.POLISH)
    }

    companion object {
        private const val KEY_LANGUAGE = "api_language"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        private const val KEY_ALWAYS_TRANSLATE = "always_translate"
        private const val KEY_ANIMATIONS = "animations"
        private const val KEY_MAP_3D = "map_3d"
        private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
    }
}
