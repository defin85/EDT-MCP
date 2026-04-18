# E2E Override

Эти инструкции действуют для live-server Python E2E subtree.

## Preconditions

- EDT plugin установлен и запущен
- `TestConfiguration` открыт в EDT
- MCP server отвечает на `/health`

## Expectations

- Keep `run_e2e_tests.py` compatible with `.github/workflows/e2e-tests.yml`
- Если новая runtime capability требует live verification, обновляй `tests/TESTING.md` вместе с E2E script или честно фиксируй evidence gap
- Не описывай E2E как CI-ready, если для него всё ещё нужен ручной EDT runtime
