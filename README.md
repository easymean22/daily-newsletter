# Claude Agent Workflow Template for Android

This template configures a lightweight Claude Code workflow:

```text
Main session: planner/orchestrator
Subagents: implementer, verifier, context-manager, task-distributor
```

Start or resume work with:

```text
/as-planner
```

## Directory Structure

```text
CLAUDE.md
.claude/
  commands/
    as-planner.md
  agents/
    implementer.md
    verifier.md
    context-manager.md
    task-distributor.md
docs/
  context/
    project-map.md
    current-state.md
  tasks/
    active/
  task-results/
  decisions/
```

## Intended Use

- Android app development.
- Korean conversation with the user.
- English markdown artifacts.
- Token-efficient context management.
- Parallel implementer/verifier usage only for independent tasks.
- Build, unit test, and Compose UI test verification.

## Setup

Copy these files into the root of a new Claude Code project.

Then copy your existing Android source code into the project, excluding old `.claude/`, stale docs, build outputs, `.gradle/`, and local machine files.
