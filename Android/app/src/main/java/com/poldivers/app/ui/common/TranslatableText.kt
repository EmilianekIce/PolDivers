package com.poldivers.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** English-only text (community data) with a small "PRZETŁUMACZ" toggle (on-device translation). */
@Composable
fun TranslatableText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val translator = remember { AppContainer.get(context).translator }
    val scope = rememberCoroutineScope()
    var translated by remember(text) { mutableStateOf<String?>(null) }
    var show by remember(text) { mutableStateOf(false) }
    var busy by remember(text) { mutableStateOf(false) }
    Column(modifier) {
        Text(if (show) translated ?: text else text, style = style, color = color)
        Text(
            when {
                busy -> "tłumaczę…"
                show -> "pokaż oryginał"
                else -> "przetłumacz na polski"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(enabled = !busy) {
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
