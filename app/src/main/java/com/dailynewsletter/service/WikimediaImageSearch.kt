package com.dailynewsletter.service

import android.util.Log
import com.dailynewsletter.data.remote.wikimedia.WikimediaApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikimediaImageSearch @Inject constructor(
    private val wikimediaApi: WikimediaApi
) {
    companion object {
        private const val TAG = "WikimediaImageSearch"
    }

    suspend fun searchFirst(query: String): String? {
        return try {
            val resp = wikimediaApi.search(query = query)
            resp.query?.pages?.values?.firstOrNull()?.imageinfo?.firstOrNull()?.url
        } catch (e: Exception) {
            Log.w(TAG, "search failed for '$query': ${e.message}")
            null
        }
    }
}
