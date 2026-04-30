#!/bin/bash
# PreToolUse hook: block Agent calls to subagent_type=implementer
# unless the prompt references an existing Task Brief at docs/tasks/active/TASK-*.md.
# Stdin: JSON of tool input. Stderr: reason. Exit 1 = block.

input=$(cat)
sub=$(printf '%s' "$input" | grep -oE '"subagent_type"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')

[ "$sub" = "implementer" ] || exit 0

prompt=$(printf '%s' "$input" | grep -oE 'docs/tasks/active/TASK-[A-Za-z0-9_-]+\.md' | head -1)

if [ -z "$prompt" ]; then
  echo "spec-writer hook: implementer dispatch blocked — prompt does not reference docs/tasks/active/TASK-*.md. Call spec-writer first." >&2
  exit 1
fi

if [ ! -f "$prompt" ]; then
  echo "spec-writer hook: implementer dispatch blocked — Brief not found at $prompt. Call spec-writer first." >&2
  exit 1
fi

exit 0
