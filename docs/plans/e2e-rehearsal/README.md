---
updated: 2026-04-26
status: review
owner: planner
summary: "1일 E2E 리허설 1차 이터레이션 (MVP 핸드오프 #8 일부) — Scope A: 슬롯/스케줄러/IPP 모두 비범위. 수동 뉴스레터 생성 버튼 → Notion에 문서 1건이 만들어지는 것까지만 검증."
consumed_by:
  - "2026-04-26 planner: 1차 작성 (draft → review)"
  - "2026-04-26 사용자 응답 Q1=명시 컨펌 / Q2=자동 경로 / Q3=잔류 두고 진행 / Q4=일시 비활성 / Q5=scope A이므로 시간 트리거·IPP 모두 무관"
  - "2026-04-26 사용자 응답: scope 축소 — Scope A 채택 (인쇄 + 슬롯/스케줄러 모두 제외, 문서 생성까지만)"
next_action: "선행 plan(tag-system 3·4 완료 / topic-generation-paths 1·3·5·6 / newsletter-shelf 1·2·3·4·6·9·10) 단계 완료 후 사용자 명시 컨펌 → 1차 시도"
refs:
  - docs/specs/mvp.md
  - docs/plans/tag-system/README.md
  - docs/plans/topic-generation-paths/README.md
  - docs/plans/newsletter-shelf/README.md
  - docs/decisions/0003-tag-system-data-model.md
  - docs/decisions/0005-newsletter-shelf-lazy-generation.md
---

# 1일 E2E 리허설 — 1차 이터레이션 / Scope A (MVP 핸드오프 #8 일부)

> **본 이터레이션 범위 (2026-04-26 사용자 확정 — Scope A):**
>
> - **수동 뉴스레터 생성 버튼**으로 Notion Newsletters DB에 문서 1건이 `generated` 상태로 추가되고 HTML 본문을 갖는 것까지가 acceptance.
> - **슬롯/스케줄러/Worker fire/IPP 인쇄** 전부 본 이터레이션 비범위 — 다음 이터레이션(들)으로 이월.
> - 단일 슬롯·라운드 1 어휘 규약은 그대로 적용.

## 1. 본 이터레이션 시나리오 (1~6단계)

| # | 단계 | 사용자 동작 | 검증 포인트 | 선행 plan |
|---|---|---|---|---|
| 1 | Notion 연동 초기 설정 | 앱 첫 실행 → Notion API key + parent page ID 입력 → "DB 생성" | Keywords / Topics / Newsletters 3개 DB 생성 + 각 DB에 `Tags multi_select` + 시드 옵션 `모든주제` 1개 | tag-system 3 ✅ |
| 2 | API 키 입력 | Settings → Claude API key 입력. **프린터 IP·요일별 슬롯·시각 모두 입력하지 않아도 됨** (본 이터레이션은 스케줄러/인쇄 비범위). | Claude key 만 `settings` k/v에 저장 | (없음) |
| 3 | 키워드 등록 | 키워드 화면에서 1개 이상 등록 (예: `eBPF`) | Keywords DB에 페이지 생성 + `Tags = [모든주제]` 기본 부여 | tag-system 4 ✅, topic-generation-paths 4·6 |
| 4 | **자동 경로**(Q2 채택)로 주제 생성 | 키워드 등록 직후 자동 생성 트리거 | Topics DB에 1건 이상 + `Tags = [모든주제]` 단독 + Claude 응답에 태그 필드 없음 | topic-generation-paths 1·3·5 |
| 5 | Topic 수동 태그 추가/제거 확인 | 1개 Topic의 Notion 페이지 또는 앱 UI에서 태그 추가 후 제거 | `모든주제`는 invariant 보장으로 삭제 불가, 다른 태그는 자유 편집 | tag-system 4 ✅ |
| 6 | **수동 뉴스레터 생성 버튼** → Notion에 문서 1건 추가 | NewsletterScreen에서 "수동 생성" 버튼 탭 → 슬롯 매개(태그 = `모든주제`, 장수 = 사용자 지정) 입력 → 즉시 호출 | Newsletters DB에 새 페이지 1건이 `generated` 상태로 추가 + 페이지 본문에 HTML 콘텐츠 존재 + 그 페이지가 사용한 Topic은 `consumed` 처리 | newsletter-shelf 1·2·3·4·6·9·10 |
| ~~7~~ | ~~슬롯 시각 도달 lazy 보충~~ | **본 이터레이션 비범위 — 다음 이터레이션 B(시간축)로 이월** | — | (이월) weekday-print-slots 전체 + newsletter-shelf 5 |
| ~~8~~ | ~~Canon G3910 종이 출력~~ | **본 이터레이션 비범위 — 다음 이터레이션 C(IPP) 또는 #7 IPP E2E plan으로 이월** | — | (이월) newsletter-shelf 7 + IPP plan |

본 이터레이션의 합격선 = step 6까지 통과.

## 2. 본 이터레이션 진입 게이트 (선행 plan 단계)

- **tag-system** — 3·4단계 ✅ **완료**(TASK-20260426-003).
- **topic-generation-paths** — 1·3·4·5단계(`ClaudeTopicSuggester` + ViewModel orchestration + 자동 경로 + `KeywordRepository.addKeyword` 시그니처). 6·7단계 UI 중 **키워드 화면(6단계)만 필요** — 주제 화면(7단계)의 수동/직접 진입점은 자동 경로 검증에 불필요. 8단계 DailyTopicWorker 제거는 본 이터레이션 직전에라도 OK.
- **newsletter-shelf** — 1·2·3·4·6·9·10단계.
  - 1·2·3: Notion 모델·`NewsletterRepository.saveNewsletter` 시그니처·`TopicRepository.findPendingTopicsByTag`·`markTopicsConsumed`.
  - 4: `generateForSlot(tag, pageCount)` — Claude로 HTML 생성 + 저장 + consumed 전이.
  - 6: NotificationHelper 분리 (수동 생성 후 토스트/알림용 — 없으면 스킵 가능).
  - 9: NewsletterViewModel에 수동 생성 orchestration.
  - 10: NewsletterScreen에 수동 생성 BottomSheet/버튼.
  - **5(`PrintOrchestrator` 신설)·7(`PrintWorker` IPP 호출)·8(`NewsletterWorker` 제거) 비범위** — 다음 이터레이션.
- **weekday-print-slots** — **전체 비범위**. UI도 스케줄러도 본 이터레이션에서 사용하지 않음.

## 3. 실패 롤백 매핑

| 막힌 단계 | 증상 | 복귀할 plan / spec 절 |
|---|---|---|
| 1 | DB 생성 실패 (token/권한/스키마) | tag-system 3 + spec §6 |
| 3 | 키워드 저장 후 Topics DB에 변화 없음 (자동 경로 미동작) | topic-generation-paths 4·5 |
| 4 | Claude 호출 실패 / 빈 응답 / 잘못된 JSON | topic-generation-paths 1 + spec §6 |
| 5 | invariant 깨짐 (`모든주제`가 빠진 Topic 발생) | tag-system 4 (`TopicRepository.saveTopic` + `ensureFreeTopicTag`) |
| 6a | 수동 생성 버튼이 안 보임 / 동작 안 함 | newsletter-shelf 9·10 (UI orchestration) |
| 6b | `generateForSlot` Claude 호출 실패 / HTML 빈 본문 | newsletter-shelf 4 + spec §6 |
| 6c | 생성은 되었으나 `consumed` 전이 안 됨 | newsletter-shelf 4 (`generateForSlot` 직후 `markTopicsConsumed`) + ADR-0005 §결정 4 |
| 6d | 생성은 되었으나 Notion 페이지에 HTML이 단일 paragraph로만 들어감 (current-state.md §3 #7 위험 재발) | newsletter-shelf 4 — Notion rich_text 2000자 분할 처리 추가 검토 |

step 7·8 관련 실패는 본 이터레이션 검증 대상이 아님.

## 4. 운영 규칙

- **수동 1회 시도 — 자동화 갈음 X** (spec §3).
- **CleanupWorker 처분**(Q4 채택: 일시 비활성) — Scope A에서 스케줄러 자체를 사용하지 않으므로 사실상 불필요. 단 `WorkScheduler.scheduleAll()`이 호출되면 다른 무용 work도 함께 enqueue되므로 **`scheduleAll()` 자체를 본 이터레이션에서 호출하지 않는** 것을 권장. (구체 방법: `MainActivity` / `DailyNewsletterApp` 초기화 코드의 `scheduleAll()` 호출을 임시 주석. 본 plan은 권장만 명시 — 코드 변경은 담당 implementer task에서.)
- **리허설 1차 시도 시점 = 사용자 명시 컨펌**(Q1 채택). planner는 status.md를 보고 §2 게이트가 충족됐음을 보고 → 사용자가 "1차 시도" 발화 → 사용자가 단말에서 시도.
- **첫 시도 데이터 정리**(Q3 채택): 잔류 두고 진행. 단 `findUnprintedNewsletterByTag`는 `generated` 페이지를 잡으므로, 매 시도 전에 Notion에서 직전 `generated` 페이지를 수동 trash해야 다음 자동 경로가 깨끗해짐. 단 본 이터레이션은 자동 경로 lazy를 안 쓰므로 이 회피책은 다음 이터레이션 운영 규칙으로 이월.
- **단말 도즈 회피**(Q5): 본 이터레이션은 시간 트리거·IPP 모두 비범위이므로 도즈/Worker fire 신뢰성 검증이 무관. 사용자가 화면을 켜고 직접 버튼을 누르므로 도즈 모드 영향 없음.
- **본 이터레이션 1회 통과 후** = 다음 이터레이션 trigger. 다음 이터레이션 후보:
  - **이터레이션 B**: Scope A에 **시간축**(슬롯/스케줄러/Worker fire) 추가. 핵심 검증 = 슬롯 시각에 `generateForSlot` 자동 호출까지. IPP는 여전히 제외.
  - **이터레이션 C**: 이터레이션 B에 **IPP 인쇄** 추가 (Canon G3910 실기 검증). 본 이터레이션 + B + C 합치면 spec §4 1~8단계 전부 커버.

## 5. 사용자 확인 5개 — 모두 해소 (2026-04-26)

1. ~~**리허설 1차 시도 시점**~~ → **Q1 채택: 사용자 명시 컨펌**.
2. ~~**1차로 검증할 주제 생성 경로**~~ → **Q2 채택: (a) 자동 경로**.
3. ~~**1차 실패 시 데이터 정리**~~ → **Q3 채택: 잔류 두고 진행**. (단 Scope A는 자동 lazy 미사용이라 영향 적음.)
4. ~~**CleanupWorker 처분**~~ → **Q4 채택: 일시 비활성** (Scope A에서는 `scheduleAll()` 자체를 호출 안 하는 것이 더 깔끔).
5. ~~**신뢰성 검증 깊이**~~ → **Scope A이므로 무관**(사용자 직접 버튼 → 도즈/Worker fire 영향 없음). 시간 트리거 신뢰성은 이터레이션 B, IPP 신뢰성은 이터레이션 C로 이월.

## 6. 본 plan이 의도적으로 결정하지 않는 것

- **시간 트리거 (슬롯·스케줄러·Worker fire)** — 이터레이션 B로 이월.
- **IPP 인쇄 (Canon G3910 실기)** — 이터레이션 C 또는 #7 IPP E2E plan(미작성).
- **Notion DB 리셋 / 재 setup 자동화** — §6 #3 spec 후보(미작성).
- **release-hardening** — §6 #4 plan 후보(미작성).
- **다중 슬롯 리허설** — 공개 배포 마일스톤.
- **CleanupWorker 영구 처분** — 본 plan은 "본 이터레이션 동안 비활성"만 결정.
- **자동 lazy 보충 회피책 (Notion 잔류 페이지 수동 trash)** — 이터레이션 B 운영 규칙으로 이월.

## 7. 진행 상태

- [x] 1차: 본 plan 사용자 확인 5개 해소 + status `draft → review`. (2026-04-26)
- [x] 2차: 선행 plan §2 게이트 단계 완료 (2026-04-26). **현재**: tag-system 3·4 완료(TASK-003) + topic-generation-paths 1·3·4·5·6 완료(TASK-004) + newsletter-shelf 1·2·3·4·6·9·10 완료(TASK-005). 빌드는 모든 task에서 `SKIPPED_ENVIRONMENT_NOT_AVAILABLE` — 사용자 단말의 Android Studio 빌드 시 자연 검증됨.
- [ ] 3차: 본 이터레이션 1차 시도 — 사용자 본인 (명시 컨펌 후).
- [ ] 4차: 결과 기록(성공/실패 단계 + 복귀 plan) → 본 README § 8 "Findings".
- [ ] 5차: 1회 통과 시 status `review → accepted` + 이터레이션 B 신설 검토.

## 8. Findings (1차 시도 결과 기록 자리)

_(1차 시도 후 채워짐.)_
