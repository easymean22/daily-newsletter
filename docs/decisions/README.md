---
updated: 2026-04-19
status: accepted
owner: harness
summary: "ADR 작성 규약"
---

# Architecture Decision Records (ADR)

되돌리기 어려운 구조적 결정을 기록한다 (글로벌 CLAUDE.md 규칙). 모든 ADR은 [frontmatter-spec.md](../frontmatter-spec.md) 준수.

## 파일 명명

`NNNN-<제목-kebab-case>.md` — 예: `0001-harness-role-based-agents.md`

번호는 증가만 한다 (결정을 뒤집어도 번호 재사용 금지 — 새 ADR이 이전 ADR을 `superseded`로 표시).

## 파일 구조

```markdown
---
updated: YYYY-MM-DD
status: draft | accepted | superseded | archived
owner: designer | harness
summary: "한 줄 결정"
supersedes:                      # 이 ADR이 대체한 이전 ADR (있으면)
  - docs/decisions/NNNN-old.md
refs:
  - docs/plans/<관련 플랜>.md
---

# ADR-NNNN: <제목>

**상태:** proposed | accepted | superseded by ADR-XXXX | deprecated
**날짜:** YYYY-MM-DD
**결정자:** <역할 또는 사람>

## 컨텍스트
무엇이 문제였나. 왜 지금 결정하나.

## 결정
무엇을 선택했나.

## 대안
기각한 안들 + 기각 이유.

## 영향
코드/프로세스/배포에 어떤 변화가 있나.
```
