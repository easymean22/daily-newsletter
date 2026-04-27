---
updated: 2026-04-26
status: accepted
owner: planner
summary: "docs 루트 인덱스 — 영역별 파일 포인터"
---

# Docs Index

프로젝트 설계/기획/구현 문서의 최상위 인덱스. 모든 파일은 상단 frontmatter로 상태를 표시한다. 규약: [frontmatter-spec.md](./frontmatter-spec.md).

## Specs (사용자 의도)

- [specs/README.md](./specs/README.md) — spec 작성 규약
- [specs/mvp.md](./specs/mvp.md) — MVP 한 줄 정의, 파이프라인 구동축, 디자이너 핸드오프 8개 **(confirmed)**

## Plans (기술 설계)

- [plans/README.md](./plans/README.md) — plan 작성 규약
- [plans/newsletter-shelf/](./plans/newsletter-shelf/README.md) — 진열대 + lazy 2개 생성 **(review)**
- [plans/tag-system/](./plans/tag-system/README.md) — 태그 1급 편입 **(in-progress, 1~2단계 완료)**
- [plans/topic-generation-paths/](./plans/topic-generation-paths/README.md) — 주제 생성 3경로 **(review)**
- [plans/weekday-print-slots/](./plans/weekday-print-slots/README.md) — 요일별 프린트 설정 UI **(accepted)**
- [plans/e2e-rehearsal/](./plans/e2e-rehearsal/README.md) — 1일 E2E 리허설 (핸드오프 #8) **(draft)**

## Decisions (ADR)

- [decisions/README.md](./decisions/README.md) — ADR 작성 규약
- [decisions/0003-tag-system-data-model.md](./decisions/0003-tag-system-data-model.md) — 태그 multi_select **(accepted v2)**
- [decisions/0005-newsletter-shelf-lazy-generation.md](./decisions/0005-newsletter-shelf-lazy-generation.md) — 진열대·lazy **(proposed)**
- [decisions/0006-weekday-print-scheduling.md](./decisions/0006-weekday-print-scheduling.md) — 요일별 프린트 스케줄링 **(accepted)**

## Status

- [status.md](./status.md) — **현재 진행 중** 작업 선언 (얇음)
- [status/archive.md](./status/archive.md) — 종료된 작업 이력 (복기 시 미로드)

## Context (planner working snapshot)

- [context/current-state.md](./context/current-state.md) — 구현/미구현/리스크 스냅샷
- [context/project-map.md](./context/project-map.md) — 코드베이스 패키지/클래스 지도

## 복기 힌트

- `status: consumed | superseded | archived` 는 기본 복기에서 제외.
- 세션 시작 시 활성 문서만 frontmatter 스캔 → 본문은 선택 로딩.
