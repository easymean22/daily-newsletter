---
updated: 2026-04-19
summary: "백엔드 설계 — 옵션 C 채택 근거 + ClaudeTopicSuggester + 자동 경로 흐름 + 상세 시그니처"
parent: ./README.md
---

# 백엔드 설계

## 대안 (3개 비교)

### 옵션 A: 단일 진입점 `TopicSelectionService`에 3경로를 메서드 분기로 통합

- 기존 `TopicSelectionService` 1개 클래스에 메서드 3개:
  - `generateFromAllKeywords(): List<SelectedTopic>` — (a) 키워드 풀 전체 기반 (기존 `selectAndSaveTopics`의 본체).
  - `generateFromSpecificKeyword(keywordId: String): List<SelectedTopic>` — (a)의 좁은 변형, 새로 등록된 키워드 1개에 집중.
  - `composeManualTopic(title: String, tags: List<String>): SelectedTopic` — (c) Claude 호출 없이 사용자 입력을 `SelectedTopic`으로 매핑.
- 저장은 모두 `TopicRepository.saveTopic()` 단일 게이트웨이 → invariant 자동 적용.
- 순환 의존성(`TopicSelectionService` ↔ `TopicRepository`)은 다음 중 하나로 해소:
  - **A-1: `TopicRepository`에서 `TopicSelectionService` 의존성 제거** — `regenerateTopics()`를 ViewModel/Service 레벨로 끌어올림 (호출자가 두 객체를 모두 주입받아 순서대로 호출).
  - A-2: 현재의 setter 패턴 유지 + `DailyNewsletterApp.onCreate`에서 명시적 와이어링.
- (b) 수동 버튼은 (a)와 동일 메서드를 ViewModel에서 호출.

### 옵션 B: 경로별 전용 서비스 3개로 분리

- `AutoTopicGenerator` (자동 + 수동 버튼이 공유), `ManualTopicComposer` (직접 작성).
- 자동 경로의 키워드 입력 트리거는 `KeywordRepository.addKeyword`가 직접 `AutoTopicGenerator`를 호출.
- 책임이 깔끔히 분리되지만 클래스 수 +2, 공통 변환(`SelectedTopic` → `saveTopic` 인자) 중복.

### 옵션 C: 하이브리드 — Claude 호출 책임만 분리, 호출/저장 orchestration은 ViewModel

- `ClaudeTopicSuggester` (순수 Claude 호출 + 응답 파싱, side-effect 없음, repository 미주입).
- 저장은 ViewModel이 `TopicRepository.saveTopic()` 직접 호출.
- (c) 직접 작성은 `ClaudeTopicSuggester`를 거치지 않고 ViewModel이 바로 `saveTopic`.
- 자동 트리거(키워드 등록 직후)는 `KeywordViewModel`이 `addKeyword` 후 `ClaudeTopicSuggester` + `TopicRepository.saveTopic` 순으로 호출.
- 순환 의존성 자체가 사라짐(`TopicRepository`는 더 이상 Claude 관련 의존성을 갖지 않음).

## Trade-off (의도 부합도 > 복잡도 > 위험 > 배포 영향)

| 축 | A (단일 서비스 분기) | B (경로별 전용 서비스) | C (Suggester + ViewModel orchestration) |
| --- | --- | --- | --- |
| **spec 의도 부합** | 3경로가 한 클래스에 모여 "주제 생성"이라는 단일 책임 표현. 단 (c)는 Claude 호출이 없는데 같은 서비스에 있어 인지적 비대칭. | 책임 분리 명확하나 spec이 3경로를 "동일 결과(Topics DB 한 줄)"로 묶는 의도와 다소 어긋남. | "주제 저장은 한 곳, 주제 제안은 별도 컴포넌트"가 spec의 트리거 다양성과 결과 단일성을 모두 표현. **최선.** |
| **순환 의존성 해소** | A-1로 가능하나 `regenerateTopics`의 위치 이동 필요. A-2(setter 유지)는 현재의 잠재 버그를 영속화. | 호출 방향이 단방향(KeywordRepo → AutoGen → TopicRepo). 사이클 없음. | 호출 방향이 단방향(ViewModel → Suggester / ViewModel → TopicRepo). 사이클 없음. **가장 명료.** |
| **기존 코드 변경량** | 작음 — 메서드 추가 + setter 제거. | 중간 — 신규 클래스 2개 + DI 배선. | 중간 — 신규 클래스 1개(`ClaudeTopicSuggester`) + ViewModel 2곳(KeywordVM, TopicsVM) 변경 + `TopicRepository`에서 `TopicSelectionService` 주입 제거. |
| **자동 경로의 트리거 위치** | KeywordViewModel이 `addKeyword` 후 `TopicSelectionService.generateFromAllKeywords` 호출. ViewModel이 두 서비스에 의존. | `KeywordRepository.addKeyword`가 `AutoTopicGenerator`를 직접 호출 → Repository 책임이 부풀어 오름. | KeywordViewModel orchestration. ViewModel이 시나리오를 표현 ("키워드 추가했으니 주제도 시도"). |
| **(c) 경로의 단순성** | (c)도 같은 서비스에 들어가 Claude 미호출 케이스가 묘하게 섞임. | (c) 전용 Composer 클래스 — 단순. | ViewModel에서 `TopicRepository.saveTopic` 직접 호출 — Suggester 우회. **가장 가벼움.** |
| **`DailyTopicWorker` 처분** | 보조로 남기기 쉬움(같은 서비스 호출). 하지만 spec 재정의로 의도 충돌 잔존. | 마찬가지. | Worker 내부에서 Suggester + Repo 두 의존성 직접 주입 후 호출 — 또는 워커 자체를 제거. **본 플랜 권고: 제거.** |
| **공개 배포 영향** | 분기 메서드가 늘면 테스트 표면이 넓어짐. | DI 배선 1줄 추가. | DI 배선 1줄 추가 + ViewModel 책임이 약간 늘어 배포 전 수동 E2E에서 검증 시나리오 명확. |
| **invariant 보존** | 단일 saveTopic 게이트웨이 유지 — OK. | 동일 — OK. | 동일 — OK. |

## 추천안: **옵션 C (Suggester + ViewModel orchestration)**

근거:

1. **spec 의도 1:1 매핑** — spec §3은 "주제 생성은 **사용자 행동이 트리거**"라고 명시. C는 "사용자 행동(키워드 추가/버튼 탭/직접 입력) → ViewModel orchestration → 결과 저장" 흐름을 그대로 표현.
2. **순환 의존성 자연 해소** — `TopicRepository`에서 `TopicSelectionService` 의존성을 제거하면 setter 패턴(현재 호출되지 않아 사실상 깨진 코드) 자체가 사라진다.
3. **(c) 직접 작성 경로의 마찰 최소** — Claude 호출이 없는 경로가 Claude 서비스에 끼어 있지 않음.
4. **순수 함수에 가까운 Suggester** — 테스트 작성이 쉽고, 프롬프트 변경의 영향 범위가 좁음.
5. **`DailyTopicWorker` 처분 결정이 단순화** — spec이 워커 트리거를 빼버린 마당에 워커를 살릴 정당성이 약함. 본 플랜은 제거 권고.

## 추천안 상세 설계

### 신규 컴포넌트

#### `ClaudeTopicSuggester` (신설)

위치: `service/ClaudeTopicSuggester.kt`.

```kotlin
data class SuggestedTopic(
    val title: String,
    val priorityType: String,         // "direct" | "prerequisite" | "peripheral"
    val sourceKeywordIds: List<String>,
    val tags: List<String>,           // Claude가 제안한 태그 (정규화 전 원형)
    val reason: String                 // 디버그/로그용
)

@Singleton
class ClaudeTopicSuggester @Inject constructor(
    private val claudeApi: ClaudeApi,
    private val settingsRepository: SettingsRepository
) {
    suspend fun suggest(
        pendingKeywords: List<KeywordUiItem>,
        pastTopicTitles: List<String>,
        availableTagPool: List<String> = emptyList()
    ): List<SuggestedTopic>
}
```

- 내부에서 Claude 프롬프트를 조립 (현재 `TopicSelectionService.selectAndSaveTopics` 본체 로직 이동 + tags 필드 추가).
- **저장 책임 없음** — 호출자가 `TopicRepository.saveTopic`를 호출.
- 실패 시 빈 리스트 반환 (예외는 던지지 않음). API Key 미설정 같은 사용자 액션 필요 케이스만 `IllegalStateException` 유지.

#### Claude 프롬프트 변경 (요지)

현재 프롬프트의 응답 스키마에 `"tags"` 배열 1개 필드 추가:

```json
{
  "title": "...",
  "priorityType": "direct|prerequisite|peripheral",
  "sourceKeywordIds": ["..."],
  "tags": ["IT", "경제"],
  "reason": "..."
}
```

프롬프트에 태그 가이드 섹션 추가 (옵션 풀이 비어있지 않을 때):

```
## 태그 부여 가이드
- 다음 옵션 풀 안에서 0~3개 선택. 풀에 적절한 게 없으면 비워둘 것 (시스템이 "모든주제"를 자동 부착).
- 옵션 풀: ${availableTagPool.joinToString(", ")}
```

옵션 풀이 비어있으면 (= setup 직후) 자유 생성 허용 — invariant가 안전망.

#### `TopicRepository.saveTopic` 시그니처 (전제 플랜과 일치)

```kotlin
suspend fun saveTopic(
    title: String,
    priorityType: String,
    sourceKeywordIds: List<String>,
    tags: List<String>           // 본 플랜이 실제 값을 채워 넣는 첫 호출자
)
```

본 플랜은 `tags`에 정규화·invariant 적용된 값을 전달.

### 기존 컴포넌트 변경

#### `TopicSelectionService` 제거 또는 deprecate

- **권고: 제거** — `ClaudeTopicSuggester`가 본체 로직을 흡수. `TopicSelectionService`는 setter 패턴 + 본체가 setter에 의존 + 호출자 1군데(워커)만 → 잔여 가치 없음.
- 제거 시 영향: `TopicRepository`의 `topicSelectionService` 주입 제거 + `regenerateTopics()` 본체 변경 (아래).

#### `TopicRepository.regenerateTopics()` 재배치

- 현재: `TopicRepository`가 `TopicSelectionService`를 주입받아 호출 → 사이클의 원인.
- 변경 후: 본 메서드를 **`TopicsViewModel`로 끌어올림**. ViewModel이 두 의존성(`TopicRepository`, `ClaudeTopicSuggester`, `KeywordRepository`)을 받아 순서대로 호출:
  1. 오늘의 주제 모두 삭제 (`TopicRepository.deleteTodayTopics()` 신설 — 기존 `getTodayTopics + forEach delete` 묶음).
  2. Suggester로 새 주제 제안.
  3. 결과를 saveTopic 루프.
- `TopicRepository`에서 `topicSelectionService` 필드 제거 → 사이클 해소.

#### `DailyTopicWorker` 처분

본 플랜의 결론: **제거.**

근거:
- spec §3 "파이프라인 구동축 재정의"에 따라 주제 생성은 **사용자 행동 트리거**. 매일 T−2h에 자동으로 도는 워커는 의도 충돌.
- 현재 코드가 setter 미호출로 어차피 깨진 상태 → "유지하면 깨진 채 유지, 고치면 의도 위배" 양쪽 다 손해.
- spec §3 "기존 T−2h/T−30m Worker의 처분(제거 / 보조 역할로 재배치 / 유지)은 designer 결정"이 본 결정 권한을 명시적으로 위임.

영향:
- `WorkScheduler.scheduleDailyTopicSelection` + 호출 제거.
- `DailyTopicWorker.kt` 파일 삭제.
- `DailyNewsletterApp.CHANNEL_TOPICS` 알림 채널은 (b) 수동 버튼/(a) 자동 트리거의 사용자 알림으로 재활용 가능.

#### `KeywordRepository.addKeyword` 시그니처

전제 플랜 tag-system에 따라:

```kotlin
suspend fun addKeyword(
    text: String,
    type: String,
    tags: List<String>            // 키워드 입력 폼에서 부여한 태그 (옵션)
)
```

본 플랜은 `addKeyword`에 **트리거를 박지 않는다** — 자동 경로 트리거는 ViewModel orchestration (옵션 C 핵심).

#### `addKeyword`가 `KeywordUiItem`을 반환하도록 확장 (자동 경로 필요)

자동 경로 ViewModel orchestration이 "방금 추가한 키워드의 ID"를 알아야 `sourceKeywordIds` 후보를 구성할 수 있음. 현재 `addKeyword`는 `Unit` 반환 → 새로 만들어진 페이지 ID를 알 수 없음.

선택지:
- **C-α (권고)**: `addKeyword`가 생성된 `KeywordUiItem`을 반환. Notion `createPage` 응답의 `id`를 매핑.
- C-β: addKeyword 후 ViewModel이 `refreshKeywords` + 가장 최근 항목을 사용 — 동시성 위험.

C-α를 권고. 시그니처 변경:

```kotlin
suspend fun addKeyword(text: String, type: String, tags: List<String>): KeywordUiItem
```

### 자동 경로 트리거 흐름 (C-α 기반)

`KeywordViewModel.addKeyword(text, type, tags)`:

```kotlin
viewModelScope.launch {
    val newKeyword = keywordRepository.addKeyword(text, type, tags)

    // 자동 주제 생성 트리거 — 실패해도 키워드 추가는 성공으로 처리.
    runCatching {
        val pending = keywordRepository.getPendingKeywords()
        val past = topicRepository.getAllPastTopicTitles()
        val tagPool = newsletterRepository.listAvailableTagNames()  // 전제 플랜 5단계 (선택)
        val suggested = claudeTopicSuggester.suggest(
            pendingKeywords = pending,
            pastTopicTitles = past,
            availableTagPool = tagPool
        )
        suggested.forEach {
            topicRepository.saveTopic(
                title = it.title,
                priorityType = it.priorityType,
                sourceKeywordIds = it.sourceKeywordIds,
                tags = it.tags
            )
        }
        // 알림: "키워드 등록으로 주제 N개가 생성됐습니다" — 토스트 또는 스낵바.
    }.onFailure { e ->
        // 키워드 추가는 이미 성공. 자동 경로 실패는 알림으로만.
        _uiState.update { it.copy(autoGenWarning = e.toUserMessage()) }
    }
}
```

핵심 정책:
- **자동 경로의 실패는 키워드 등록의 실패가 아니다.**
- **자동 경로의 동기/비동기**: 위 코드는 ViewModel scope에서 그냥 `await` — 사용자는 키워드 추가 직후 화면이 잠시 로딩/스낵바를 보게 됨. 별도 워커로 떠넘기지 않음.
- **Claude 호출 비용·지연이 신경쓰일 경우**: 키워드 1개당 호출이 무거움. **debounce 또는 토글로 자동 경로를 끄는 옵션**이 필요할 수 있음 → 사용자 확인 필요 #1.

### 매칭 / 상태 갱신

- (a)/(b) 자동·수동 버튼 경로는 결과 주제들이 Topics DB에 새로 추가되므로 `TopicsViewModel`이 reload.
- 키워드의 `Status` 변환 ("pending" → "resolved")은 본 플랜 scope 밖. **본 플랜은 키워드 상태를 건드리지 않는다** (사용자 확인 필요 #2).

## 에러 / 빈 상태 (백엔드 보장 계약)

- **(a) 자동 경로에서 Claude API Key 미설정**: ViewModel이 잡아 사용자에게 "API 키를 먼저 설정해주세요" 한국어 메시지. 키워드 추가는 성공.
- **(a) 자동 경로에서 Claude 응답이 빈 리스트**: 알림/토스트로 "이번엔 새 주제가 만들어지지 않았어요" 정도. 사용자 액션 불필요.
- **(a) 자동 경로에서 Claude 응답 파싱 실패**: 자동 경로 실패로 처리, 사용자에게 알림. 키워드 추가는 성공.
- **(b) 수동 버튼에서 pending 키워드 0개**: spec §5 엣지 정책("pending 0개 스킵")의 일반화. 버튼을 disabled 처리하거나, 탭 시 "키워드를 먼저 추가해주세요" 안내. UI 결정.
- **(c) 직접 작성에서 제목 빈 문자열**: 저장 버튼 disabled. 인라인 검증.
- **(c) 직접 작성에서 태그에 쉼표 포함**: 인라인 에러 (전제 플랜 §"에러/빈 상태" 정책).
- **invariant 자동 부착**: 모든 경로에서 `tags` 빈 리스트로 saveTopic 호출 시 `모든주제`가 자동 부착됨 — 사용자에게 별도 알림 없음 (전제 플랜에 따름).
