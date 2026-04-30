# Task Brief: Mermaid 다이어그램 자동 삽입 (Gemini → Notion code block)

Task ID: TASK-20260428-032
Status: active

## Goal
Deep-dive 뉴스레터에 개념 이해를 돕는 다이어그램이 자동으로 들어가도록. Gemini가 본문 안에 mermaid 텍스트를 출력 → 우리가 Notion `code` 블록(`language: "mermaid"`)으로 변환 → Notion이 다이어그램으로 자동 렌더.

## User-visible behavior
- 뉴스레터 본문에 적절한 위치(아키텍처/플로우/시퀀스 설명 부근)에 mermaid 다이어그램 1~2개가 등장.
- Notion에서 보면 자동으로 그래픽 다이어그램으로 표시.
- 다이어그램이 부적절한 주제(개념적 설명만 필요한 주제)는 mermaid 없이도 OK.

## Scope

### 1. `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
TASK-026에서 작성한 deep-dive 프롬프트에 mermaid 지침 추가:

```text
**그림이 도움 되는 경우 mermaid 다이어그램 사용**:
- 시스템 아키텍처/구성요소 → flowchart
- 시간순 동작/메시지 흐름 → sequenceDiagram
- 상태 전이 → stateDiagram
- 데이터 모델 관계 → erDiagram
- 작성 형식: <pre><code class="language-mermaid">...mermaid 텍스트...</code></pre>
- 1편당 최대 2개. 추상적 단순 박스 다이어그램은 만들지 말 것 — 실제 정보가 담긴 경우만.
- 다이어그램 안의 노드 라벨은 한글 OK.
- mermaid 문법이 확실하지 않으면 그냥 생략.
```

이 지침을 기존 deep-dive 작성 규칙 7번 다음에 8번으로 추가. 그 이후 번호는 자동 +1.

### 2. `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
`htmlToBlocks(html: String): List<NotionBlock>` 보강:
- 정규식 또는 단순 파서로 `<pre><code class="language-mermaid">...</code></pre>` 패턴 추출.
- 추출된 mermaid 텍스트를 Notion `code` 블록으로 변환:
  ```kotlin
  NotionBlock(
      type = "code",
      code = NotionCodeBlock(
          richText = listOf(NotionRichText(text = NotionTextContent(mermaidText))),
          language = "mermaid"
      )
  )
  ```
- 처리 순서: heading_1/2/3, paragraph, ul/li 같은 기존 블록 처리에 mermaid 블록을 끼워넣을 것. 위치 보존이 안 되면 mermaid를 모두 모아 마지막에 붙여도 OK.
- 추출 후 원본 HTML에서 해당 `<pre>` 영역은 제거하고 나머지를 평소처럼 처리.

### 3. `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`
`NotionBlock`에 `code: NotionCodeBlock?` 필드 추가 (없다면). `NotionCodeBlock` data class 신규:
```kotlin
data class NotionCodeBlock(
    @SerializedName("rich_text") val richText: List<NotionRichText>,
    val language: String = "plain text"
)
```
- 기존 다른 블록 필드와 동일 컨벤션.
- snake_case ↔ camelCase는 `@SerializedName` 사용.

## Out of Scope
- 실제 사진/스크린샷 — Imagen/외부 검색 별도 Task.
- 다른 코드 언어(python, kotlin 등) 일반 코드 블록 변환 — 후속.
- mermaid 문법 클라이언트 검증 — Notion이 알아서 렌더 실패 처리.
- 알람/주제/키워드/설정 무관.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB 스키마 변경.
- No 새로운 외부 API 호출.

## Acceptance Criteria
- [ ] NewsletterGenerationService 프롬프트 텍스트 안에 "mermaid" 단어 등장 (grep `mermaid`).
- [ ] `NotionCodeBlock` data class 신규 정의 (NotionModels.kt 안).
- [ ] `NewsletterRepository.htmlToBlocks`가 `language-mermaid` 패턴을 처리 (grep `language-mermaid` 또는 `mermaid`).
- [ ] htmlToBlocks 결과에 `type = "code"` + `language = "mermaid"` 블록이 생성되는 코드 경로 존재.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "mermaid" app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `grep -n "NotionCodeBlock\\|language-mermaid\\|\\\"code\\\"" app/src/main/java/com/dailynewsletter/data`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 3개.
- 프롬프트 추가분 인용.
- htmlToBlocks 새 분기 인용.
- NotionCodeBlock 정의 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄: 재빌드 → 새 뉴스레터 생성 → Notion에서 mermaid 다이어그램 렌더 확인.

## STOP_AND_ESCALATE
- htmlToBlocks 현재 구조가 한 패스로 paragraph만 처리하는 형태라 mermaid 추출 후 잔여 HTML과 순서 합치는 게 너무 복잡하면 — mermaid 블록을 본문 끝에 모아 붙이는 단순 fallback 채택 (escalate 불필요).
- mermaid가 너무 자주 또는 너무 드물게 나오면 — 프롬프트 튜닝은 후속 Task.
