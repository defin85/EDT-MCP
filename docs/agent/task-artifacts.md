# Task Artifacts

Этот документ связывает planning, execution и runtime evidence.

## OpenSpec

- `openspec/project.md` — conventions, stack, process contract для этого репозитория.
- `openspec/specs/*` — текущее normative description того, что уже поддерживается.
- `openspec/changes/*` — proposal/design/tasks для ещё не принятых или не завершённых изменений.
- `openspec/changes/archive/*` — завершённые и архивированные changes.

## Beads

- `.beads/` — issue graph, состояния работ, зависимости и локальная metadata для execution stage.
- `bd ready` — источник ready/unblocked работы.
- Для implementation work change должен иметь связанный Beads issue или epic/task decomposition.
- Текущий bootstrap использует direct Dolt backend; shared backlog constraints и seed workflow описаны в `docs/agent/beads-workflow.md`.

## Code And Tests

- `mcp/bundles/com.ditrix.edt.mcp.server/` — production implementation.
- `mcp/tests/com.ditrix.edt.mcp.server.tests/` — unit/Tycho evidence.
- `tests/e2e/` + live EDT server — runtime evidence.
- `TestConfiguration/` — controlled project fixture for runtime tests.

## CI And Release

- `.github/workflows/build.yml` — canonical build/test gate.
- `.github/workflows/e2e-tests.yml` — runtime HTTP validation workflow.
- `.github/workflows/release.yml` and `deploy-update-site.yml` — release/update-site delivery path.

## Working Notes

- `.claude/*.md` — architecture notes, implementation checklists и exploratory plans.
- Эти файлы можно использовать как context или historical rationale, но они не заменяют OpenSpec/Beads.
- `docs/agent/generated-tool-catalog.md` — generated reference для fast tool discovery.
- `docs/agent/long-running-ops.md` — curated runtime flow для самых рискованных change areas.
