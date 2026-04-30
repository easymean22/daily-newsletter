# Task Brief: 뉴스레터 — 단일 주제 Deep Dive 모드 전환

Task ID: TASK-20260428-026
Status: active

## Goal
뉴스레터 작성 정책을 **N개 주제 묶음 → 1개 주제 deep dive**로 전환. 추상적 설명 대신 구체적 예시·시나리오·워크플로우 중심으로 깊게 다룬다. 그림은 후속(out of scope), 본 Task는 텍스트 콘텐츠 깊이만.

## User-visible behavior
- 수동 생성 (NewsletterScreen `+` 버튼) UI는 변경 없음 — 사용자는 여전히 태그 + 페이지 수 선택.
- 백엔드는 그 태그의 pending 주제 중 **가장 최신 1건**을 골라 그 주제만 deep-dive로 작성한 뉴스레터를 생성.
- 결과: Notion에 새 뉴스레터 페이지 1건. 제목은 그 단일 주제의 부제목. 본문은 한 주제를 깊고 구체적으로 다룬 헤더+단락 구성.
- 그 1개 주제만 `consumed`로 표시. 나머지 동일 태그 주제들은 다음 호출에서 다시 후보.

## Scope

### `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`

#### 1. `generateForSlot(tag, pageCount)` 흐름 변경
- `topics = topicRepository.findPendingTopicsByTag(tag)` 그대로 호출.
- 빈 리스트 → `IllegalStateException("...")` 그대로.
- **변경**: `val selectedTopic = topics.first()` (가장 최신, findPendingTopicsByTag가 Date desc 정렬).
- 이 `selectedTopic` 1건만 Gemini에 넘겨 deep dive 작성 요청.
- 응답으로 받은 html은 1주제 본문. selectedTopicIds = `[selectedTopic.id]` 고정.
- `topicRepository.markTopicsConsumed(listOf(selectedTopic.id))`로 1건만 consumed.

#### 2. 프롬프트 재작성
새 `prompt`:
```text
당신은 기술 학습 뉴스레터를 작성하는 AI입니다. 아래 단일 주제에 대해 deep dive로 깊고 구체적인 뉴스레터 1편을 작성하세요.

## 주제
- id: {selectedTopic.id}
- 제목: {selectedTopic.title}

## 작성 규칙
1. **한 주제만 다룸**: 다른 주제로 옮겨가지 말고 위 주제를 끝까지 깊게.
2. **분량**: 약 {charCount}자 (A4 {pageCount}페이지). 내용이 부족하면 더 깊은 하위 주제·세부 요소까지 파고들어서라도 분량을 채울 것.
3. **언어**: 한국어 기본, 정확한 기술 용어는 영어 표기.
4. **금지 — 추상적·일반론**: "효율적이다", "다양한 분야에 활용된다" 같은 두루뭉술한 표현 금지. 모든 문장은 검증 가능한 구체 사실·숫자·구조·동작으로.
5. **필수 — 구체화**:
   - 실제 코드/명령어/설정 스니펫 (해당하는 경우)
   - 실 사용 시나리오 1개 이상 (예: "X 회사가 Y 문제를 풀기 위해 Z를 도입한 결과 처리량이 W% 개선")
   - 워크플로우 또는 단계별 절차 (1→2→3 식)
   - 비교/벤치마크 수치 (있다면)
   - 알려진 함정·실수·반-패턴
6. **구조**:
   - <h1>{selectedTopic.title}</h1>
   - <h2>핵심 개념</h2><p>...</p> — 주제의 본질을 1~2문단으로 정의 (단, 사전적 정의가 아니라 "왜 이게 만들어졌고 무슨 문제를 푸는가"에 집중).
   - <h2>아키텍처 / 동작 원리</h2> — 내부 메커니즘. 그림이 있다면 표준 다이어그램 출처를 텍스트로 인용 (URL 직접 삽입은 하지 말 것 — 후속 작업).
   - <h2>실용 시나리오</h2> — 구체 사례 1~2개. 회사명/제품명/숫자 가능하면 실제 인용.
   - <h2>워크플로우 / 단계별 적용</h2> — 1, 2, 3 번호 매긴 절차. <ol>이 아니라 <h3>1. ...</h3><p>...</p> 식의 헤더 구조 사용.
   - <h2>주의점·반-패턴</h2> — 알려진 함정.
   - <h2>참고 자료</h2><ul><li>...</li></ul> — 공신력 있는 출처 URL 또는 문서명.
7. **완결 필수**: 응답은 반드시 `</body></html>`로 끝. 중간 끊김 금지.

## 출력 형식
JSON만 출력 (코드 블록·앞뒤 텍스트 금지):
{
  "selectedTopicIds": ["{selectedTopic.id}"],
  "titleSuffix": "<선택 주제 한 줄 부제목>",
  "html": "<위 구조로 작성한 HTML>"
}
```
- `charCount`, `pageCount`는 기존 로직 그대로 (`pageCount * 1800`).
- `today`는 더 이상 본문에 안 들어감 (Daily Newsletter 라벨 제거 — h1은 주제 제목).
- `selectedTopicIds`는 항상 정확히 1개. parser는 그대로 사용 가능.

#### 3. 부수 변경
- `companion object`나 helper들은 가능하면 그대로. parser들은 1개 주제도 동일 포맷이라 호환.
- `markTopicsConsumed`는 list에 1개만 들어감. catch 블록 그대로.
- `buildFullHtml(htmlBody)`도 그대로.

### Out of Scope
- `<img>` 태그 / Notion image block 처리 — 후속 별도 Task. (프롬프트도 URL 직접 삽입은 금지로 명시.)
- 수동 생성 UI에서 "주제 직접 선택" 피커 도입 — 후속. 현재는 태그 → 백엔드 자동 picking.
- 알람 자동 생성 — TASK-027에서 이 함수를 호출.
- Topic 우선순위 정책 변경 — 그대로 latest first.
- Tag system 변경 — 0.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt` (이미 `findPendingTopicsByTag`가 desc 정렬로 잘 동작 — 변경 불필요)
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- `app/src/main/java/com/dailynewsletter/alarm/*` (병렬 진행 중인 TASK-025 소유)
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No public 시그니처 변경 (`generateForSlot(tag, pageCount): GeneratedNewsletter` 그대로 유지 — 호출자 변경 회피).
- No prompt에 image URL 삽입 지시 (htmlToBlocks가 처리 못 함, 깨진 링크 위험).
- No 다른 파일 수정.

## Acceptance Criteria
- [ ] `generateForSlot` 내부에서 `topics.first()` 또는 동등한 single-topic 선택 로직 등장.
- [ ] `markTopicsConsumed`에 단일 id 리스트 (`listOf(selectedTopic.id)` 또는 `listOf(selectedId)`) 전달.
- [ ] 프롬프트 텍스트에 "deep dive", "단일", "추상적", "구체" 같은 키워드가 등장 (grep `deep dive\\|구체\\|추상적\\|단일`).
- [ ] 프롬프트 텍스트가 멀티 주제(`후보 주제`, `주제 선택 수`) 표현이 아니다 (grep 결과 0건).
- [ ] `selectedTopicIds.size > pageCount * 2` warning 코드는 의미 사라졌으므로 제거 또는 단일 id 가정 전제로 단순화.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "deep dive\\|구체\\|추상적\\|단일" app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `grep -n "후보 주제\\|주제 선택 수" app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개.
- 새 프롬프트 일부 인용 (구체화 규칙 부분).
- single-topic picking 코드 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄: 재빌드 → 수동 생성 → 결과 뉴스레터가 1주제 deep-dive 형태이고 구체 시나리오·워크플로우 포함하는지 확인.

## STOP_AND_ESCALATE
- `topics.first()`이 항상 latest를 보장하지 않을 가능성 (sort 누락) 발견 시 — TopicRepository 변경이 owned 밖이라 escalate.
- 프롬프트 길이가 maxOutputTokens(16384)에 부담되면 — pageCount * 1800 charCount는 그대로 두되 maxOutputTokens 그대로 유지. 만약 응답이 잘리는 경향이면 escalate (별도 Task로 모델/토큰 조정).
