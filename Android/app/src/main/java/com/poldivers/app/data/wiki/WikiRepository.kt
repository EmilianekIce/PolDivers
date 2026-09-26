package com.poldivers.app.data.wiki

import androidx.core.text.HtmlCompat
import java.net.URLEncoder

class WikiRepository(private val api: WikiApiService) {

    // Session-only cache: avoids re-fetching a page the user already opened this run.
    // We never pre-fetch or crawl -- every entry here was requested by an explicit tap.
    private val pageCache = mutableMapOf<String, WikiPage>()

    suspend fun search(query: String): List<WikiSearchResult> {
        if (query.isBlank()) return emptyList()
        return api.search(query).query?.search.orEmpty()
    }

    suspend fun getPage(title: String): WikiPage {
        pageCache[title]?.let { return it }
        val response = api.getPageExtract(title)
        var page = response.query?.pages?.values?.firstOrNull { it.pageid > 0 }
            ?: WikiPage(title = title, extract = "")
        if (page.extract.isBlank()) {
            val html = runCatching { api.getLeadHtml(title).parse?.text }.getOrNull().orEmpty()
            page = page.copy(extract = htmlToText(html))
        }
        pageCache[title] = page
        return page
    }

    companion object {
        fun pageUrl(title: String): String =
            "https://helldivers.wiki.gg/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("%2F", "/")

        private fun htmlToText(html: String): String {
            if (html.isBlank()) return ""
            // Infobox tables and reference markers are noise in a plain-text reader.
            val cleaned = html
                .replace(Regex("(?s)<table.*?</table>"), "")
                .replace(Regex("(?s)<sup.*?</sup>"), "")
                .replace(Regex("(?s)<style.*?</style>"), "")
            return HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }
    }
}
