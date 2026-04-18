<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# Root Policy

`AGENTS.md` в корне репозитория задаёт канонический workflow для AI-агентов.

## Source Of Truth Order

1. Этот файл задаёт глобальные правила discovery, OpenSpec/Beads workflow и handoff contract.
2. `docs/agent/index.md` и связанные `docs/agent/*` дают curated navigation, verify contract и traceability.
3. `openspec/AGENTS.md`, `openspec/project.md`, `openspec/specs/*`, `openspec/changes/*` управляют spec-driven workflow после инициализации OpenSpec.
4. `.codex/config.toml` задаёт repo-scoped Codex defaults, когда проект trusted.
5. Локальные `AGENTS.md` / `AGENTS.override.md` в подпроектах допустимы только для area-specific контекста.

## Language

- Планы, спеки, agent-facing docs и change descriptions ведём на русском языке.
- Code, UI strings, JSON-RPC method names, tool names и API identifiers сохраняем на английском.

## Execution Workflow

We operate in a cycle: **OpenSpec (What) -> Beads (How) -> Code (Implementation)**.

### Approval Gate

- До явного approval меняются только OpenSpec artifacts, `docs/agent/*` и Beads planning artifacts.
- После approval change должен быть отражён и в `openspec/changes/*`, и в Beads issue graph.

### Execution Loop

- Перед реализацией читай `openspec/project.md`, затем релевантные `proposal.md`, `tasks.md`, `design.md` и delta specs.
- Для code changes работай только из ready/unblocked Beads work.
- Держи OpenSpec intent, Beads graph и repository state синхронными.
- Для каждого обязательного требования нужна traceability: `Requirement -> Code -> Test`.

## Curated Agent Docs

Начинай discovery отсюда:

- `docs/agent/index.md` — стартовый индекс.
- `docs/agent/architecture-map.md` — карта модулей, entry points и boundaries.
- `docs/agent/verification.md` — canonical run/test/verify contract.
- `docs/agent/task-artifacts.md` — связь OpenSpec, Beads, CI, runtime и рабочих заметок.
- `docs/agent/codex-setup.md` — prerequisites и portable bootstrap для локальной работы.
- `docs/agent/long-running-ops.md` — high-friction карта progress/tasks/runtime flow.
- `docs/agent/generated-tool-catalog.md` — generated reference по tool implementations и тестам.
- `docs/agent/beads-workflow.md` — текущее состояние Beads, seed backlog и ограничения shared workflow.

## Search And Verification

- Search order: `docs/agent/*` -> `mcp__claude_context__search_code` (when indexed) -> `rg -n` -> `rg --files`.
- Для текстового поиска предпочитай точные identifiers, затем JSON-RPC method names, tool names и directory names.
- Важные выводы подтверждай минимум двумя источниками: code + test/doc/README/workflow.
- Для behavioural claims ищи подтверждение не только в коде, но и в `README.md`, `tests/TESTING.md`, `.github/workflows/*` или `plugin.xml`.
- После изменений запускай минимальный релевантный verify set, затем более широкий gate при необходимости.

## Local Instruction Zones

- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/AGENTS.override.md` — core plugin runtime, protocol, tools, progress, tasks, UI.
- `mcp/tests/com.ditrix.edt.mcp.server.tests/AGENTS.override.md` — Tycho/JUnit test fragment conventions.
- `tests/e2e/AGENTS.override.md` — live-server Python E2E flow.

## Repository Boundaries

- `mcp/bundles/com.ditrix.edt.mcp.server/` — основной Eclipse/EDT plugin: protocol, tools, UI, progress, tags, groups, tasks.
- `mcp/tests/com.ditrix.edt.mcp.server.tests/` — Tycho/JUnit layer без живого EDT.
- `tests/e2e/run_e2e_tests.py` — HTTP E2E against a running MCP server.
- `TestConfiguration/` — минимальная 1C configuration fixture для runtime/E2E checks.
- `.claude/*.md` — рабочие architecture/implementation notes; полезны как context, но не являются source of truth.

## Durable Doc Policy

- Agent-facing docs по умолчанию используют path/section links, а не line-level ссылки.
- Line-level evidence допустим только в review notes, diff discussions и generated references.
- В agent-facing docs фиксируй только живые команды, пути и проверяемые артефакты.

## Session Completion

- Не закрывай задачу без явного verify evidence.
- Не помечай работу как завершённую, если обязательное поведение вынесено в `TODO` или `FIXME`.
- В финальном handoff ссылайся на конкретные файлы, проверки и открытые риски.

<!-- BEGIN BEADS INTEGRATION -->
## Issue Tracking with bd (beads)

Используй `bd` как execution layer после OpenSpec planning/approval.

### Minimal Workflow

- Проверить ready work: `bd ready --json`
- Создать/связать execution issue: `bd create ... --json`
- Взять в работу: `bd update <id> --status in_progress --json`
- Закрыть после verify: `bd close <id> --reason "Done" --json`

### Repo Notes

- Prefix для этого репозитория: `EDTMCP`
- Для найденной по ходу работы новой задачи используй `discovered-from:<parent-id>`
- Не используй markdown TODO-листы как основной execution tracker, если работа уже заведена в Beads
- Если shared Beads history ещё не развёрнут, считай OpenSpec durable planning layer, а локальный `bd` — execution queue текущей машины
- Для текущего bootstrap и seed backlog смотри `docs/agent/beads-workflow.md`

<!-- END BEADS INTEGRATION -->
