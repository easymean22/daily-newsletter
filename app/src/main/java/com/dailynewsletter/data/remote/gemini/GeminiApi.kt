package com.dailynewsletter.data.remote.gemini

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Path("model") model: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
