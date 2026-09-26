package com.poldivers.app.data.wiki

import kotlinx.serialization.Serializable

/**
 * Thin DTOs for the standard MediaWiki Action API exposed by helldivers.wiki.gg/api.php.
 * We only ever call this on demand (user taps a search result), never bulk-scrape the wiki --
 * content stays cached in memory for the current session only and is rendered through our
 * own mobile-friendly UI instead of the wiki's own page layout.
 */

@Serializable
data class WikiSearchResponse(val query: WikiSearchQuery? = null)

@Serializable
data class WikiSearchQuery(val search: List<WikiSearchResult> = emptyList())

@Serializable
data class WikiSearchResult(
    val ns: Int = 0,
    val title: String,
    val pageid: Int,
    val snippet: String = "",
)

@Serializable
data class WikiPageResponse(val query: WikiPageQuery? = null)

@Serializable
data class WikiPageQuery(val pages: Map<String, WikiPage> = emptyMap())

@Serializable
data class WikiPage(
    val pageid: Int = 0,
    val title: String = "",
    val extract: String = "",
    val thumbnail: WikiThumbnail? = null,
)

@Serializable
data class WikiThumbnail(val source: String, val width: Int = 0, val height: Int = 0)
