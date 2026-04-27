---
updated: 2026-04-26
status: accepted v3
owner: designer
summary: "태그 = 3개 DB 공통 multi_select + 시드 `모든주제` 1개 + Topics invariant 강제 (Claude는 태그 비관여, 전부 사용자 수동)"
consumed_by:
  - "2026-04-19 implementer: plans/tag-system 1~2단계 — NotionModels multi_select + TagNormalizer + ensureFreeTopicTag"
  - "2026-04-26 planner: 라운드 1 supersedes — 시드 이름 `자유주제` → `모든주제` rename, Claude 태그 자동 추천·자동 분류 폐기, 태그 부여는 전부 사용자 수동"
refs:
  - docs/plans/tag-system/README.md
  - docs/specs/mvp.md
---

# ADR-0003: 태그 시스템 데이터 모델 — Notion multi_select 단일축

**상태:** accepted v3
**날짜:** 2026-04-19 (v1) / 2026-04-19 (v2 — 사용자 답변 반영) / 2026-04-26 (v3 — 라운드 1 supersedes)
**결정자:** designer

## Round-1 supersedes (v3, 2026-04-26)

라운드 1 확정 (specs/mvp.md §3 leading callout)이 본 ADR의 다음 부분을 갱신한다. 본 ADR 본문의 다른 결정(데이터 모델, 정규화, 매칭 진실 위치 등)은 그대로 유효하다.

1. **시드 태그 rename**: `자유주제` → `모든주제`. 동일 invariant(모든 Topic에 기본 부여, 사용자가 수정·삭제 불가) 유지. (이전 이름 `자유주제`는 폐기 — 본 ADR v1·v2 본문에서 그대로 인용된 부분은 역사적 맥락으로 남기되 새 결정·설명·예시는 모두 `모든주제`를 쓴다.)
2. **Claude 제거**: 태그 자동 추천·자동 분류·Claude 응답에 `tags` 필드 포함 등의 흐름은 모두 폐기. 태그 생성과 Topic에의 태그 부여는 **전부 사용자 수동**. 본 ADR v1·v2가 §"의도적으로 결정하지 않는 것"에 남긴 "Claude가 주제 자동 생성 시 태그를 어떻게 추론하는지" 항목은 무효 — Claude는 태그를 추론하지 않는다.
3. **manual-only tag attachment**: 자동·수동·직접 작성 모든 주제 생성 경로에서 default = `모든주제` 단독. 사용자가 이후 수동으로 추가 태그를 붙이고 뗀다. retroactive 자동 분류·"새 태그 동의 프롬프트"·"사용 누적 후 시스템이 추천"은 모두 폐기.
4. **코드 상수**: `TagNormalizer.FREE_TOPIC_TAG` 식별자명은 본 sweep에서 변경하지 않는다. 리터럴 값(`"자유주제"` → `"모든주제"`)의 rename은 status.md의 별도 implementer 항목.

## 번호 할당 메모

ADR-0002의 "후속 작업" 섹션에서 ADR-0003은 "API 키 저장소 보안 강화"로 예약되어 있었으나, 해당 트랙(release-hardening)은 본 ADR 시점까지 ADR이 작성되지 않았다. MVP의 1일 E2E를 가로막는 1급 결정인 태그 모델이 더 시급하므로 ADR-0003 번호를 본 결정에 재할당한다. **API 키 저장소 보안 강화는 다음 빈 번호(현 시점 ADR-0004)로 재예약.** release-hardening 트랙 designer가 그 ADR을 작성할 때 본 메모를 참조.

## Revision history

- **v1 (2026-04-19)**: 최초 작성 — 시드 4개(IT/경제/문화/시사), 자유 등록, 정규화 정책, Newsletters DB가 매칭 진실, 요일당 태그 단수 가정.
- **v2 (2026-04-19)**: 사용자 답변 반영 — **시드를 `자유주제` 1개로 단순화**(이전 이름 `자유주제`는 폐기 — v3 참조), **invariant "Topics는 항상 시드 태그 포함" 추가**, 요일당 태그 다중 선택 허용(상세는 핸드오프 #6). 정규화 정책 변경 없음.
- **v3 (2026-04-26)**: 라운드 1 supersedes — 시드 이름 `자유주제` → `모든주제` rename, Claude 태그 자동 추천·자동 분류 폐기, 태그 부여는 전부 사용자 수동, MVP 슬롯은 단일 슬롯 1경로. (§"Round-1 supersedes" 참조.)

## 컨텍스트

`docs/specs/mvp.md`에서 planner는 태그를 1급 개념으로 편입했다.

- 키워드·주제·뉴스레터가 **공통**으로 가지는 분류 축. 예시는 spec에 IT/경제/문화/시사로 적혀 있으나 시드 정책은 본 ADR이 결정.
- 요일별 프린트 설정의 매칭 기준이 태그다 (요일 = 시각 + **태그(들)** + 페이지 수).
- 프린트 시점 lazy 생성의 트리거 조건이 "요일 태그(들)와 매칭되는 미프린트 뉴스레터 < 1"이다.

이로 인해 태그는 **3개 Notion DB(Keywords/Topics/Newsletters) 모두에 동일한 형태로 존재해야** 하며, 동일한 태그명이 3 곳에서 일관되게 비교 가능해야 한다 (lazy 매칭 쿼리가 "오늘 요일 태그 X = 뉴스레터의 태그 X"를 판단하기 때문).

현재 코드 상태:
- `NotionSetupService.setupDatabases()`가 3개 DB를 만들지만 **태그 속성 없음**.
- 키워드 등록/주제 생성/뉴스레터 저장 어디에도 태그 부여 경로 없음.
- 로컬 Room은 단일 settings k/v 테이블 — 태그 카탈로그를 둘 자리 없음 (또한 **Notion이 진실의 원천**이라는 원칙).

지금 결정해야 하는 이유:
1. MVP 핸드오프 #2(주제 생성), #4(lazy 매칭), #6(요일별 설정 UI), #7(매칭 0건 엣지 정책)이 모두 이 모델에 의존. 모델이 흔들리면 4개 플랜이 함께 흔들린다.
2. Notion 데이터베이스 속성 변경은 마이그레이션 비용이 크다 (데이터가 들어간 뒤에는 reshape이 어려움). 본격 사용 전에 최종 형태를 잡아야 한다.
3. **MVP는 본인 1명, 기존 데이터 없음** — 지금이 모델 결정에 가장 비용이 싼 시점.

## 결정

### 1. 태그 저장 위치: Notion `Tags` **multi_select** 속성, 3개 DB에 동일 이름·동일 옵션 셋

- Keywords / Topics / Newsletters 3개 DB 각각에 `Tags` 라는 동일 이름의 multi_select 속성을 추가.
- 이름·옵션 컬렉션은 3개 DB가 **독립**으로 가진다 (Notion multi_select는 DB 단위 옵션 풀). 동기화는 앱 레이어가 책임.
- 옵션 색상은 시드 시점에만 지정. 이후 사용자가 Notion UI에서 자유 변경 가능 — 앱은 색상에 의존하지 않는다.

### 2. 시드: `자유주제` 단일 옵션 (v2 변경)

- `NotionSetupService.setupDatabases()`가 3개 DB를 만들 때 **공통 시드 옵션 1개**만 등록한다.
  - 이름: `자유주제`
  - 색상: `gray` (시각적으로 "기본/안전망" 의미)
- 신규 태그가 페이지 생성/수정 시 `multi_select` 값으로 들어오면 **Notion이 schema에 자동 등록**한다 (공식 문서 명시: integration이 write 권한을 가지면 새 옵션명이 데이터 소스 schema에 추가됨; 출처는 본 ADR "관련" 섹션).
- 이로써 **단일 안전망(자유주제) + 자유 입력(개인용 유연성)** 양쪽을 한 모델에서 만족.
- v1의 시드 4개 분류(IT/경제/문화/시사) 안은 채택하지 않음 — 사유는 §대안 (E) 참조.

### 3. invariant: "모든 Topics 레코드는 항상 `자유주제` 태그를 포함한다" (v2 신설)

- **강제 지점**: `TopicRepository.saveTopic(...)` 호출 직전, 정규화 단계에서 `자유주제`(정규화 키 일치) 누락 시 자동 보충(append) — 애플리케이션 레이어 책임.
  - multi_select는 모델 레벨에서 "특정 옵션 필수"를 강제할 수단이 없으므로(Notion property는 required/default 미지원), **앱이 강제하는 것이 유일한 메커니즘**.
- **적용 범위**: Topics 한정. Keywords·Newsletters에는 자동 부착하지 않는다.
  - Newsletters에는 NewsletterGenerationService가 주제(들)의 태그 합집합으로 채우는 경로(핸드오프 #4 영역)를 통해 자연스럽게 `자유주제`가 따라 들어올 수 있음 — 본 ADR은 강제하지 않음.
- **함의**:
  - 어떤 경로(자동/수동/직접 작성)로 만들어진 주제든 최소 1개 태그(`자유주제`)를 가짐 → "태그 없는 주제" 부재.
  - 사용자가 Notion UI에서 옵션을 지워도, 다음 saveTopic 호출 시 자동 등록 메커니즘에 의해 옵션이 다시 등록되어 invariant가 회복됨.
  - 요일별 설정에서 사용자가 `자유주제`를 (단독 또는 다른 태그와 함께) 선택하면 매칭이 사실상 항상 성립 — 핸드오프 #7(매칭 0건 엣지)의 발생 빈도가 크게 줄어듦.
- 강제 로직 의사코드는 `docs/plans/tag-system.md` §"자유주제 자동 보충 로직" 참조.

### 4. 정규화 정책: 표시는 사용자 입력 원형, 비교는 정규화 키

- **저장 표시 형태(canonical display)**: 사용자가 입력한 그대로의 문자열을 trim()만 적용.
- **비교 키(matching key)**: `trim() + 소문자화 + 내부 공백 단일화`. 예: `"  IT  "`, `"it"`, `"It"`, `"i  t"` 모두 `"it"`로 정규화되어 동일 태그로 취급.
- 한글은 lowercase 변환의 영향이 없으므로 동일하게 동작.
- **앱 레이어의 책임**:
  - 페이지 작성 시 사용자가 입력한 새 태그가 기존 옵션의 정규화 키와 충돌하면 **기존 옵션의 표시 형태를 재사용**한다 (Notion에 신규 옵션이 중복 생성되지 않게 함). 이를 위해 앱은 각 DB의 현재 옵션 목록을 주기적으로 조회.
  - 요일별 설정의 태그(설정 화면에서 사용자가 고르는 값)와 뉴스레터 페이지의 태그 매칭은 정규화 키로 비교.
  - invariant 강제(§3) 시에도 정규화 키로 `자유주제` 포함 여부 판단 (예: 사용자가 ` 자유주제 `를 입력했으면 자동 보충하지 않음).
- **Notion API의 제약**: multi_select 옵션 이름에 **쉼표(`,`) 사용 불가**. 입력 검증 단계에서 차단.

### 5. 스키마 동기화 정책: Newsletters DB가 진실, 다른 두 DB는 근사 동기화

- lazy 매칭 쿼리(`요일 태그 ∩ 뉴스레터 태그`)는 **Newsletters DB의 multi_select**를 기준으로 돈다. 따라서 **Newsletters DB의 옵션 풀이 매칭의 진실**이다.
- Keywords / Topics DB의 옵션 풀은 사용자 입력 편의(자동완성, 추천)를 위한 보조. 일치하지 않아도 매칭 정확도에는 영향 없음 — 매칭은 페이지 값(문자열)을 기준으로 하기 때문.
- 즉 **3개 DB의 옵션 풀을 강제로 동일하게 유지하지 않는다**. 각각 자연스럽게 채워지게 두고, 앱은 정규화 키로 의미적 동일성을 보장.

### 6. 요일별 설정의 태그 저장 위치 + 다중성 (v2 보강)

- 요일 7개 × {시각, 태그명 **N개**, 페이지 수, on/off} 구조는 `SettingsEntity`의 k/v로 저장 (구체적 키 네이밍·직렬화는 핸드오프 #6에서).
- **요일당 태그는 다중 선택 허용** — 사용자 답변(2026-04-19) 반영.
- 태그명만 저장 (옵션 ID 아님 — multi_select 옵션의 안정적 ID는 Notion 내부값이라 외부 노출 불안정). 매칭은 정규화 키로.
- **OR/AND 매칭 정책은 본 ADR이 결정하지 않는다** — 핸드오프 #6의 ADR/플랜에서. 본 ADR의 권고는 OR(lazy 후보 풀 확장).
- 본 ADR은 "어디에 둔다" + "다중 선택 허용"만 결정. **요일 모델 자체의 스키마는 핸드오프 #6의 ADR/플랜에서**.

## 대안

### (B) Notion `relation` + 별도 `Tags` 데이터베이스 — 기각

- 4번째 DB(`Tags`)를 만들고 Keywords/Topics/Newsletters가 relation으로 참조.
- 장점: 옵션 단일 진실 소스, rename이 자동 전파, ID 기반 안정성.
- 기각 사유:
  1. **MVP 의도 부합도 낮음** — spec은 태그를 "분류 축"으로 정의했고 별개의 엔티티 라이프사이클을 요구하지 않는다. 4개 DB는 MVP 1일 E2E의 인지 부담만 키운다.
  2. **lazy 매칭 쿼리 복잡도** — Notion API의 relation 필터는 ID 기반이라 매칭 전에 매번 Tags DB lookup이 1단계 추가됨. lazy 생성 경로의 지연 비용이 늘어남.
  3. **사용자 직접 작성 경로**(주제 3경로 중 1)에서 태그 자유 입력을 매번 4번째 DB에 페이지 생성 후 ID 받아오기 → relation 페이로드 구성으로 라운드트립 1+ 추가.
  4. 자동 등록 이점 상실 — multi_select가 신규 옵션을 자동 등록해주는 점이 자유 입력 흐름과 맞지 않게 됨.

### (C) Notion `select` (단일 선택) — 기각

- 각 페이지가 정확히 1개 태그.
- 기각 사유: 키워드/주제가 여러 분류 축에 걸칠 수 있다 ("AI 규제" → IT + 시사). 단일 분류는 의도 손실. 요일 매칭은 단일 태그로 한다 해도 콘텐츠 측이 다중이어야 매칭이 의미 있음. **v2 invariant("Topics는 항상 자유주제 + α") 자체를 표현 불가** — 단독으로 기각 사유로 충분.

### (D) Notion `rich_text` 자유 텍스트 (콤마 구분 등) — 기각

- 페이지 속성에 `"IT, 경제"` 같은 문자열.
- 기각 사유: Notion 측의 sanity(필터·자동완성·UI 색상)를 통째로 잃는다. lazy 매칭 쿼리가 substring 기반이 되어 오탐 가능 (예: "IT법" vs "IT").

### (E) 시드 4개 분류(IT/경제/문화/시사) — 기각 (v2 신설)

- 본 ADR v1이 채택하려 했던 안. spec §3 예시에 등장한 4개 분류축을 setup 시점에 미리 옵션 풀에 등록.
- 장점: 사용자가 첫 화면에서 익숙한 분류 후보를 바로 보고 태그를 부여할 수 있다 (인지 부담 ↓).
- 기각 사유 (사용자 의도 모델 정확화 — 2026-04-19 답변):
  - 사용자의 멘탈 모델은 **"처음부터 분류축을 강제하지 않고, 주제를 누적한 후에 그 누적 데이터를 보고 자생적으로 분류축을 만들어낸다"**. 사전 분류축 4개는 이 모델과 정반대 방향(top-down 강제)이다.
  - 시드 4개가 박혀 있으면 사용자가 무의식적으로 그 4개에 맞춰 태그를 부여하게 됨 — bottom-up 분류 발견을 저해.
  - 4개 중 어느 것에도 맞지 않는 콘텐츠("취미", "여행기", "에세이")가 들어왔을 때 사용자가 "기타"로 처리할지 새 태그를 만들지 매번 결정해야 함 — 마찰 ↑.
  - 대신 **"자유주제" 단일 시드 + invariant**가 안전망 역할 (분류 안 한 콘텐츠는 자동으로 자유주제로 묶임) + **후속 기능 "태그 추천"** 이 누적 데이터로부터 자생적 분류를 제안. 이게 사용자 모델과 일치.

### (F) 로컬 Room에 Tags 카탈로그 별도 보관 + Notion에는 텍스트만 — 기각

- 카탈로그를 로컬에 두고 Notion에 동기화.
- 기각 사유: **"Notion이 진실의 원천"** 원칙 위반. 단말 재설치/리셋 시 카탈로그 손실. 멀티 디바이스 시나리오(MVP 밖이지만)에서 깨짐.

## 영향

### 코드 변경 범위 (실제 작업은 implementer)

- `data/remote/notion/NotionModels.kt`: `NotionPropertySchema`에 `multi_select` 필드, `NotionSelectOptions`를 multi_select용으로 재사용/분리. `NotionPropertyValue`에 `multi_select: List<NotionMultiSelectValue>?` 추가. `NotionFilter`에 `multi_select: NotionMultiSelectFilter?` 추가 (`contains` 연산자 지원).
- `service/NotionSetupService.kt`: 3개 DB의 properties에 `Tags` multi_select 추가, **시드 옵션 1개(`자유주제`, color=`gray`) 등록**.
- `data/repository/KeywordRepository.kt`: `addKeyword(text, type, tags: List<String>)` — 시그니처 확장. `KeywordUiItem`에 `tags: List<String>` 추가. UI 흐름은 핸드오프 #1에서 결정.
- `data/repository/TopicRepository.kt`: `saveTopic(...)` 시그니처에 `tags: List<String>` 추가. **내부에서 `ensureFreeTopicTag()`로 invariant 강제** 후 Notion에 전송. `getTodayTopics()` 매핑에 tags. `TopicUiItem`에 tags 추가.
- `data/repository/NewsletterRepository.kt`: `saveNewsletter(...)` 시그니처에 `tags: List<String>` 추가. `NewsletterUiItem`에 tags 추가. **lazy 매칭 쿼리 신설**: `getUnprintedNewsletterByTag(tagKey: String): NewsletterUiItem?` — 핸드오프 #4에서 활용.
- `service/TopicSelectionService.kt`: Claude 프롬프트와 응답 스키마에 `tags` 필드 추가 (각 주제마다 0~N 태그 — invariant가 자유주제 보충). 응답 파싱 후 정규화 적용. **본 ADR은 모델만 결정 — 프롬프트 구체 변경은 핸드오프 #2.**
- `service/NewsletterGenerationService.kt`: 주제로부터 태그 합집합을 계산해 뉴스레터 저장 시 전달. **본 ADR은 모델만.**
- 신규 유틸: `data/tag/TagNormalizer.kt`(또는 동급 위치) — `normalize(input: String): String` + `ensureFreeTopicTag(input: List<String>): List<String>` + 상수 `FREE_TOPIC_TAG = "자유주제"`. 비교는 모두 normalize 경유.
- `SettingsEntity`: 별도 필드 추가 없음. 요일별 설정 키들은 핸드오프 #6에서 (다중 태그 직렬화 형태 포함).

### 마이그레이션 정책

- **MVP는 본인 1명, 기존 사용자 없음** — 마이그레이션 코드 없음. `NotionSetupService.setupDatabases()`는 "이미 keywordsDbId가 있으면 skip" 분기를 유지하지만, **태그 속성이 추가된 후 처음 setup하는 사용자만 정상 동작**한다.
- 이미 (개인 테스트로) DB가 만들어진 상태에서 본 ADR 적용 시: 사용자가 Notion에서 3개 기존 DB를 휴지통으로 보내고 `parentPageId`만 남긴 채 앱 재실행 → setup이 새로 돈다. `SettingsRepository.set(KEY_KEYWORDS_DB_ID, null)` 같은 reset UI는 핸드오프 #1 또는 #6에서 결정 (본 ADR scope 밖).

### 의도적으로 본 ADR이 결정하지 않는 것 (핸드오프로 위임)

- 태그 부여 UI (입력 칩, 자동완성, 색상 표시 여부) — 핸드오프 #1, #2의 UI 섹션.
- Claude가 주제 자동 생성 시 태그를 어떻게 추론하는지 (프롬프트 설계) — 핸드오프 #2. invariant 덕분에 빈 응답도 안전.
- lazy 매칭 알고리즘의 우선순위 (예: 동률 시 어떤 뉴스레터를 고르나) + 다중 태그 시 OR/AND — 핸드오프 #4 / #6.
- 매칭 0건 + 생성도 불가일 때 사용자에게 보일 문구 — 핸드오프 #7. invariant로 발생 빈도는 낮아짐.
- 요일별 설정 UI에서 태그 셀렉터의 출처 (Newsletters DB의 옵션 vs Topics DB의 옵션 vs 정규화된 합집합) + 다중 선택 UI 형태 — 핸드오프 #6 (본 ADR은 "Newsletters DB가 매칭 진실"이라는 원칙 + "다중 허용"만 제공).
- **신규 기능 "태그 추천"** — 누적된 주제로부터 새 태그 후보를 시스템이 제안하고 사용자가 채택하는 흐름. planner가 spec에 새 핸드오프(예: #9)로 등록 필요. 본 플랜·ADR scope 밖.

## 관련

- [ADR-0001](0001-harness-role-based-agents.md) — 역할 기반 에이전트
- [ADR-0002](0002-git-repo-and-secret-isolation.md) — git 초기화 (본 ADR 본문 §"번호 할당 메모" 참조)
- spec: [`docs/specs/mvp.md`](../specs/mvp.md) §3 "태그 시스템 (1급 개념)", §7 핸드오프 #3
- 플랜: [`docs/plans/tag-system.md`](../plans/tag-system.md) — 본 ADR의 실행 단계
- Notion API 근거:
  - [Property object — multi_select](https://developers.notion.com/reference/property-object) (스키마, 색상 enum, 쉼표 금지, name case-insensitive uniqueness)
  - [Page property values — multi_select](https://developers.notion.com/reference/page-property-values) (인용: "If the multi-select property does not yet have an option by that name, then the name will be added to the data source schema if the integration also has write access to the parent data source.")
  - [Update property schema object](https://developers.notion.com/reference/update-property-schema-object) (옵션 추가는 가능하나 기존 옵션의 name·color는 변경 불가)

## 후속 ADR 재예약

- **ADR-0004 (예정, 배포 직전)**: API 키 저장소 보안 강화 — EncryptedSharedPreferences vs Android Keystore vs secrets-gradle-plugin 비교. (ADR-0002의 "후속 작업"이 ADR-0003으로 예약했으나 본 ADR이 그 번호를 사용했으므로 ADR-0004로 이동.)
- **ADR-0005 (예정, 배포 직전)**: 공개 레포 이관 — 공개 범위/라이선스/README/CI.
