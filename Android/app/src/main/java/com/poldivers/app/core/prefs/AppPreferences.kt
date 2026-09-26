package com.poldivers.app.core.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Languages the community API is known to localize (see LocalizedMessage in the OpenAPI spec). */
enum class ApiLanguage(val tag: String, val label: String) {
    POLISH("pl-PL", "Polski"),
    ENGLISH("en-US", "English"),
    GERMAN("de-DE", "Deutsch"),
    FRENCH("fr-FR", "Français"),
    SPANISH("es-ES", "Español"),
    ITALIAN("it-IT", "Italiano"),
    RUSSIAN("ru-RU", "Русский"),
    CHINESE_SIMPLIFIED("zh-Hans", "简体中文"),
    CHINESE_TRADITIONAL("zh-Hant", "繁體中文"),
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

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled

    fun setLanguage(lang: ApiLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, lang.name).apply()
        _language.value = lang
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
    }
}
