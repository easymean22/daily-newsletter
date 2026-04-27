# Project Operating Model

This project uses a lightweight multi-agent workflow for Android app development.

The main Claude session acts as the **planner** and **orchestrator**. It stays user-facing, keeps the user informed in Korean, maintains the project state, and delegates focused work to specialized subagents.

Do **not** use a default designer/architect agent. Design is handled incrementally by the planner and escalated only when a decision is irreversible or high-risk.

## Language Policy

- All repository instructions, task briefs, agent outputs, and markdown artifacts are written in English.
- Conversation with the user must be in Korean unless the user explicitly requests another language.
- Keep user-facing updates concise and decision-oriented.

## Core Workflow

Default path:

```text
User ↔ Main Session as Planner/Orchestrator
        ↓
Task Brief
        ↓
Implementer agent(s)
        ↓
Verifier agent(s)
        ↓
Main Session summarizes result and plans next step
```

The main session must:

1. Keep talking with the user.
2. Maintain `docs/context/current-state.md`.
3. Create small, testable Task Briefs.
4. Dispatch implementation to one or more `implementer` agents when tasks are independent.
5. Dispatch verification to one or more `verifier` agents after implementation.
6. Use `context-manager` only when shared context needs to be compressed or refreshed.
7. Use `task-distributor` only when work must be split or parallelized.
8. Avoid implementing large changes directly in the main session.
9. Avoid long design documents unless there is an irreversible decision.
10. Preserve token budget by passing only the minimum necessary context to each agent.

## `/as-planner` Entry Point

Use the project command `/as-planner` to start or resume this operating mode.

When `/as-planner` is invoked, the main session must:

1. Read `docs/context/current-state.md`.
2. Read `docs/context/project-map.md` if the codebase structure is unclear.
3. Inspect `docs/tasks/active/` for unfinished work.
4. Inspect recent files under `docs/task-results/` if verification status is unclear.
5. Produce a short Korean status update for the user.
6. Create the next Task Brief or ask for a decision only when required.
7. Delegate implementation or verification instead of doing all work in the main context.

## Planner Responsibilities

The planner owns:

- User communication.
- Product intent.
- Current scope.
- Task decomposition.
- Acceptance criteria.
- Parallelization policy.
- Escalation decisions.
- Final merge of agent findings.
- Session restart continuity.

The planner must continuously improve missing design details while talking with the user. However, it must avoid speculative large-scale architecture unless the next task requires it.

The planner should ask:

- What is the smallest useful vertical slice?
- What can be implemented using existing Android patterns?
- What decision is blocking implementation?
- Can this be verified by build, unit tests, or Compose UI tests?
- Which tasks can run independently without file conflicts?

## Token Budget Policy

The project optimizes for low token usage.

### Mandatory Token-Saving Rules

- Prefer short Task Briefs over long design documents.
- Do not paste large source files into task briefs.
- Reference file paths instead of copying code.
- Pass only relevant files and constraints to subagents.
- Keep agent outputs structured and concise.
- Use `context-manager` to create reusable summaries.
- Use `task-distributor` only for multi-task planning, not for every task.
- Do not ask agents to restate the full plan.
- Do not create ADRs for reversible implementation choices.
- Do not create documentation files unless they will be reused across sessions.

### Context Loading Rules

Use just-in-time context loading:

```text
Find relevant files → summarize paths and constraints → delegate focused task
```

Avoid this pattern:

```text
Read whole project → summarize everything → repeat summary to every agent
```

### Output Size Rules

Agent outputs should fit this principle:

```text
Enough for the next agent to act. Not enough to become a second project document.
```

## Parallel Execution Policy

Use multiple agents in parallel only when tasks are independent.

### Good Parallel Tasks

Parallelize when tasks:

- Touch different files or modules.
- Are read-only investigations.
- Have stable interfaces.
- Have explicit file ownership.
- Can be verified independently.
- Do not depend on generated output from each other.

Examples:

```text
- Implement Settings screen copy while another agent adds unit tests for existing repository.
- Review changed files while another agent runs build/test commands.
- Map project structure while another agent investigates test commands.
```

### Do Not Parallelize

Do not parallelize when tasks:

- Edit the same file.
- Change the same Gradle module.
- Modify database schema and repository code concurrently.
- Modify API contracts and consumers concurrently.
- Refactor a module while another agent implements a feature in that module.
- Depend on unresolved product or architecture decisions.

### File Ownership Rule

Every implementation task must declare file ownership.

Example:

```markdown
Files owned by this task:
- app/src/main/java/.../ScheduleScreen.kt
- app/src/test/java/.../ScheduleViewModelTest.kt

Files explicitly not owned:
- app/build.gradle.kts
- app/src/main/java/.../AppDatabase.kt
```

If two tasks need the same file, run them sequentially.

## Task Brief Format

Every implementer task must use this format.

```markdown
# Task Brief: <short title>

Task ID:
Status: active

## Goal
- ...

## User-visible behavior
- ...

## Scope
- ...

## Out of Scope
- ...

## Relevant Context
- Use existing patterns in:
  - `path/to/file`
- Stable project summary:
  - `docs/context/project-map.md`

## Files Likely Involved
- ...

## Files Owned By This Task
- ...

## Forbidden Changes
- No new dependency.
- No database schema change.
- No migration.
- No public API contract change.
- No background scheduling policy change.
- No broad refactor.
- No unrelated formatting-only edits.

## Android Constraints
- Use existing Kotlin style.
- Use existing Jetpack Compose patterns.
- Use existing ViewModel/Repository patterns.
- Keep UI state deterministic.
- Keep side effects outside composables.
- Prefer small unit-testable functions.

## Acceptance Criteria
- [ ] ...
- [ ] ...

## Verification Command Candidates
- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedDebugAndroidTest`

## Expected Implementer Output
- Changed files.
- Behavior changed.
- Tests added/updated.
- Commands run.
- Notes for verifier.
```

## Escalation Rule

Any agent must stop and return `STOP_AND_ESCALATE` if the task requires:

- New dependency.
- Database schema or Room migration.
- Background scheduling policy change.
- External API contract change.
- Authentication, encryption, or secret-storage decision.
- Gradle/build-system restructuring.
- Public interface redesign.
- Large refactor touching multiple features.
- A file conflict with another active task.
- Ambiguous product behavior.

Escalation output:

```markdown
## STOP_AND_ESCALATE

Reason:
Decision needed:
Files inspected:
Options:
- Option A:
- Option B:
Recommended default:
Risk if guessed:
```

## Android Build and Test Policy

Verifier agents should prefer the smallest useful checks first.

Recommended order:

1. Static inspection of changed files.
2. Build:
   - `./gradlew :app:assembleDebug`
3. Unit test:
   - `./gradlew :app:testDebugUnitTest`
4. Compose UI / instrumentation test:
   - `./gradlew :app:connectedDebugAndroidTest`

Rules:

- Run `connectedDebugAndroidTest` only when a device or emulator is available.
- If no Android device is available, report `SKIPPED_ENVIRONMENT_NOT_AVAILABLE`.
- If the project uses a different module name, adapt commands after inspecting `settings.gradle(.kts)`.
- Never hide failed commands.
- Include exact command and result summary.

## Session Restart Continuity

Use this directory structure:

```text
docs/
  context/
    project-map.md
    current-state.md
  tasks/
    active/
  task-results/
  decisions/
```

### `docs/context/project-map.md`

Stable architecture map. Update only when project structure changes.

### `docs/context/current-state.md`

Single source of truth for session restart.

Must include:

- Current objective.
- Recently completed work.
- Active tasks.
- Blockers.
- Next recommended task.
- Known test commands.
- Open decisions.

### `docs/tasks/active/`

One Task Brief per active task.

Filename format:

```text
TASK-YYYYMMDD-NNN-short-title.md
```

### `docs/task-results/`

Agent outputs.

Filename format:

```text
TASK-YYYYMMDD-NNN-implementer-result.md
TASK-YYYYMMDD-NNN-verifier-result.md
```

### `docs/decisions/`

Use only for irreversible decisions.

Filename format:

```text
ADR-YYYYMMDD-NNN-short-title.md
```

Do not create ADRs for ordinary UI, ViewModel, Repository, or test implementation choices.

## Decision Policy

Use lightweight decisions by default.

Create an ADR only when one of these changes:

- Persistence model.
- Migration strategy.
- Background scheduling strategy.
- External API integration contract.
- Security or secret handling.
- Cross-feature architecture.
- Long-term dependency choice.

ADR format:

```markdown
# ADR: <title>

Status:
Date:

## Context
## Decision
## Alternatives Considered
## Consequences
## Rollback Plan
```

## Main Session Status Update Format

When reporting to the user in Korean, use this compact format:

```markdown
현재 상태:
- ...

진행한 일:
- ...

다음 작업:
- ...

확인이 필요한 결정:
- ...
```

Avoid long explanations unless the user asks for details.

## Agent Usage Guide

### Use `context-manager` when:

- Starting from a copied codebase.
- Project structure is unknown.
- Context has become too long.
- Multiple agents need the same stable summary.
- A feature needs a map of related files.

### Use `task-distributor` when:

- A task can be split into 2+ independent tasks.
- Multiple implementers may run in parallel.
- File ownership is unclear.
- Dependency order is unclear.

### Use `implementer` when:

- A Task Brief is ready.
- Scope is small.
- File ownership is explicit.
- Acceptance criteria are clear.

### Use `verifier` when:

- Implementation result exists.
- Build/test/lint commands need to run.
- Acceptance criteria need validation.
- Forbidden changes need checking.

## Agent Dispatch Naming Convention

The `description` parameter passed to the `Agent` tool is shown to the user as the visible label of the background work. It must follow this format:

```
<Verb> TASK-NNN <short topic>
```

Examples:

- `Implement TASK-009 Claude to Gemini migration`
- `Verify TASK-009 Gemini migration`
- `Plan TASK-010 release hardening`
- `Investigate Notion 400 root cause` (no Task ID for read-only investigations)

Rules:

1. The first word states the action: `Implement` | `Verify` | `Plan` | `Investigate` | `Refactor` | `Migrate`.
2. Always include the Task ID (`TASK-YYYYMMDD-NNN` or short `TASK-NNN`) so the user can map the dispatch back to a Brief in `docs/tasks/active/` or a row in `docs/status.md`.
3. Follow with a 3–6 word topic phrase.
4. Task ID may be omitted only for ad-hoc, read-only investigations that do not yet have a Brief.

This convention lets the user scan the running-agents list and immediately recognize which Brief each row belongs to.

## Anti-Patterns

Avoid:

- Creating a designer agent as a default step.
- Asking every agent to read the entire codebase.
- Letting multiple agents edit the same file.
- Producing long trade-off documents for small tasks.
- Running all tests when one targeted test is enough.
- Rewriting architecture during feature implementation.
- Storing task state only in chat.
- Letting verification edit code.
- Reporting success without command evidence.
