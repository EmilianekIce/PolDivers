package com.poldivers.app.feature.news

private val TAG_REGEX = Regex("<i=(\\d+)>|</i>")

/** A run of dispatch text; [style] is the game's markup id (<i=1>, <i=3>...) or null for plain text. */
data class DispatchSpan(val text: String, val style: Int?)

data class DispatchContent(val headline: String?, val body: List<DispatchSpan>) {
    val plainBody: String get() = body.joinToString("") { it.text }
}

/**
 * Dispatch messages use game-internal markup for in-game emphasis: `<i=1>` is the yellow
 * highlight, `<i=3>` the bold headline. The community API passes it through verbatim, so we
 * turn it into spans (styled by the screen) instead of showing raw tags.
 *
 * The first paragraph is treated as the headline when it is short and fully marked up or
 * written in capitals (e.g. "STRATEGIC ALERT" / "ALARM STRATEGICZNY").
 */
fun parseDispatchMessage(raw: String): DispatchContent {
    val spans = parseSpans(raw.trim())
    val plain = spans.joinToString("") { it.text }
    val firstBreak = plain.indexOf("\n")
    if (firstBreak in 1 until 80) {
        val first = plain.substring(0, firstBreak).trim()
        val looksLikeHeadline = first == first.uppercase() || spans.firstOrNull()?.let {
            it.style != null && it.text.trim() == first
        } == true
        if (looksLikeHeadline) {
            return DispatchContent(headline = first, body = dropChars(spans, firstBreak).trimLeading())
        }
    }
    return DispatchContent(headline = null, body = spans)
}

/** Splits game text into plain / marked-up runs (tags removed). */
fun parseGameMarkup(raw: String): List<DispatchSpan> = parseSpans(raw)

private fun parseSpans(raw: String): List<DispatchSpan> {
    val spans = mutableListOf<DispatchSpan>()
    var style: Int? = null
    var cursor = 0
    TAG_REGEX.findAll(raw).forEach { match ->
        if (match.range.first > cursor) spans += DispatchSpan(raw.substring(cursor, match.range.first), style)
        style = match.groupValues[1].toIntOrNull()
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) spans += DispatchSpan(raw.substring(cursor), style)
    return spans.filter { it.text.isNotEmpty() }
}

private fun dropChars(spans: List<DispatchSpan>, count: Int): List<DispatchSpan> {
    var remaining = count
    val result = mutableListOf<DispatchSpan>()
    for (span in spans) {
        when {
            remaining <= 0 -> result += span
            span.text.length <= remaining -> remaining -= span.text.length
            else -> {
                result += span.copy(text = span.text.substring(remaining))
                remaining = 0
            }
        }
    }
    return result
}

private fun List<DispatchSpan>.trimLeading(): List<DispatchSpan> {
    val result = toMutableList()
    while (result.isNotEmpty()) {
        val trimmed = result[0].text.trimStart()
        if (trimmed.isEmpty()) result.removeAt(0) else {
            result[0] = result[0].copy(text = trimmed)
            break
        }
    }
    return result
}
