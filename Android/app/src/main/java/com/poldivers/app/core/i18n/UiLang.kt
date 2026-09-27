package com.poldivers.app.core.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Interface language, following the data language setting (PL/EN). Backed by Compose state, so
 * every composable that calls [tr] recomposes when the setting changes.
 */
object UiLang {
    var english by mutableStateOf(false)
}

/** Picks the Polish or English wording of an interface text. */
fun tr(pl: String, en: String): String = if (UiLang.english) en else pl
