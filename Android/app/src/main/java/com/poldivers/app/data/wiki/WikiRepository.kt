package com.poldivers.app.data.wiki

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
        val page = response.query?.pages?.values?.firstOrNull { it.pageid > 0 }
            ?: WikiPage(title = title, extract = "")
        pageCache[title] = page
        return page
    }
}
