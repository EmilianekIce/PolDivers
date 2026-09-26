package com.poldivers.app.data.wiki

import retrofit2.http.GET
import retrofit2.http.Query

interface WikiApiService {

    @GET("api.php?action=query&list=search&format=json&srlimit=20")
    suspend fun search(@Query("srsearch") query: String): WikiSearchResponse

    @GET("api.php?action=query&prop=extracts|pageimages&format=json&explaintext=true&piprop=thumbnail&pithumbsize=480&redirects=1")
    suspend fun getPageExtract(@Query("titles") title: String): WikiPageResponse
}
