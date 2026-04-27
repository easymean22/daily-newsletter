---
updated: 2026-04-19
summary: "배경 — spec §3 사용자 행동 트리거 재정의 + 기존 코드 이슈 2건"
parent: ./README.md
---

# 배경 / 관련 요구사항

spec §3 (재정의된 파이프라인 구동축):

> **주제 생성은 사용자 행동이 트리거**한다. 즉 키워드 입력 직후, 또는 수동 주제 생성 버튼, 또는 사용자가 직접 주제를 작성해서 넣는 경로로 Topics DB에 주제가 쌓인다.

## 발견된 기존 코드 이슈 (이 플랜이 함께 수리)

본 플랜의 코드 읽기 단계에서 다음 두 가지 잠재 버그 / 의도-코드 불일치를 발견했다. **수리 책임은 본 플랜 scope에 포함**한다 (#2의 "기존 `TopicSelectionService` / `DailyTopicWorker`의 처분 결정" 항목과 직결).

1. **`TopicSelectionService.setTopicRepository()`가 코드 어디에서도 호출되지 않음.** 따라서 현재 시점에 `DailyTopicWorker`가 발동하면 `IllegalStateException("TopicRepository not set")`이 던져지고 retry 3회 후 failure로 끝난다. spec의 "한 번도 끝까지 안 돌려봤다"는 핵심 불안을 코드 레벨에서 확인. 본 플랜은 이 setter 패턴 자체를 **걷어내는** 방향으로 설계 (자동 경로의 구조적 단순화 부산물).
2. **`DailyTopicWorker` (T−2h 스케줄)는 spec §3에서 "주제 생성은 사용자 행동이 트리거"로 재정의되면서 의도 충돌**. 본 플랜이 이 워커의 처분(제거 / 보조 유지)을 결정한다.
