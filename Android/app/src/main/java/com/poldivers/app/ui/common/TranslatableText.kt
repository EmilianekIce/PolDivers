package com.poldivers.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.poldivers.app.core.AppContainer
import kotlinx.coroutines.launch

/** When true, [TranslatableText] and the wiki reader translate by themselves (settings / campaign toggle). */
val LocalAutoTranslate = compositionLocalOf { false }

/** English-only text (community data) with a small "przetłumacz" toggle -> the language chosen in settings. */
@Composable
fun TranslatableText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val translator = remember { AppContainer.get(context).translator }
    val scope = rememberCoroutineScope()
    var translated by remember(text) { mutableStateOf<String?>(null) }
    var show by remember(text) { mutableStateOf(false) }
    var busy by remember(text) { mutableStateOf(false) }
    val auto = LocalAutoTranslate.current
    LaunchedEffect(text, auto) {
        if (!translator.canTranslate) return@LaunchedEffect
        if (!auto) {
            show = false
            return@LaunchedEffect
        }
        if (translated == null) {
            busy = true
            translated = runCatching { translator.translate(text) }.getOrNull()
            busy = false
        }
        show = translated != null
    }
    Column(modifier) {
        Text(if (show) translated ?: text else text, style = style, color = color)
        if (!translator.canTranslate) return@Column
        Text(
            when {
                busy -> "tłumaczę…"
                show -> "pokaż oryginał"
                else -> "przetłumacz"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(enabled = !busy) {
                com.poldivers.app.core.AppContainer.get(context).haptics.tap()
                if (translated != null) {
                    show = !show
                } else {
                    busy = true
                    scope.launch {
                        translated = runCatching { translator.translate(text) }.getOrNull()
                        show = translated != null
                        busy = false
                    }
                }
            },
        )
    }
}
