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
     * Generates a newsletter using the latest pending topic regardless of tag.
     * Used by AlarmActivity when no unprinted newsletter is available.
     * Falls back to throwing if no pending topic exists at all.
     */
    suspend fun generateLatest(pageCount: Int): GeneratedNewsletter {
        // Try tag-less: pick the single latest pending topic across all tags
        val topic = topicRepository.getLatestPendingTopic()
            ?: throw IllegalStateException("주제가 없습니다")

        val apiKey = settingsRepository.getGeminiApiKey() ?: throw IllegalStateException("Gemini API Key 미설정")
        val charCount = pageCount * 1800

        val prompt = """
당신은 기술 학습 뉴스레터를 작성하는 AI입니다. 아래 단일 주제에 대해 deep dive로 깊고 구체적인 뉴스레터 1편을 작성하세요.

## 주제
- id: ${topic.id}
- 제목: ${topic.title}

## 작성 규칙
1. **한 주제만 다룸**: 다른 주제로 옮겨가지 말고 위 주제를 끝까지 깊게.
2. **분량**: 약 ${charCount}자 (A4 ${pageCount}페이지). 내용이 부족하면 더 깊은 하위 주제·세부 요소까지 파고들어서라도 분량을 채울 것.
3. **언어**: 한국어 기본, 정확한 기술 용어는 영어 표기.
4. **금지 — 추상적·일반론**: "효율적이다", "다양한 분야에 활용된다" 같은 두루뭉술한 표현 금지. 모든 문장은 검증 가능한 구체 사실·숫자·구조·동작으로.
5. **필수 — 구체화**:
   - 실제 코드/명령어/설정 스니펫 (해당하는 경우)
   - 실 사용 시나리오 1개 이상
   - 워크플로우 또는 단계별 절차 (1→2→3 식)
   - 비교/벤치마크 수치 (있다면)
   - 알려진 함정·실수·반-패턴
6. **구조**:
   - <h1>${topic.title}</h1>
   - <h2>핵심 개념</h2><p>...</p>
   - <h2>아키텍처 / 동작 원리</h2>
   - <h2>실용 시나리오</h2>
   - <h2>워크플로우 / 단계별 적용</h2>
   - <h2>주의점·반-패턴</h2>
   - <h2>참고 자료</h2><ul><li>...</li></ul>
7. **완결 필수**: 응답은 반드시 `</body></html>`로 끝. 중간 끊김 금지.
8. **그림**: <img-search query="영문 검색어"/> 또는 mermaid — 합산 최대 3개.

## 출력 형식
JSON만 출력 (코드 블록·앞뒤 텍스트 금지):
{
  "selectedTopicIds": ["${topic.id}"],
  "titleSuffix": "<선택 주제 한 줄 부제목>",
  "html": "<위 구조로 작성한 HTML>"
}
""".trimIndent()

        val systemInstruction = "당신은 기술 학습 뉴스레터를 작성하는 전문가입니다. 요청된 JSON 형식으로만 응답하세요."

        val response = GeminiRetry.withModelFallback("newsletter-latest", GeminiTopicSuggester.DEFAULT_MODEL, GeminiTopicSuggester.FALLBACK_MODEL) { model ->
            geminiApi.generateContent(
                apiKey = apiKey,
                model = model,
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
        }

        val rawText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini 응답이 비어 있습니다")

        val finishReason = response.candidates.firstOrNull()?.finishReason ?: "UNKNOWN"
        val rawTextChecked = if (!rawText.contains("</body>")) {
            Log.w(TAG, "generateLatest HTML truncated, finishReason=$finishReason")
            rawText + "\n</body></html>"
        } else {
            rawText
        }

        val cleanText = rawTextChecked
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val selectedTopicIds = parseSelectedTopicIds(cleanText)
        val titleSuffix = parseTitleSuffix(cleanText)
        val htmlBody = parseHtml(cleanText)
        val fullHtml = buildFullHtml(htmlBody)
        val title = if (titleSuffix.isNotBlank()) titleSuffix else topic.title

        val pageId = newsletterRepository.saveNewsletter(title, fullHtml, selectedTopicIds, pageCount, emptyList())

        try {
            topicRepository.markTopicsConsumed(listOf(topic.id))
        } catch (e: Exception) {
            Log.w(TAG, "generateLatest markTopicsConsumed failed: ${e.message}")
        }

        return GeneratedNewsletter(
            id = pageId,
            title = title,
            html = fullHtml,
            selectedTopicIds = selectedTopicIds,
            tags = emptyList()
        )
    }

    /**
     * Generates a newsletter for the given slot (tag + pageCount).
     * Picks the single latest pending topic by tag, calls Gemini for a deep-dive article,
     * saves to Notion, then marks that one topic as consumed.
     * Per ADR-0005 §결정 4: consumed transition happens immediately after save.
     */
    suspend fun generateForSlot(tag: String, pageCount: Int): GeneratedNewsletter {
        val apiKey = settingsRepository.getGeminiApiKey() ?: throw IllegalStateException("Gemini API Key 미설정")

        val topics = topicRepository.findPendingTopicsByTag(tag)
        if (topics.isEmpty()) {
            throw IllegalStateException("해당 태그($tag)의 pending 주제가 없습니다")
        }

        // Deep-dive mode: pick the single latest topic (findPendingTopicsByTag returns Date desc).
        val selectedTopic = topics.first()
        val charCount = pageCount * 1800

        val prompt = """
당신은 기술 학습 뉴스레터를 작성하는 AI입니다. 아래 단일 주제에 대해 deep dive로 깊고 구체적인 뉴스레터 1편을 작성하세요.

## 주제
- id: ${selectedTopic.id}
- 제목: ${selectedTopic.title}

## 작성 규칙
1. **한 주제만 다룸**: 다른 주제로 옮겨가지 말고 위 주제를 끝까지 깊게.
2. **분량**: 약 ${charCount}자 (A4 ${pageCount}페이지). 내용이 부족하면 더 깊은 하위 주제·세부 요소까지 파고들어서라도 분량을 채울 것.
3. **언어**: 한국어 기본, 정확한 기술 용어는 영어 표기.
4. **금지 — 추상적·일반론**: "효율적이다", "다양한 분야에 활용된다" 같은 두루뭉술한 표현 금지. 모든 문장은 검증 가능한 구체 사실·숫자·구조·동작으로.
5. **필수 — 구체화**:
   - 실제 코드/명령어/설정 스니펫 (해당하는 경우)
   - 실 사용 시나리오 1개 이상 (예: "X 회사가 Y 문제를 풀기 위해 Z를 도입한 결과 처리량이 W% 개선")
   - 워크플로우 또는 단계별 절차 (1→2→3 식)
   - 비교/벤치마크 수치 (있다면)
   - 알려진 함정·실수·반-패턴
6. **구조**:
   - <h1>${selectedTopic.title}</h1>
   - <h2>핵심 개념</h2><p>...</p> — 주제의 본질을 1~2문단으로 정의 (단, 사전적 정의가 아니라 "왜 이게 만들어졌고 무슨 문제를 푸는가"에 집중).
   - <h2>아키텍처 / 동작 원리</h2> — 내부 메커니즘. 표준 다이어그램 출처는 텍스트로 인용할 것 — <img> URL은 삽입하지 말 것 (후속 작업).
   - <h2>실용 시나리오</h2> — 구체 사례 1~2개. 회사명/제품명/숫자 가능하면 실제 인용.
   - <h2>워크플로우 / 단계별 적용</h2> — 1, 2, 3 번호 매긴 절차. <ol> 대신 <h3>1. ...</h3><p>...</p> 식의 헤더 구조 사용.
   - <h2>주의점·반-패턴</h2> — 알려진 함정.
   - <h2>참고 자료</h2><ul><li>...</li></ul> — 공신력 있는 출처 URL 또는 문서명.
7. **완결 필수**: 응답은 반드시 `</body></html>`로 끝. 중간 끊김 금지.
8. **그림 첨부 — 사진 우선, 다이어그램은 보조**:
   각 시각화 지점에서 **다음 우선순위**를 따르세요:
   - **(1순위) 실제 사진/이미지**: 개념이 사람·장치·제품·장소·실험 결과 같이 시각적 실체가 있는 경우. 마커: `<img-search query="영문 검색어"/>`.
     - 검색어는 영문 구체 명사 (예: "Linux kernel architecture diagram", "TCP three way handshake", "Kubernetes cluster"). "computer", "data" 같은 일반어 금지.
     - 1편당 최대 2개.
   - **(2순위, 사진이 어울리지 않을 때만) mermaid 다이어그램**: 개념이 **구조·흐름·관계** 자체일 때 (실 사진은 의미 없는 경우):
     - 시스템 아키텍처/구성요소 → flowchart
     - 시간순 메시지 흐름 → sequenceDiagram
     - 상태 전이 → stateDiagram
     - 데이터 모델 관계 → erDiagram
     - 형식: `<pre><code class="language-mermaid">...mermaid 텍스트...</code></pre>`
     - 추상적 단순 박스 다이어그램 금지 — 실제 정보 담긴 경우만. 노드 라벨 한글 OK. 문법 불확실하면 생략.
     - 1편당 최대 2개.
   - **합산 상한**: 사진 + mermaid 합쳐 최대 3개. 같은 개념에 둘 다 쓰지 말 것.
   - 그림이 본문 이해에 도움 안 되는 추상 개념은 둘 다 생략.

## 출력 형식
JSON만 출력 (코드 블록·앞뒤 텍스트 금지):
{
  "selectedTopicIds": ["${selectedTopic.id}"],
  "titleSuffix": "<선택 주제 한 줄 부제목>",
  "html": "<위 구조로 작성한 HTML>"
}
""".trimIndent()

        val systemInstruction = "당신은 기술 학습 뉴스레터를 작성하는 전문가입니다. 요청된 JSON 형식으로만 응답하세요."

        val response = GeminiRetry.withModelFallback("newsletter", GeminiTopicSuggester.DEFAULT_MODEL, GeminiTopicSuggester.FALLBACK_MODEL) { model ->
            geminiApi.generateContent(
                apiKey = apiKey,
                model = model,
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
        }

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

        // Parse JSON response — selectedTopicIds is always exactly 1 element in deep-dive mode.
        val selectedTopicIds = parseSelectedTopicIds(cleanText)
        val titleSuffix = parseTitleSuffix(cleanText)
        val htmlBody = parseHtml(cleanText)

        val fullHtml = buildFullHtml(htmlBody)
        val title = if (titleSuffix.isNotBlank()) titleSuffix else selectedTopic.title

        // TODO(round-1): Gemini는 태그에 관여하지 않는다. 모든 경로의 default 태그는 emptyList() (saveTopic/saveNewsletter 내부의 ensureFreeTopicTag invariant 보충).
        val pageId = newsletterRepository.saveNewsletter(title, fullHtml, selectedTopicIds, pageCount, emptyList())

        // ADR-0005 §결정 4: mark the single selected topic consumed immediately after save.
        try {
            topicRepository.markTopicsConsumed(listOf(selectedTopic.id))
        } catch (e: Exception) {
            Log.w(TAG, "markTopicsConsumed failed: ${e.message}")
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
            Log.w(TAG, "Failed to parse selectedTopicIds: ${e.message}")
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
            Log.w(TAG, "Failed to parse html field: ${e.message}")
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
