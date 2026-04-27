package com.dailynewsletter.data.remote.gemini

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    @SerializedName("responseMimeType") val responseMimeType: String? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

data class GeminiCandidate(
    val content: GeminiContent,
    @SerializedName("finishReason") val finishReason: String?
)
