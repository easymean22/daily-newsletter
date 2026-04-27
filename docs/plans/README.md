---
updated: 2026-04-19
status: accepted
owner: harness
summary: "plan 작성 규약 — 소형 플랜은 단일 파일, 대형은 디렉토리 분할"
refs:
  - docs/frontmatter-spec.md
---

# Plans

`designer` 역할이 작성한 **기술 설계**와 단계별 구현 플랜. 모든 plan은 [frontmatter-spec.md](../frontmatter-spec.md) 준수.

## 파일 명명 / 구조 선택

- **소형 플랜 (섹션 1~2개, <300줄 권장)**: 단일 파일 `<기능명-kebab-case>.md` 또는 `NNNN-<제목>.md`.
  - 예: `docs/plans/0002-git-init-and-gitignore.md`.
- **대형 플랜 (섹션 3개 이상, 또는 >500줄)**: 디렉토리로 분할.
  - `docs/plans/<기능명>/README.md` — 전체 frontmatter + 요약 + 체크박스 진행률 + 파일 지도 + 미해결 사용자 확인.
  - `docs/plans/<기능명>/NN-<섹션>.md` — 섹션별 파일 (예: `01-background.md`, `02-backend.md`, `03-ui.md`, `04-infra.md`, `05-checklist.md`).
  - 형제 파일의 frontmatter는 최소 필드만: `updated`, `summary`, `parent: ./README.md`.

## 단일 파일 구조 예시

```markdown
---
updated: YYYY-MM-DD
status: draft | accepted | in-progress | consumed | archived
owner: designer
summary: "한 줄 WHAT"
consumed_by: []
next_action: "누가 무엇을"
refs:
  - docs/specs/<파일>.md
---

# <기능명>

## 배경 / 관련 요구사항
spec의 어느 부분을 다루는지.

## 백엔드 설계 (해당 시)
### 대안 (2~3개)
### Trade-off (의도 부합도 > 복잡도 > 위험 > 배포 영향)
### 추천안 + 근거

## UI 설계 (해당 시)
### 대안 (2~3개)
### Trade-off (의도 부합도 > 단순성 > 접근성 > 입력 부담 > 오류 복구)
### 추천안 + 근거
### 와이어프레임 (텍스트 스케치)
### 컴포넌트 트리 / 상태 흐름
### 에러·빈 상태 ← 필수

## 크로스커팅/인프라 (해당 시)

## 단계별 플랜 (implementer 체크리스트)
- [ ] 1단계: ...

## 관련 결정
- ADR-XXXX
```

## implementer 갱신 규칙

- 체크박스를 진행에 따라 `[x]`로 전환.
- 단계 완료 커밋마다 README frontmatter `consumed_by`에 한 줄 추가.
- 전체 단계 완료 시 `status: consumed`로 전환, `docs/status.md` 해당 라인을 `docs/status/archive.md`로 이관.
