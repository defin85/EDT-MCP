# Agent Docs Index

Этот раздел задаёт короткий и проверяемый путь входа в `EDT-MCP` для Codex-агента.

## С чего начинать

1. Канонический policy/source-of-truth по инструкциям: `AGENTS.md` в корне.
2. Curated карта репозитория: `docs/agent/architecture-map.md`.
3. Run/test/verify contract: `docs/agent/verification.md`.
4. Связь OpenSpec, Beads, CI и runtime: `docs/agent/task-artifacts.md`.
5. Локальные prerequisites и bootstrap: `docs/agent/codex-setup.md`.
6. High-friction runtime flow: `docs/agent/long-running-ops.md`.
7. Runtime debug variables smoke: `docs/agent/runtime-debug-smoke.md`.
8. Generated tool reference: `docs/agent/generated-tool-catalog.md`.

## Короткая Карта

- Что это за проект: Eclipse/1C:EDT plugin, который поднимает MCP server для AI clients.
- Где основная реализация: `mcp/bundles/com.ditrix.edt.mcp.server/`.
- Где unit/Tycho tests: `mcp/tests/com.ditrix.edt.mcp.server.tests/`.
- Где runtime/E2E checks: `tests/e2e/run_e2e_tests.py`, `TestConfiguration/`.
- Где рабочие design notes: `.claude/*.md`.
- Где смотреть tool -> implementation/test mapping: `docs/agent/generated-tool-catalog.md`.
- Где смотреть long-running runtime path: `docs/agent/long-running-ops.md`.

## Быстрые Ответы

- Где смотреть build graph: `mcp/pom.xml`.
- Где искать protocol entry points: `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/protocol/`.
- Где искать tool implementations: `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/`.
- Где искать UI/status bar behaviour: `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/ui/`.
- Где искать progress/tasks wiring: `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/progress/`, `.../tasks/`.
- Как быстро проверить onboarding surface: `scripts/codex-onboard.sh`, `scripts/verify_agent_surface.sh`.

## Search Guidance

- Для capability discovery начинай с `README.md`, `tests/TESTING.md` и `docs/agent/architecture-map.md`.
- Для code discovery используй exact identifiers через `rg -n`, затем сужай по package/directory.
- Для runtime claims проверяй code + tests/docs/workflows, а не один источник.
- Для tool-level discovery используй `docs/agent/generated-tool-catalog.md` раньше, чем ручной обход `tools/impl/`.
