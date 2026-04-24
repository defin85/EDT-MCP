## 1. Capability Contract

- [x] 1.1 Определить public contract для persistent unit test session: session identity, ownership,
      reuse scope, lifecycle states (`starting`, `ready`, `busy`, `stale`, `dead`).
- [x] 1.2 Определить public contract новых tools:
      `prepare_test_session`, `get_test_session_status`, `recycle_test_session`.
- [x] 1.3 Зафиксировать, как `run_unit_tests` выбирает cold launch vs warm session reuse:
      supported `sessionMode` / recycle policy / fail-closed stale outcomes.

## 2. Session Runtime Layer

- [x] 2.1 Добавить session registry для YAxUnit-backed enterprise sessions с stable `sessionId`,
      target correlation и bounded retention.
- [x] 2.2 Добавить liveness tracking и busy serialization, чтобы одна warm session не принимала
      параллельные test runs.
- [x] 2.3 Добавить fail-closed invalidation rules: project/runtime mutations, infobase sync/update,
      lost heartbeat, explicit recycle, incompatible provider/runtime state.
- [x] 2.4 Добавить provider-side control bridge abstraction для persistent session commands, не
      привязывая public contract к единственному transport mechanism.

## 3. MCP Tools And Integration

- [x] 3.1 Реализовать `prepare_test_session` для explicit warm-up / attach flow.
- [x] 3.2 Реализовать `get_test_session_status` для read-only inspection current session state,
      liveness и stale reason.
- [x] 3.3 Реализовать `recycle_test_session` для controlled terminate/restart/invalidate flow.
- [x] 3.4 Расширить `run_unit_tests`, чтобы он умел reuse warm session по policy и честно
      отличал `reused`, `cold_started`, `recycled`, `stale_rejected`.

## 4. Verification

- [x] 4.1 Добавить focused Tycho/JUnit coverage для session state machine, reuse policy,
      invalidation rules, stale reasons и recycle semantics.
- [x] 4.2 Проверить live flow на реальном EDT contour:
      `prepare_test_session` -> первый run -> повторный warm rerun -> intentional mutation ->
      stale rejection -> recycle -> новый successful run.
- [x] 4.3 Обновить `README.md`, `tests/TESTING.md` и agent-facing docs с warm-session contract,
      limitations и explicit "reuse is an optimization, not a correctness shortcut" notes.
