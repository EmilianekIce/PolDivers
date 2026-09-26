package com.poldivers.app.feature.news

private val TAG_REGEX = Regex("<i=\\d+>|</i>")

data class DispatchContent(val headline: String?, val body: String)

/**
 * Dispatch messages use game-internal BBCode-ish markup (<i=1>...</i>, <i=3>...</i>) purely for
 * in-game emphasis; the community API passes it through verbatim. We don't know the exact
 * semantics of each numeric variant, so we just strip the tags and treat the first paragraph
 * (usually the all-caps headline, e.g. "STRATEGIC ALERT") as a title.
 */
fun parseDispatchMessage(raw: String): DispatchContent {
    val cleaned = raw.replace(TAG_REGEX, "").trim()
    val parts = cleaned.split("\n\n", limit = 2)
    return if (parts.size == 2 && parts[0].length < 60) {
        DispatchContent(headline = parts[0].trim(), body = parts[1].trim())
    } else {
        DispatchContent(headline = null, body = cleaned)
    }
}
