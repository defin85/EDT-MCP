## 1. Contract

- [x] 1.1 Зафиксировать contract для `get_operation_snapshot` и machine-readable blocking hint под
      `long-running-operations`, включая not-found outcome с `found: false`.
- [x] 1.2 Зафиксировать, что `get_active_operation` остаётся focused shortcut, а full
      multi-operation listing/history не входит в этот rollout.

## 2. Runtime Surface

- [x] 2.1 Добавить public `get_operation_snapshot` tool поверх existing runtime lookup по
      `operationId`.
- [x] 2.2 Добавить helper для correlation busy-state blockers с tracked operations по project/app
      context, когда корреляция однозначна.
- [x] 2.3 Заменить opaque busy messages на нормализованные diagnostics с stable reason codes и
      additive `_meta` blocking hint, а также перевести exact-poll hints с `operationId` на
      `get_operation_snapshot`.

## 3. Documentation

- [x] 3.1 Обновить `README.md` с exact polling по `operationId` и blocking hint semantics.
- [x] 3.2 Обновить `docs/agent/long-running-ops.md`, чтобы agent/workflow docs объясняли разницу
      между focused polling и exact operation lookup.

## 4. Verification

- [x] 4.1 Добавить focused unit coverage на `get_operation_snapshot`, not-found semantics и
      blocking hint correlation.
- [ ] 4.2 Проверить live EDT flow: detached continuation lookup по `operationId` и busy-state
      rejection с actionable blocker diagnostics.
      Текущее локальное evidence gap: `curl -sf http://localhost:8765/health` завершился с code 7,
      поэтому live server verification в этой сессии недоступен.
