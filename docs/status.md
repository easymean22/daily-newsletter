---
updated: 2026-04-26
status: accepted
owner: planner
summary: "현재 진행 중 작업의 얇은 선언 인덱스 (결정 상세는 각 plan frontmatter/README)"
refs:
  - docs/specs/mvp.md
---

# 진행 중 작업 (Status)

공유 모듈 수정 전 여기에 선언 (글로벌 CLAUDE.md 규칙). **이 파일은 pointer만** — 결정 내용·trade-off는 각 plan/ADR 안에 둔다. 종료된 작업과 변경 이력은 [status/archive.md](./status/archive.md).

## 형식

```
- [YYYY-MM-DD | <role> | <상태>] <한 줄 요약> → <plan/spec 경로>
```

상태 값: `진행중`, `review`, `블록됨`, `검토대기`.

## 현재 진행 중

- [2026-04-19 | planner | 완료(spec)] MVP 라운드 1 의도 재확정 (모든주제 rename · Claude 태그 비관여 · 진열대 어휘 제거 · 단일 슬롯 MVP) → [specs/mvp.md](./specs/mvp.md)
- [2026-04-26 | planner | 완료] 라운드 1 문서 정합 sweep — ADR-0003 v3 / ADR-0005 어휘 교정 + accepted 전환 / ADR-0006 단일 슬롯 scope 명확화 / 4개 plan README에 라운드 1 callout 삽입 / sub-files 옛 어휘는 후속 정리로 위임. → docs/decisions/{0003,0005,0006}*.md, docs/plans/{tag-system,topic-generation-paths,newsletter-shelf,weekday-print-slots}/README.md
- [2026-04-26 | planner(직접) | 완료] `TagNormalizer.FREE_TOPIC_TAG` 리터럴 값 `"자유주제"` → `"모든주제"` rename + 테스트/주석 동시 갱신. 메인 세션 직접 처리(서브에이전트 비용 회피). → app/src/main/java/com/dailynewsletter/data/tag/TagNormalizer.kt, app/src/test/java/com/dailynewsletter/data/tag/TagNormalizerTest.kt
- [2026-04-26 | designer | accepted] 요일별 프린트 설정 UI (슬롯 묶음) → [plans/weekday-print-slots/](./plans/weekday-print-slots/README.md) (ADR-0006 accepted + 단일 슬롯 scope 명확화. implementer 대기 — 선행: tag-system 3~4, newsletter-shelf 1~8.)
- [2026-04-26 | designer | accepted (Scope A subset)] 뉴스레터 lazy 보충 모델 → [plans/newsletter-shelf/](./plans/newsletter-shelf/README.md) (ADR-0005 accepted; Q4=B / Q6=U-A 채택. Q1·Q2·Q3은 이터레이션 B/C로 이월. 단계 1·2·3·4·6·9·10 implementer 진입.)
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] newsletter-shelf 단계 1·2·3·4·6·8·9·10·12 완료 — `getPage` + `consumed` + 신규 repo 메서드 + `generateForSlot` + `NotificationHelper` + `NewsletterWorker` 삭제 + `WorkScheduler` 정리 + ViewModel orchestration + Screen BottomSheet UI. → docs/task-results/TASK-20260426-005-implementer-result.md
- [2026-04-26 | designer | in-progress] 태그 시스템 1급 편입 → [plans/tag-system/](./plans/tag-system/README.md) (1~4·6단계 완료. 5단계 #6 이월. 7단계 수동 E2E 대기.)
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] tag-system 3·4·6단계 — DB Tags 시드 + Repository 태그 read/write + 호출지 emptyList() 임시 처리 완료. → docs/task-results/TASK-20260426-003-implementer-result.md
- [2026-04-26 | designer | accepted] 주제 생성 3경로 → [plans/topic-generation-paths/](./plans/topic-generation-paths/README.md) (Q1~Q5 모두 해소.)
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] topic-generation-paths 단계 1·2·3·4·5·6·8·9·11 완료 — `ClaudeTopicSuggester` 신설, `TopicSelectionService`·`DailyTopicWorker` 삭제, `KeywordRepository.addKeyword` 반환 타입 `KeywordUiItem`, `KeywordViewModel` 자동 경로 orchestration, `KeywordScreen` 태그 입력 UI + 스낵바. → docs/task-results/TASK-20260426-004-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] §6 #2 PrintWorker 자동 enqueue 경로 disconnect — `WorkScheduler.schedulePrint` 제거(22줄 삭제). grep 0 matches·git diff 단일 파일 확인. 빌드는 로컬 Java 미설치로 미검증. → docs/task-results/TASK-20260426-002-brief.md, docs/task-results/TASK-20260426-002-implementer-result.md
- [2026-04-26 | planner | review] §6 #5 1일 E2E 리허설 plan **Scope A 확정** (수동 생성 버튼까지만 검증, 슬롯/스케줄러/IPP 비범위) — 사용자 확인 5개 모두 해소. **1차 시도에서 fatal crash 발견 — TASK-006 처리 후 재시도.** → [plans/e2e-rehearsal/README.md](./plans/e2e-rehearsal/README.md)
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] Notion 초기 설정 UI 트리거 + 4개 ViewModel 크래시 안전망 완료 — Settings에 "Notion DB 자동 생성" 버튼 + `ViewModelExtensions.exceptionHandler()` 신설 + 4개 ViewModel 모든 launch에 안전망 + 4개 Screen에 error 스낵바. 정적 grep 4개 PASS. → docs/task-results/TASK-20260426-006-implementer-result.md
- [2026-04-26 | planner(직접) | 완료(임시 직접 수정)] Scope A 1차 시도 후속: Notion `Title` 속성 `title = emptyMap()` 추가(HTTP 400 fix) + Claude 모델명 `claude-sonnet-4-6-20250514` → `claude-sonnet-4-5-20250929` + `ClaudeTopicSuggester` 침묵 catch 제거 / JSON 추출 견고화 / Log.d 추가. 메인 세션 직접 수정 — **이후 동일 패턴은 implementer 위임 규칙(~/CLAUDE.md "버그 픽스 위임 규칙") 적용**. → app/src/main/java/com/dailynewsletter/{service/NotionSetupService.kt, data/remote/claude/ClaudeModels.kt, service/ClaudeTopicSuggester.kt}
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] Keyword 기본 태그 invariant 확장 — `KeywordRepository.addKeyword`에서 `TagNormalizer.ensureFreeTopicTag(tags)` 적용 (line 66 정규화 → 87 createPage / 99 return 동일 사용, 5줄 이내 diff). grep 3 hits PASS. → docs/task-results/TASK-20260426-007-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] Keyword 화면 초기 진입 시 Notion 자동 로드 — `KeywordViewModel.loadKeywords()`에 `refreshKeywords()` 1회 호출 + 실패 시 `state.error` 노출 (4줄 삽입, line 64). → docs/task-results/TASK-20260426-008-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 깨짐 발견] Claude → Gemini 전체 전환 — 신규 3 / 수정 7 / 삭제 3 + claude 디렉터리 제거. 사용자 빌드에서 `SuggestedTopic` Unresolved reference 발견(TASK-010에서 후속 처리). → docs/task-results/TASK-20260426-009-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] TASK-009 누락분 — `data class SuggestedTopic` 복원 (`GeminiTopicSuggester.kt:16`, 6줄 삽입). grep 3 hits 모두 동일 파일 내(정의 1 + 사용 2) PASS. → docs/task-results/TASK-20260426-010-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] Gemini 구조화 출력 모드 — `GeminiGenerationConfig.responseMimeType` 신설(default null) + `GeminiTopicSuggester`가 `responseMimeType="application/json"` + `maxOutputTokens=8192` 사용. 프롬프트 마크다운 fence 예시 제거. extractJsonArray fallback 보존. grep 2 acceptance PASS. → docs/task-results/TASK-20260426-011-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] Notion rich_text 2000자 제한 회피 — `NewsletterRepository.saveNewsletter`가 `chunkText(html, 1900)` 결과를 `richText` 배열 N entries로 분할(line 92 + 127). private helper `chunkText` 신설(line 290). 13KB → 약 7 entries. grep PASS. → docs/task-results/TASK-20260426-012-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] HTML → Notion 네이티브 블록 변환 — `NotionBlock.heading_1` 신설, `htmlToBlocks` helper(line 287), `saveNewsletter.children = htmlToBlocks(htmlContent)`(line 122), p 블록은 chunkText로 1900자 분할 보존. h1/h2/h3/p/ul-li 매핑. inline 태그 strip + entities 디코딩. ol 미지원/inline 스타일 미보존은 후속. → docs/task-results/TASK-20260426-013-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] 뉴스레터 갤러리 UI — LazyColumn → LazyVerticalGrid(2열, GridCells.Fixed(2)), `NewsletterCard` Composable 추출(빈 surfaceVariant 정사각 + 제목 maxLines=2). `+` 액션/BottomSheet/빈 상태/카드 탭 동작 모두 보존. ViewModel 미수정. → docs/task-results/TASK-20260426-014-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] 뉴스레터 응답 잘림 방지 — `maxOutputTokens=16384`(line 99), 프롬프트 rule 7 신설("</body></html>으로 끝낸다, 중간 끊기 금지"), 응답에 `</body>` 부재 시 `Log.w` + `</body></html>` append(line 107~109). companion object TAG 신설. → docs/task-results/TASK-20260426-015-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] 뉴스레터 화면 초기 자동 로드 — `NewsletterViewModel`에 VM-private `refreshNewsletters()` 신설(line 69), `loadNewsletters()` → `refreshNewsletters()` 호출(line 64). 실패 시 state.error. NOTE: Repository에 `refreshNewsletters` 미존재 → `getNewsletters()`를 runCatching으로 wrap (Brief 가정 정정, 의도와 동등). → docs/task-results/TASK-20260426-016-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] blocksToHtml NPE 픽스 — line 51 try-catch + line 71 null-safe(`properties?.entries?...`). 다른 3개 호출지(143/194/243)는 내부 null-safety로 자동 보호. 정식 block→HTML 재작성은 후속(getBlockChildren 정식 타입 + blocksToHtml 재작성). → docs/task-results/TASK-20260426-019-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] Notion Block → HTML 정식 변환 — `NotionBlocksResponse` 신설(NotionModels.kt:180), `getBlockChildren` 반환 타입 정정(NotionApi.kt:62), `blocksToHtml` block.type 분기 재작성(h1/h2/h3/p/ul-li, line 68~). 연속된 bulleted_list_item을 단일 `<ul>`로 그룹핑. TASK-019 try-catch 4 호출지 보존. TopicRepository/KeywordRepository 미영향. → docs/task-results/TASK-20260426-020-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] 뉴스레터 제목 prefix 제거 — line 132 `val title = if (titleSuffix.isNotBlank()) titleSuffix else "Daily Newsletter - $today"`. HTML body `<h1>` 헤더(line 78)는 그대로 유지. grep 2 hits PASS. → docs/task-results/TASK-20260426-017-implementer-result.md
- [2026-04-26 | implementer | 코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE] Newsletters Date에 시간 포함 — `OffsetDateTime.now().format(ISO_OFFSET_DATE_TIME)` (line 90). LocalDate import 제거. read paths 영향 없음. grep PASS. → docs/task-results/TASK-20260426-018-implementer-result.md
- [2026-04-17 | designer | 진행중] 공개 배포 전 시크릿/로깅 위생 → docs/plans/release-hardening.md *(파일 미작성)*

## 선언/해제 규칙

1. 공유 모듈 수정 전 위 목록에 한 줄 추가.
2. 해당 plan의 frontmatter에 `status`, `next_action` 갱신.
3. 작업 완료 시:
   - plan 체크박스 전부 `[x]` + `consumed_by`에 한 줄 추가 → `status: consumed` 전환.
   - 본 파일에서 해당 줄 제거, [archive.md](./status/archive.md)에 한 줄 이관.
