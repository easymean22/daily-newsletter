package com.dailynewsletter.data.remote.wikimedia

import retrofit2.http.GET
import retrofit2.http.Query

data class WikimediaResponse(val query: WikimediaQuery?)
data class WikimediaQuery(val pages: Map<String, WikimediaPage>?)
data class WikimediaPage(val title: String, val imageinfo: List<WikimediaImageInfo>?)
data class WikimediaImageInfo(val url: String)

interface WikimediaApi {
    @GET("api.php")
    suspend fun search(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("generator") generator: String = "search",
        @Query("gsrsearch") query: String,
        @Query("gsrnamespace") namespace: Int = 6,
        @Query("gsrlimit") limit: Int = 1,
        @Query("prop") prop: String = "imageinfo",
        @Query("iiprop") iiprop: String = "url"
    ): WikimediaResponse
}
