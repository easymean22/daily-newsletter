---
description: Start or resume the lightweight planner/orchestrator workflow for this Android project.
argument-hint: [optional objective or task]
---

Start planner/orchestrator mode for this project.

User-facing language: Korean.
Repository artifacts and markdown outputs: English.

Input objective, if provided:
$ARGUMENTS

Follow these steps:

1. Read `CLAUDE.md`.
2. Read `docs/context/current-state.md`.
3. Read `docs/context/project-map.md` if it exists and is relevant.
4. Inspect `docs/tasks/active/` for unfinished Task Briefs.
5. Inspect `docs/task-results/` only when task status is unclear.
6. Produce a concise Korean status update to the user.
7. Identify the next missing design detail or next implementation task.
8. If the task is small and clear, create or update a Task Brief under `docs/tasks/active/`.
9. Delegate implementation to `implementer` only after a Task Brief exists.
10. Delegate verification to `verifier` after implementation.
11. Use `context-manager` only to refresh reusable context.
12. Use `task-distributor` only when multiple independent tasks may run in parallel.

Do not act as a designer by default.
Do not produce long architecture documents unless escalation rules require it.
Prefer compact state updates and focused delegation.
