package com.dailynewsletter.data.remote.claude

import com.google.gson.annotations.SerializedName

data class ClaudeRequest(
    val model: String = "claude-sonnet-4-6-20250514",
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    val messages: List<ClaudeMessage>,
    val system: String? = null
)

data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeResponse(
    val id: String,
    val content: List<ClaudeContent>,
    val model: String,
    @SerializedName("stop_reason") val stopReason: String?,
    val usage: ClaudeUsage
)

data class ClaudeContent(
    val type: String,
    val text: String
)

data class ClaudeUsage(
    @SerializedName("input_tokens") val inputTokens: Int,
    @SerializedName("output_tokens") val outputTokens: Int
)
