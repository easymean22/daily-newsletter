package com.dailynewsletter.service

import android.util.Log
import com.dailynewsletter.data.remote.gemini.GeminiApi
import com.dailynewsletter.data.remote.gemini.GeminiContent
import com.dailynewsletter.data.remote.gemini.GeminiGenerationConfig
import com.dailynewsletter.data.remote.gemini.GeminiPart
import com.dailynewsletter.data.remote.gemini.GeminiRequest
import com.dailynewsletter.data.repository.SettingsRepository
import com.dailynewsletter.ui.keyword.KeywordUiItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

data class SuggestedTopic(
    val title: String,
    val priorityType: String,         // "direct" | "prerequisite" | "peripheral"
    val sourceKeywordIds: List<String>,
    val reason: String
)

@Singleton
class GeminiTopicSuggester @Inject constructor(
    private val geminiApi: GeminiApi,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Asks Gemini to suggest topics based on pending keywords and past topic titles.
     * No tags field in response (round-1 spec: Gemini does not assign tags).
     * Throws on failure so callers (ViewModel) can surface the real reason via snackbar.
     */
    suspend fun suggest(
        pendingKeywords: List<KeywordUiItem>,
        pastTopicTitles: List<String>
    ): List<SuggestedTopic> {
        val apiKey = settingsRepository.getGeminiApiKey()
            ?: throw IllegalStateException("Gemini API Key 미설정")

        if (pendingKeywords.isEmpty()) return emptyList()

        val keywordList = pendingKeywords.joinToString("\n") { "- [${it.id}] ${it.title} (${it.type})" }
        val pastTopicList = if (pastTopicTitles.isNotEmpty()) {
            pastTopicTitles.joinToString("\n") { "- $it" }
        } else "없음"

        val prompt = """
당신은 학습 주제 선정 AI입니다. 사용자가 저장한 키워드/메모를 기반으로 읽을 주제를 제안해주세요.

## 저장된 키워드/메모
$keywordList

## 이미 다룬 주제 (선정하지 마세요)
$pastTopicList

## 선정 우선순위
1. 키워드로 직접 적어 놓은 내용 (priorityType: "direct")
2. 키워드를 종합했을 때 알면 좋을 선행 지식 (priorityType: "prerequisite")
3. 키워드와 기존 읽은 내용을 종합했을 때 알면 좋을 주변 지식 (priorityType: "peripheral")

## 규칙
- 이미 다룬 주제는 선정하지 않습니다
- 이미 다룬 주제와 중복되지 않는 주제를 1개 이상 제안하세요 (개수는 키워드 내용에 따라 자유롭게 판단)
- 각 주제는 구체적이고 학습 가능한 단위여야 합니다

## 응답 형식 (JSON 배열만 출력, 다른 텍스트 없이)
[
  {
    "title": "주제 제목",
    "priorityType": "direct|prerequisite|peripheral",
    "sourceKeywordIds": ["키워드id1"],
    "reason": "선정 이유"
  }
]
""".trimIndent()

        val response = geminiApi.generateContent(
            apiKey = apiKey,
            model = DEFAULT_MODEL,
            request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = prompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    maxOutputTokens = 8192,
                    temperature = 0.7,
                    responseMimeType = "application/json"
                )
            )
        )

        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini 응답에 content가 없습니다")
        Log.d(TAG, "Gemini raw response: $text")

        // Robust JSON array extraction: prefer fenced ```json block; fall back to first '[' .. last ']'.
        val jsonStr = extractJsonArray(text)
            ?: throw IllegalStateException("Gemini 응답에서 JSON 배열을 찾지 못했습니다: ${text.take(200)}")

        return try {
            Gson().fromJson(jsonStr, object : TypeToken<List<SuggestedTopic>>() {}.type)
        } catch (e: Exception) {
            throw IllegalStateException("Gemini 응답 JSON 파싱 실패: ${e.message}", e)
        }
    }

    private fun extractJsonArray(text: String): String? {
        val fenced = Regex("```(?:json)?\\s*(\\[[\\s\\S]*?\\])\\s*```").find(text)
        if (fenced != null) return fenced.groupValues[1].trim()

        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start in 0 until end) return text.substring(start, end + 1).trim()

        return null
    }

    companion object {
        private const val TAG = "GeminiTopicSuggester"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
