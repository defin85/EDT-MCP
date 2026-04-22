## 1. Capability Contract

- [x] 1.1 Определить public MCP contract для `run_unit_tests`: supported provider values, project
      and application targeting, filter/scope parameters, `runId`, и final summary shape.
- [x] 1.2 Определить public MCP contract для `get_test_run_report`: lookup by stable `runId`,
      supported report formats, retention/expiration semantics, и explicit not-found outcome.
- [x] 1.3 Зафиксировать scope первого rollout-а: только `YAxUnit`, только configuration projects,
      только run mode, без BDD/smoke/debug и без extension-project support.

## 2. Runtime Adapter

- [x] 2.1 Добавить shared YAxUnit runtime adapter поверх EDT runtime bridge для запуска тестов на
      выбранной application target.
- [x] 2.2 Подтвердить supported launch path для YAxUnit startup contract на целевой compatibility
      line: `RuntimeExecutionArguments#setStartupOption(...)` доступен, поэтому отдельный temporary
      launch-configuration adapter на baseline `EDT 2024.2.5.16` не потребовался.
- [x] 2.3 Добавить fail-closed preflight: project/application readiness, YAxUnit availability,
      runtime access settings, busy conflict control, и явный `updateBeforeRun` path.
- [x] 2.4 Добавить retained test-run store с `runId`, summary, report manifest, TTL cleanup и
      безопасным lookup только для read-only retrieval surface.

## 3. MCP Tools

- [x] 3.1 Реализовать async-first `run_unit_tests` с task-backed execution, progress stages и
      project-scoped conflict scheduling.
- [x] 3.2 Реализовать `get_test_run_report` для retrieval сохранённого summary/текстового отчёта
      по `runId` с explicit unavailable/expired outcomes.
- [x] 3.3 Обновить discovery/docs surface (`README.md`, `tools/list`, agent docs), чтобы новый
      tool честно описывался как YAxUnit-backed async-first runtime execution.

## 4. Verification

- [x] 4.1 Добавить focused Tycho/JUnit coverage для request validation, preflight failures,
      provider config materialization, retained run lookup, expiration и report parsing.
- [ ] 4.2 Проверить live flow на реальном EDT contour с установленным YAxUnit: bare
      `run_unit_tests` -> task creation -> progress -> final summary -> `get_test_run_report`.
- [x] 4.3 Обновить `tests/TESTING.md` и verification notes с setup prerequisites, supported scope,
      live commands и явными limitation notes.
