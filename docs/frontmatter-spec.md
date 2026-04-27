---
updated: 2026-04-19
status: accepted
owner: harness
summary: "모든 docs 파일이 따르는 frontmatter 표준 + status lifecycle"
---

# Frontmatter 표준

`docs/specs/*.md`, `docs/plans/**/*.md`, `docs/decisions/*.md` 모든 파일은 상단에 아래 YAML 블록을 둔다. 복기 세션은 이 frontmatter만으로 1차 스캔하고, 본문은 선택된 문서만 로드한다.

## 형식

```yaml
---
updated: YYYY-MM-DD            # 마지막 의미 있는 변경일
status: draft | confirmed | accepted | in-progress | consumed | superseded | archived
owner: planner | designer | implementer | harness
summary: "한 줄 WHAT"            # 본문을 안 열어도 파악되는 요약
consumed_by:                   # 이 파일이 반영되어 끝난 작업들 (없으면 [] 또는 생략)
  - "YYYY-MM-DD <role>: <작업 한 줄>"
next_action: "누가 무엇을"       # 없으면 생략
refs:                          # 의존·관련 문서 (없으면 생략)
  - docs/specs/mvp.md
supersedes:                    # ADR 전용 — 이 ADR이 대체한 이전 ADR (없으면 생략)
  - docs/decisions/0003-old.md
---
```

## status 의미

| 값 | 의미 | 복기 로딩 (기본) |
|---|---|---|
| `draft` | planner 초안, 사용자 컨펌 전 | 포함 |
| `confirmed` | spec 컨펌 완료 (designer 핸드오프 가능) | 포함 |
| `accepted` | designer 플랜/ADR 확정 (implementer 진입 가능) | 포함 |
| `in-progress` | implementer가 단계별로 작업 중 | 포함 |
| `consumed` | 계획된 작업이 전부 반영되어 종료 | **제외** |
| `superseded` | 후속 문서로 대체됨 (ADR 이력 보존용) | 제외 |
| `archived` | 의도적으로 보관만 | 제외 |

추가 한정자:
- `blocked`, `abandoned` 는 현재 쓰지 않는다. 필요해지면 본 스펙에 추가 후 사용.

## consumed_by 작성 규칙

- implementer가 단계를 완료해 커밋에 반영되면 `consumed_by`에 한 줄 추가.
- 형식: `"YYYY-MM-DD <role>: <작업명 + 핵심 산출물>"`.
- 플랜의 체크박스 전부가 `[x]` 되고 consumed_by가 그 전부를 커버하면 `status: consumed`로 전환.
- consumed_by가 일부만 찼으면 `status: in-progress` 유지.

## 디렉토리로 분할된 플랜 (sub-plan)

`docs/plans/<feature>/` 형태의 디렉토리 플랜은:

- **README.md**: 위 frontmatter 전체 필드 보유. 플랜의 대표.
- **형제 파일 (`01-backend.md` 등)**: 최소 frontmatter만.
  ```yaml
  ---
  updated: YYYY-MM-DD
  summary: "한 줄 — 이 섹션이 담는 것"
  parent: ../README.md
  ---
  ```

형제 파일은 status·consumed_by를 중복 보유하지 않는다 — 플랜 레벨은 README 하나만.

## 복기 1차 스캔 팁

```
Grep: "^status: \(draft\|confirmed\|accepted\|in-progress\)" → 활성 문서 목록
Grep: "^updated:" → 변경 순 정렬
```

세션이 본문을 로드할지 여부는 frontmatter만으로 판단.
