# Beads Workflow

Этот документ описывает фактическое состояние Beads в `EDT-MCP`.

## Current Mode

- Prefix: `EDTMCP`
- Backend: Dolt
- Current local setup uses `.beads/metadata.json` with direct/server-backed Dolt mode

Практический вывод: Beads уже пригоден как локальная execution queue на этой машине, но shared issue history ещё не оформлен как явно повторяемый repo-tracked workflow.

## What Is Durable Today

- Durable shared planning truth: `openspec/project.md`, `openspec/specs/*`, `openspec/changes/*`
- Local execution queue: `bd ready`, `bd show`, `bd create`, `bd update`, `bd close`

Если есть расхождение, верь OpenSpec как shared source of truth и отражай execution state в локальном `bd`.

## Minimal Commands

```bash
bd ready --json
bd create "Title" -t task -p 2 --json
bd update <id> --status in_progress --json
bd close <id> --reason "Done" --json
```

## Seed Backlog

Если новый clone/машина поднимает execution queue с нуля, начни с такого seed:

```bash
bd create "Codex productivity follow-ups" -t epic -p 2 --json
bd create "Add focused automated coverage for task-backed update_database" -t task -p 2 --json
bd create "Promote Beads from local direct-mode bootstrap to shared team workflow" -t task -p 2 --json
bd create "Expand subtree overrides when server areas split further" -t task -p 3 --json
```

## Current Limitation

В текущем bootstrap-е я не зафиксировал shared Dolt remote или repo-tracked `issues.jsonl` export path. Если команде нужен truly shared Beads backlog между clone-ами, это должно стать отдельной инфраструктурной задачей.
