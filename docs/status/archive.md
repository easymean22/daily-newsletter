---
updated: 2026-04-19
status: archived
owner: harness
summary: "종료된 작업 이력 + 하네스 변경 로그 (복기 시 미로드)"
---

# Status Archive

`docs/status.md`에서 종료된 작업을 이관 보관. 복기 세션은 이 파일을 기본 로딩하지 않는다 — 과거 맥락이 필요할 때만 참조.

## 종료된 작업

- [2026-04-19 | harness] docs 인덱스/요약 레이어 도입 완료 — `docs/INDEX.md` + `docs/frontmatter-spec.md` 신설, 대형 플랜 3개(newsletter-shelf / tag-system / topic-generation-paths) 디렉토리 분할 (각 README + 섹션 파일), 모든 spec/plan/decision에 frontmatter 표준 적용, `docs/status.md` pointer-only로 슬림화 + `docs/status/archive.md` 신설, `/as-planner|designer|implementer` 복기 절차를 frontmatter 스캔 방식으로 재작성.
- [2026-04-19 | implementer] tag-system 1~2단계 완료 — NotionModels multi_select 3종 + TagNormalizer + JVM 테스트 인프라 부트스트랩(junit/mockk/coroutines-test/turbine) + TagNormalizerTest 12/12 PASS — assembleDebug/testDebugUnitTest BUILD SUCCESSFUL. 5단계(listAvailableTagNames) 포함 여부는 핸드오프 #6으로 미룸(2026-04-19 사용자 확정).
- [2026-04-19 | planner] MVP spec 능동 발굴 라운드 완료 — docs/specs/mvp.md. 14개 의도 질문(Q1~Q14) + Q5 재해석 전부 해소. 주요 확정: 자동 주제 생성 개수=Claude 판단, 과거 주제 회피=**프린트된 것만**(pending 중복 허용), 직접 작성 주제 태그=Claude 자동 부여, 주제 1건 태그 다중, 요일=**(시각, [(태그,장수), ...])** 슬롯 묶음, **뉴스레터 진열대 모델** — 풀 비면 즉석 **2개 생성(1 사용, 1 비축)**, 1 뉴스레터=Claude N개 주제 엮음, 요일별 on/off 토글, 슬롯 일부 실패 시 격리.
- [2026-04-19 | implementer] 0002 git init + .gitignore 실행 — 루트 `.gitignore`/`.gitattributes` + git 저장소 초기화. 커밋 2개 (`0d8863d`, `1ec1c28`). DoD 4개 모두 충족. 원격 `origin` 미연결 — 사용자 확인 필요.
- [2026-04-17 | designer] git 저장소 초기화 + .gitignore 설계 — docs/plans/0002-git-init-and-gitignore.md, docs/decisions/0002-git-repo-and-secret-isolation.md. implementer 실행 완료.
- [2026-04-19 | planner] MVP spec 사용자 컨펌 완료 — docs/specs/mvp.md (draft → confirmed). 파이프라인 구동축을 스케줄 기반에서 **사용자 행동 + 프린트 시점 lazy**로 재정의. **태그 시스템을 1급 개념으로 편입**. 주제 생성 3경로. 요일별 프린트 설정=시각+태그+페이지. 엣지 케이스 정책(프린트 실패 당일 1회, pending 0개 스킵+토글, 생성 실패 알림만) 확정.
- [2026-04-19 | planner] 커밋/푸시 규칙 수립 — CLAUDE.md. origin URL 등록.

## 하네스 변경 이력

- **2026-04-19**: docs 인덱스/요약 레이어 도입 — 대형 플랜 3개(newsletter-shelf-model, tag-system, topic-generation-paths) 디렉토리로 분할. 모든 spec/plan/decision에 frontmatter 표준 적용. status.md를 pointer-only 구조로 슬림화, 종료 이력은 본 파일로 이관. `/as-planner|designer|implementer` 복기 절차를 frontmatter 스캔 방식으로 재작성.
- **2026-04-19**: 하네스 단순화(ADR-0001 v3) — `backend-designer`·`ui-designer`·`tester` 역할 통합 → 단일 `designer`. `docs/plans/backend/` → `docs/plans/` 평탄화.
- **2026-04-17**: 하네스 재설계(ADR-0001 v2) — `architect` → `backend-designer`로 이관. `docs/plans/0002-git-init-and-gitignore.md`는 `docs/plans/backend/` 하위로 이동.
- **2026-04-19**: planner가 커밋/푸시 규칙을 CLAUDE.md에 박고, MVP spec 초안(`docs/specs/mvp.md`) 작성.
