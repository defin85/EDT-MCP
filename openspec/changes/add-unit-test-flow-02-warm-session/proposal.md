# Change: Unit test flow 02 - persistent warm session и hot rerun

## Why

После базового `run_unit_tests` следующий очевидный performance gap — холодный запуск
`1С:Предприятия` на каждый rerun. Для интерактивной работы агента это слишком дорого: даже если
отчёт разбирается быстро, каждое повторное выполнение снова платит за startup runtime.

При этом "просто держать режим предприятия живым" недостаточно. Как только меняется тестируемый
код, runtime target или состояние инфобазы, reuse старой сессии становится опасным: можно получить
быстрые, но недостоверные тесты на устаревшем состоянии.

Нужен отдельный follow-up change после `Unit test flow 01`, который добавит warm-session
execution как ускоряющий режим, но с честной `stale`-семантикой и явным lifecycle control.

## What Changes

- Добавить persistent unit test session surface для YAxUnit-backed test execution на том же target,
  что и базовый `run_unit_tests`.
- Добавить explicit lifecycle tools для warm session:
  `prepare_test_session`, `get_test_session_status`, `recycle_test_session`.
- Расширить `run_unit_tests` reuse policy, чтобы tool мог:
  - использовать существующую warm session,
  - требовать именно warm session,
  - fail-close при `stale`/dead/busy session,
  - по явной policy пересоздавать session перед rerun.
- Ввести fail-closed invalidation rules для session reuse: изменения workspace/runtime state,
  синхронизация инфобазы, потеря liveness/heartbeat, explicit recycle и другие события,
  делающие reuse недостоверным.
- Сохранить scope узким: только YAxUnit, только configuration projects, без BDD/smoke/debug,
  без cross-project pooling и без обещаний "тесты без обновления" как общего случая.

## Impact

- Affected specs: `persistent-test-session-control`
- Affected code: unit-test runtime adapter, session registry/liveness tracking, `run_unit_tests`
  contract, new session-control tools, docs and verification assets
- Validation: strict OpenSpec validation, focused Tycho/JUnit coverage for session state machine and
  invalidation rules, and live EDT verification с cold run -> warm rerun -> stale detection ->
  recycle flow
