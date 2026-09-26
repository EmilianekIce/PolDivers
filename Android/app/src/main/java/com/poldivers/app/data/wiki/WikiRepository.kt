package com.poldivers.app.data.wiki

import androidx.core.text.HtmlCompat
import java.net.URLEncoder

class WikiRepository(private val api: WikiApiService) {

    // Session-only cache: avoids re-fetching a page the user already opened this run.
    // We never pre-fetch or crawl -- every entry here was requested by an explicit tap.
    private val pageCache = mutableMapOf<String, WikiPage>()
    private val articleCache = mutableMapOf<String, WikiArticle>()

    suspend fun search(query: String): List<WikiSearchResult> {
        if (query.isBlank()) return emptyList()
        val results = api.search(query).query?.search.orEmpty()
        if (results.isEmpty()) return results
        val thumbs = runCatching {
            api.getThumbnails(results.joinToString("|") { it.title }).query?.pages?.values
                ?.mapNotNull { page -> page.thumbnail?.let { page.title to it.source } }
                ?.toMap()
        }.getOrNull().orEmpty()
        return results.map { it.copy(thumbnail = thumbs[it.title]) }
    }

    private val summaryCache = mutableMapOf<String, WikiPage?>()

    /**
     * Lead text + image for the given page titles in a single request (null = no such page).
     * Called when the user opens the campaigns tab, for the handful of campaigns on screen.
     */
    suspend fun getSummaries(titles: Collection<String>): Map<String, WikiPage?> {
        val missing = titles.filter { it !in summaryCache }.distinct()
        if (missing.isNotEmpty()) {
            val query = api.getSummaries(missing.joinToString("|")).query
            val byTitle = query?.pages?.values.orEmpty().filter { it.pageid > 0 }.associateBy { it.title.lowercase() }
            // Follow MediaWiki's title normalisation and redirects back to what we asked for.
            val renames = (query?.normalized.orEmpty() + query?.redirects.orEmpty()).associate { it.from.lowercase() to it.to.lowercase() }
            missing.forEach { title ->
                var key = title.lowercase()
                repeat(3) { renames[key]?.let { key = it } }
                summaryCache[title] = byTitle[key]
            }
        }
        return titles.associateWith { summaryCache[it] }
    }

    /** Galactic War campaigns parsed from the wiki's "Campaigns" page (one request, on demand). */
    suspend fun getCampaigns(): List<WikiCampaign> =
        parseCampaigns(api.getWikitext("Campaigns").parse?.wikitext.orEmpty())

    /** One full article, fetched only because the user opened it (tap / in-article link). */
    suspend fun getArticle(title: String): WikiArticle {
        articleCache[title]?.let { return it }
        val parse = api.getPageHtml(title).parse ?: throw IllegalStateException("Brak strony: $title")
        return WikiArticle(title = parse.title.ifBlank { title }, html = parse.text).also { articleCache[title] = it }
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
