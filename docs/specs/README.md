---
updated: 2026-04-19
status: accepted
owner: harness
summary: "spec 작성 규약"
refs:
  - docs/frontmatter-spec.md
---

# Specs

`planner` 역할이 작성한 **사용자 의도·요구사항** 문서. 기술 설계는 여기에 없다 — 그건 `docs/plans/`. 모든 spec은 [frontmatter-spec.md](../frontmatter-spec.md) 준수.

## 파일 명명

`<기능명-kebab-case>.md` — 예: `font-customization.md`, `offline-queue.md`.

## 파일 구조

```markdown
---
updated: YYYY-MM-DD
status: draft | confirmed | consumed | archived
owner: planner
summary: "한 줄 WHAT"
consumed_by: []
next_action: "designer가 핸드오프 N개를 플랜으로"
---

# <기능명>

## 사용자 가치 (WHY)
누가 왜 이걸 원하나. 이 기능이 없으면 무엇이 불편한가.

## 기능 요구 (WHAT)
사용자 관점에서 이 기능이 하는 일. 기술 용어 지양.

## 엣지 케이스 / 실패 모드
- ...

## 비범위 (NOT in scope)
이번에 의도적으로 포함 안 하는 것 + 이유.

## 사용자에게 확인 대기 중 (있을 때만)
planner가 아직 답을 받지 못한 의도 질문들.

## 디자이너 핸드오프
- designer 검토 필요 (백엔드/파이프라인): <항목들>
- designer 검토 필요 (UI/문구): <항목들>
- designer 검토 필요 (크로스커팅/인프라): <항목들>
```

## 흐름

```
사용자 ⇄ planner  →  docs/specs/<기능>.md
                        │
                        ▼
                    designer  →  docs/plans/<기능>.md
                                    │
                                    ▼
                         implementer(코드+테스트) → reviewer
```
