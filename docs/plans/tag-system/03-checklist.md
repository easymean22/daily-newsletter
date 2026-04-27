---
updated: 2026-04-26
summary: "implementer 체크리스트 8단계 (1~2단계 완료; 라운드 1 어휘 정합)"
parent: ./README.md
---

# 단계별 플랜 (implementer 체크리스트)

## 1단계: Notion 모델 확장
- [x] `data/remote/notion/NotionModels.kt`에 `NotionMultiSelectSchema`, `NotionMultiSelectValue`, `NotionMultiSelectFilter` 추가 (또는 기존 `NotionSelectOptions` 재사용 결정).
- [x] `NotionPropertySchema.multiSelect`, `NotionPropertyValue.multiSelect`, `NotionFilter.multiSelect` 필드 추가.
- [ ] Gson 직렬화 검증 (간단한 unit test 또는 수동 로그 확인 — `HttpLoggingInterceptor`가 BODY로 찍히므로 setup 1회 호출로 검증 가능). — 컴파일 + assembleDebug 통과로 타입 안정성 확보. 실 호출 검증은 3단계 E2E에서.

## 2단계: 정규화 유틸 + 시드 태그 보충 유틸
- [x] `data/tag/TagNormalizer.kt` 신설. `normalize(String): String`.
- [x] `data/tag/TagDefaults.kt`(또는 `TagNormalizer.kt` 동일 파일)에 상수 `FREE_TOPIC_TAG`(라운드 1 후 리터럴 `"모든주제"`; 식별자 rename은 status.md 후속)와 `ensureFreeTopicTag(List<String>): List<String>` 함수.
- [x] unit test 5~7개: 영문/한글/혼합 공백/대소문자/혼합 다중공백/원형 보존 X (비교 키만 반환) + `ensureFreeTopicTag` 케이스 3개(빈 리스트 / 이미 포함 / 다른 표기로 포함 — `"모든주제 "`, `" 모든 주제"` 등 정규화 동치). 코드 상수 리터럴 rename 후 테스트 데이터도 함께 갱신.

## 3단계: NotionSetupService에 Tags 속성 시드
- [ ] 3개 DB 생성 호출의 properties 맵에 `"Tags"` multi_select 1개 시드(`모든주제`, color=`gray`) 추가.
- [ ] 기존 사용자가 setup을 다시 돌리지 않으므로 별도 마이그레이션 분기 없음 — 본 플랜은 "본인 1명, 기존 데이터 폐기 가능"을 전제.
- [ ] 수동 검증: Notion 페이지에서 3개 DB가 모두 `Tags` 컬럼을 가지고 시드 `모든주제`가 회색으로 나타나는지.

## 4단계: 3개 Repository에 태그 read/write 추가
- [ ] `KeywordUiItem`, `TopicUiItem`, `NewsletterUiItem`에 `tags: List<String>` 추가.
- [ ] `KeywordRepository.addKeyword` 시그니처 확장. `refreshKeywords` 매핑에 tags. `getPendingKeywords`도 자연 전파.
- [ ] `TopicRepository.saveTopic` 시그니처 확장. **내부에서 `ensureFreeTopicTag()` 적용** 후 Notion에 보내는 multi_select 페이로드 구성. invariant 강제 지점은 여기 한 곳.
- [ ] `TopicRepository.getTodayTopics` 매핑. `getAllPastTopicTitles`는 핸드오프 #2가 변경 예정 — 본 플랜에서는 매핑만 안 깨지게 유지.
- [ ] `NewsletterRepository.saveNewsletter` 시그니처 확장. `getNewsletters` 매핑.
- [ ] **신규 메서드** `NewsletterRepository.findUnprintedNewsletterByTag(tagName)`: `multi_select.contains` + `Status.equals "generated"` 필터 쿼리. 결과 첫 페이지의 첫 항목 반환 (선택 알고리즘은 #4가 정함 — 본 플랜은 "찾는다"까지만).

## 5단계: 옵션 풀 헬퍼 (핸드오프 #6으로 이월 — 2026-04-19 사용자 확정)
- [ ] `NewsletterRepository.listAvailableTagNames(): List<String>` — 데이터베이스 메타(`GET /v1/databases/{id}`)에서 multi_select 옵션 목록 조회. 캐시는 메모리 5분.
  - **주의**: 현재 `NotionApi`에 `getDatabase` 엔드포인트가 없다. 추가 필요. (Retrofit `@GET("v1/databases/{id}")` 1줄 + 응답 모델 확장.)
- [ ] 본 단계는 #6 진입 전에 필요. 본 플랜에서 만들어도 좋고, #6 플랜에서 만들어도 좋다 — implementer 판단. **사용자 확정: 본 플랜에서 제외, #6에서 처리.**

## 6단계: TopicSelectionService / NewsletterGenerationService의 태그 전파 (최소 변경)
- [ ] **본 플랜 scope**: 두 service의 `saveTopic` / `saveNewsletter` 호출 지점이 시그니처 변경으로 컴파일이 깨진다. 빈 리스트(`emptyList()`)로 임시 통과시켜 빌드만 유지.
- [ ] **`saveTopic`은 `ensureFreeTopicTag`로 invariant가 보장**되므로 임시 빈 리스트도 안전 (저장 시 `모든주제` 자동 부착됨).
- [ ] **TODO 주석**: "태그는 사용자 수동 부여만 — 자동 생성/직접 작성/수동 생성 모든 경로에서 default `모든주제` 단독, 추가 태그는 사용자가 수동으로. tag-system 플랜은 시그니처만 확장."
- [ ] 이 임시 처리가 #2 시작 전에 다른 단계로 가지 않도록 status.md에 명시.

## 7단계: 검증 (수동 E2E 한 번)
- [ ] 앱 신규 설치 → setup 실행 → Notion에서 3개 DB 모두 `Tags` 시드 `모든주제` 노출 확인.
- [ ] (#1·#2 UI가 아직 없으니) `KeywordRepository.addKeyword("테스트", "keyword", listOf("신규태그"))`를 디버그 진입점에서 호출 → Notion에서 Keywords DB에 페이지가 만들어지고 Tags 컬럼이 `신규태그`로 표시 + DB 옵션 풀에 `신규태그`가 자동 등록됐는지 확인.
- [ ] **invariant 검증**: 디버그 진입점에서 `TopicRepository.saveTopic("테스트 주제", ...., tags = emptyList())` 호출 → Notion Topics DB에 페이지가 만들어지고 Tags가 `모든주제` 단일로 자동 부착됐는지 확인.
- [ ] 동일 호출을 두 번 (대소문자 다르게: `"it"`, `"IT"`) → 옵션 풀에 중복 생성 안 되는지 (앱의 옵션 lookup이 동작하는지) 확인.

## 8단계: 산출물 상태 갱신
- [ ] 본 플랜 README frontmatter `status: in-progress` → 완료 시 `consumed`. consumed_by에 단계별 implementer 완료 기록 추가.
- [ ] `docs/status.md` 갱신 (완료 시 archive로 이관).
