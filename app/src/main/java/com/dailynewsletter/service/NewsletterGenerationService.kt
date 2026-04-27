package com.dailynewsletter.service

import android.util.Log
import com.dailynewsletter.data.remote.gemini.GeminiApi
import com.dailynewsletter.data.remote.gemini.GeminiContent
import com.dailynewsletter.data.remote.gemini.GeminiGenerationConfig
import com.dailynewsletter.data.remote.gemini.GeminiPart
import com.dailynewsletter.data.remote.gemini.GeminiRequest
import com.dailynewsletter.data.repository.NewsletterRepository
import com.dailynewsletter.data.repository.SettingsRepository
import com.dailynewsletter.data.repository.TopicRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedNewsletter(
    val id: String,
    val title: String,
    val html: String,
    val selectedTopicIds: List<String>,
    val tags: List<String>
)

@Singleton
class NewsletterGenerationService @Inject constructor(
    private val geminiApi: GeminiApi,
    private val topicRepository: TopicRepository,
    private val newsletterRepository: NewsletterRepository,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val TAG = "NewsletterGenerationService"
    }

    /**
     * Generates a newsletter for the given slot (tag + pageCount).
     * Fetches pending topics by tag, calls Gemini, saves to Notion, then marks topics as consumed.
     * Per ADR-0005 §결정 4: consumed transition happens immediately after save.
     */
    suspend fun generateForSlot(tag: String, pageCount: Int): GeneratedNewsletter {
        val apiKey = settingsRepository.getGeminiApiKey() ?: throw IllegalStateException("Gemini API Key 미설정")

        val topics = topicRepository.findPendingTopicsByTag(tag)
        if (topics.isEmpty()) {
            throw IllegalStateException("해당 태그($tag)의 pending 주제가 없습니다")
        }

        val topicList = topics.joinToString("\n") { "- [${it.id}] ${it.title} (${it.priorityType})" }
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
        val charCount = pageCount * 1800

        val prompt = """
당신은 기술 뉴스레터 작성 AI입니다. 아래 후보 주제들 중 일부를 선택하여 학습용 뉴스레터를 작성해주세요.

## 후보 주제 (id + 제목 + 우선순위)
$topicList

## 작성 규칙
1. **주제 선택 수(N)**: N ≤ ${pageCount * 2} (${pageCount}페이지 × 2). 분량에 맞게 자유롭게 결정하되 이 상한을 초과하지 마세요.
2. **분량**: 약 ${charCount}자 (A4 ${pageCount}페이지 분량). 목표 글자수 = ${charCount}자.
3. **언어**: 기본적으로 한국어로 작성하되, 영어로 표현하는 것이 정확한 기술 용어는 영어로 작성
4. **톤**: 학술적이지만 지나치게 어려운 표현은 사용하지 않음
5. **구조**: 각 주제는 반드시 핵심 개념, 상세 설명, 실용적 예시 3개 섹션을 모두 포함해야 합니다. 어떤 주제도 섹션을 생략하지 마세요.
6. **출처**: 논문, 공식 문서, 신뢰할 수 있는 블로그 등 공신력 있는 자료를 우선 인용. 각 섹션 끝에 참고 자료 명시
7. **완결 필수**: 응답은 반드시 `</body></html>` 로 끝내야 합니다. 중간에 절대 끊지 말고 끝까지 작성하세요.

## 출력 형식
다음 JSON 형식으로 응답해주세요 (JSON만 출력, 코드 블록 없음):
{
  "selectedTopicIds": ["<선택한 topic id 목록>"],
  "titleSuffix": "<뉴스레터 부제목 (선택된 주제 요약, 한 줄)>",
  "html": "<HTML 본문 (아래 구조 참고)>"
}

html 구조:
<h1>Daily Newsletter - $today</h1>
각 주제별:
<h2>주제 제목</h2>
<h3>핵심 개념</h3><p>...</p>
<h3>상세 설명</h3><p>...</p>
<h3>실용적 예시</h3><p>...</p>
<p class="source">참고: [출처 목록]</p>
""".trimIndent()

        val systemInstruction = "당신은 기술 학습 뉴스레터를 작성하는 전문가입니다. 요청된 JSON 형식으로만 응답하세요."

        val response = geminiApi.generateContent(
            apiKey = apiKey,
            model = GeminiTopicSuggester.DEFAULT_MODEL,
            request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = "$systemInstruction\n\n$prompt"))
                    )
                ),
                generationConfig = GeminiGenerationConfig(maxOutputTokens = 16384, temperature = 0.7)
            )
        )

        val rawText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini 응답이 비어 있습니다")

        val finishReason = response.candidates.firstOrNull()?.finishReason ?: "UNKNOWN"
        val rawTextChecked = if (!rawText.contains("</body>")) {
            Log.w(TAG, "Newsletter HTML truncated, finishReason=$finishReason")
            rawText + "\n</body></html>"
        } else {
            rawText
        }

        val cleanText = rawTextChecked
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Parse JSON response
        val selectedTopicIds = parseSelectedTopicIds(cleanText)
        val titleSuffix = parseTitleSuffix(cleanText)
        val htmlBody = parseHtml(cleanText)

        // Soft warning if Gemini exceeded the N limit
        if (selectedTopicIds.size > pageCount * 2) {
            Log.w("NewsletterGenerationService",
                "Gemini selected ${selectedTopicIds.size} topics but limit is ${pageCount * 2} for pageCount=$pageCount. Not truncating to avoid html-id mismatch.")
        }

        val fullHtml = buildFullHtml(htmlBody)
        val title = if (titleSuffix.isNotBlank()) titleSuffix else "Daily Newsletter - $today"

        // TODO(round-1): Gemini는 태그에 관여하지 않는다. 모든 경로의 default 태그는 emptyList() (saveTopic/saveNewsletter 내부의 ensureFreeTopicTag invariant 보충).
        val pageId = newsletterRepository.saveNewsletter(title, fullHtml, selectedTopicIds, pageCount, emptyList())

        // ADR-0005 §결정 4: mark topics consumed immediately after save
        try {
            topicRepository.markTopicsConsumed(selectedTopicIds)
        } catch (e: Exception) {
            Log.w("NewsletterGenerationService", "markTopicsConsumed failed: ${e.message}")
        }

        return GeneratedNewsletter(
            id = pageId,
            title = title,
            html = fullHtml,
            selectedTopicIds = selectedTopicIds,
            tags = emptyList()
        )
    }

    private fun parseSelectedTopicIds(json: String): List<String> {
        return try {
            val match = Regex(""""selectedTopicIds"\s*:\s*\[([^\]]*)\]""").find(json)
            val content = match?.groupValues?.get(1) ?: return emptyList()
            Regex(""""([^"]+)"""").findAll(content).map { it.groupValues[1] }.toList()
        } catch (e: Exception) {
            Log.w("NewsletterGenerationService", "Failed to parse selectedTopicIds: ${e.message}")
            emptyList()
        }
    }

    private fun parseTitleSuffix(json: String): String {
        return try {
            val match = Regex(""""titleSuffix"\s*:\s*"([^"]*)"""").find(json)
            match?.groupValues?.get(1) ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parseHtml(json: String): String {
        return try {
            // Extract html value: find "html": "..." accounting for escaped quotes
            val startMarker = "\"html\":"
            val startIdx = json.indexOf(startMarker)
            if (startIdx < 0) return ""
            val afterMarker = json.indexOf('"', startIdx + startMarker.length)
            if (afterMarker < 0) return ""
            // Find the closing quote (not escaped)
            val sb = StringBuilder()
            var i = afterMarker + 1
            while (i < json.length) {
                val c = json[i]
                if (c == '\\' && i + 1 < json.length) {
                    val next = json[i + 1]
                    when (next) {
                        '"' -> sb.append('"')
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        '\\' -> sb.append('\\')
                        else -> sb.append(next)
                    }
                    i += 2
                } else if (c == '"') {
                    break
                } else {
                    sb.append(c)
                    i++
                }
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.w("NewsletterGenerationService", "Failed to parse html field: ${e.message}")
            ""
        }
    }

    private fun buildFullHtml(bodyHtml: String): String = """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<style>
    @page { size: A4; margin: 20mm; }
    body {
        font-family: 'Noto Sans KR', 'Malgun Gothic', sans-serif;
        line-height: 1.8;
        font-size: 11pt;
        color: #1a1a1a;
    }
    h1 {
        font-size: 18pt;
        color: #0d1b2a;
        border-bottom: 3px solid #1b263b;
        padding-bottom: 8px;
        margin-bottom: 20px;
    }
    h2 {
        font-size: 14pt;
        color: #1b263b;
        border-left: 4px solid #415a77;
        padding-left: 12px;
        margin-top: 24px;
    }
    h3 {
        font-size: 12pt;
        color: #415a77;
        margin-top: 16px;
    }
    p { margin: 8px 0; text-align: justify; }
    .source {
        font-size: 9pt;
        color: #778da9;
        border-top: 1px solid #e0e1dd;
        padding-top: 8px;
        margin-top: 16px;
    }
    code {
        background: #f0f0f0;
        padding: 2px 6px;
        border-radius: 3px;
        font-size: 10pt;
    }
</style>
</head><body>
$bodyHtml
</body></html>
""".trimIndent()
}
