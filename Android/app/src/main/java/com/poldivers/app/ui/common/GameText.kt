package com.poldivers.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.poldivers.app.feature.news.parseGameMarkup

/**
 * Major Order / campaign text the way the game shows it: `<i=1>` runs highlighted, `<i=3>` bold,
 * and every planet name mentioned highlighted too (like the community trackers do).
 */
fun gameText(
    raw: String,
    highlight: Color,
    planetNames: Collection<String> = emptyList(),
    /** Tap on a planet name (receives the name as written in the text). */
    onPlanet: ((String) -> Unit)? = null,
    /** Tap on another highlighted term (<i=1> run). */
    onTerm: ((String) -> Unit)? = null,
): AnnotatedString {
    val spans = parseGameMarkup(raw.trim())
    val base = buildAnnotatedString {
        spans.forEach { span ->
            when (span.style) {
                null -> append(span.text)
                3 -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                else -> {
                    val start = length
                    withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)) { append(span.text) }
                    val term = span.text.trim()
                    if (onTerm != null && term.length >= 3 && planetNames.none { it.equals(term, ignoreCase = true) }) {
                        addLink(LinkAnnotation.Clickable("term", linkInteractionListener = { onTerm(term) }), start, length)
                    }
                }
            }
        }
    }
    val names = planetNames.filter { it.length >= 3 }.sortedByDescending { it.length }
    if (names.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        val text = base.text
        val taken = BooleanArray(text.length)
        names.forEach { name ->
            var from = 0
            while (true) {
                val i = text.indexOf(name, from, ignoreCase = true)
                if (i < 0) break
                val end = i + name.length
                val wordBoundary = (i == 0 || !text[i - 1].isLetterOrDigit()) && (end == text.length || !text[end].isLetterOrDigit())
                if (wordBoundary && (i until end).none { taken[it] }) {
                    addStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline), i, end)
                    if (onPlanet != null) {
                        val found = text.substring(i, end)
                        addLink(LinkAnnotation.Clickable("planet", linkInteractionListener = { onPlanet(found) }), i, end)
                    }
                    for (k in i until end) taken[k] = true
                }
                from = end
            }
        }
    }
}
