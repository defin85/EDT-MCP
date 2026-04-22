# Change: Unit test flow 01 - базовый cold run через EDT runtime

## Why

`EDT-MCP` уже умеет выполнять длительные runtime-операции, синхронизировать инфобазы и запускать
runtime в debug mode, но по-прежнему не умеет честно запускать unit tests из MCP. Сейчас агенту
нужен ручной handoff в EDT UI, внешние launch configurations или сторонние тестовые плагины, что
делает тестовый запуск неуправляемым и плохо автоматизируемым.

Для первого rollout нужен узкий и безопасный механизм запуска unit tests через текущий task/progress
contract `EDT-MCP`, без притворства, что сервер уже поддерживает весь ландшафт 1С-тестирования.

## What Changes

- Добавить поддержанный MCP surface для запуска unit tests через EDT runtime на выбранной
  configuration project и её registered application target.
- Выбрать `YAxUnit` как единственный provider первого rollout-а; НЕ делать `Vanessa Automation`,
  `Vanessa-ADD` или внешний `edt-test-runner` обязательной runtime-зависимостью для этого change.
- Не повторять внешний standalone build/test server внутри `EDT-MCP`: использовать уже существующую
  EDT workspace/runtime model, `projectName` и `applicationId`, а не вводить отдельный
  `base-path`/`source-set`/`connection-string` configuration surface как основной контракт.
- Добавить async-first tool `run_unit_tests` с task-backed execution, progress reporting и
  retained final summary.
- Добавить read-only tool `get_test_run_report` для retrieval сохранённого отчёта/summary по
  stable `runId`, вместо контракта "вот путь к файлу на диске EDT".
- Зафиксировать fail-closed preflight и scope boundaries: только configuration projects, только
  supported YAxUnit flow, без BDD/smoke/debug и без extension-project support в первом rollout-е.
- Явно НЕ обещать rebuild-free или persistent-session fast path в первом rollout-е; потенциальный
  WebSocket/hot-session execution остаётся отдельным follow-up после доказанной базовой viability.

## Impact

- Affected specs: `unit-test-execution`, `long-running-operations`
- Affected code: runtime launch adapter, tool registry and implementations, retained test-run
  storage, progress/tasks wiring, `README.md`, `tests/TESTING.md`, agent docs
- Validation: strict OpenSpec validation, focused Tycho/JUnit coverage for preflight/config/report
  handling, and live EDT verification against a real YAxUnit-enabled contour
