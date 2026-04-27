# Task Brief: 뉴스레터 본문 응답 잘림 방지

Task ID: TASK-20260426-015
Status: active

## Goal
Gemini가 뉴스레터 HTML을 생성하다가 중간(`<p>...오버헤드를 최소화\n</body></html>` 같은 형태)에서 끊겨 출력되는 문제를 줄인다. 토큰 한도 상향 + 프롬프트 강화 + 포맷 보호망.

## User-visible behavior
- 뉴스레터 본문이 모든 선정된 Topic을 끝까지 포함 (각 Topic의 핵심개념/상세설명/실용적 예시 3섹션 모두).
- 마지막에 `</body></html>`이 있으면 닫힘 정상; 없으면 백그라운드 보강(아래 §3-c).

## Root cause 추정
- 사용자 보고 출력은 첫 Topic의 두 번째 단락 도중에서 끊김 → Gemini 모델이 토큰 한도(8192) 또는 자체 판단으로 응답을 일찍 종료.
- A4 2장 분량의 한국어 HTML(스타일 + 3 Topic × 3 섹션)은 한국어 1자당 평균 1.5~2 토큰을 감안하면 8192로 빠듯.

## Scope
1. **`maxOutputTokens` 상향**: `NewsletterGenerationService.kt:94` `8192` → `16384`. 무료 티어 gemini-2.5-flash 단일 응답 상한 검토 — 16384는 안전.
2. **프롬프트 강화** (`NewsletterGenerationService.kt:50` 부근의 `val prompt = """..."""`):
   - "각 주제는 반드시 3섹션(핵심 개념 / 상세 설명 / 실용적 예시)을 모두 포함" 명시.
   - "응답은 반드시 `</body></html>` 으로 끝낸다" 명시.
   - "중간에 끊지 말 것. 끝까지 작성." 명시.
3. **포맷 보호망**:
   - `generateForSlot` 함수가 Gemini 응답을 받은 직후 검사:
     - `text.contains("</body>")` 가 false면 `Log.w(TAG, "Newsletter HTML truncated, finishReason=...")` 경고 + 사용자에게 보일 메시지를 응답에 추가하지는 않음(처리 로직은 그대로 진행).
     - `</body></html>`이 빠진 경우, 끝에 자동 append (단순 보강).
   - 이미 `Log.w`/유사 로그가 있다면 활용 (TASK-009 implementer가 NewsletterGenerationService 내에 Log.w 줄을 둔 것으로 추정 — 현재 상태 확인 후 활용 또는 신설).

## Out of Scope
- 페이지 분할 / streaming response — Gemini 무료 티어에서는 비활성. 후속.
- 자동 재시도 / 재호출 — 토큰 비용 우려. 후속.
- HTML→Notion 블록 변환은 TASK-013에서 처리.
- UI 변경 없음 (TASK-014).
- ViewModel 변경 없음.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/remote/gemini/*`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No prompt 의미 변경(추가만 허용).
- No `responseMimeType` 적용 (HTML 출력에 부적합).
- 모델 변경 금지 (`gemini-2.5-flash` 그대로).
- TASK-007/008/009/010/011/012 변경분 보존.

## Acceptance Criteria
- [ ] `maxOutputTokens = 16384`로 상향.
- [ ] 프롬프트에 "3섹션 모두 포함" + "</body></html>으로 끝낸다" 지시문 추가.
- [ ] 응답 검사 로직(`</body>` 부재 시 Log.w + append) 존재.
- [ ] grep `16384` in NewsletterGenerationService.kt → 1 hit.
- [ ] grep `</body>` in NewsletterGenerationService.kt → 1+ hit (프롬프트 문구 + 검사 로직).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "16384\|</body>\|truncated" app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개.
- 핵심 변경 줄 인용 (maxOutputTokens, 프롬프트 추가, 검사 로직).
- grep 결과.
- 빌드 결과(또는 SKIPPED).
- 사용자 다음 동작 1줄.

## STOP_AND_ESCALATE
- gemini-2.5-flash의 `maxOutputTokens` 단일 응답 상한이 16384 미만으로 의심되면 escalate.
- 응답 보강(append) 로직 위치를 결정하기 어려운 경우(예: 호출 함수가 너무 많이 갈라져 있음) escalate.
