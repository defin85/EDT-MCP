## 1. Contract

- [ ] 1.1 Зафиксировать contract для `get_operation_snapshot` и machine-readable blocking hint под
      `long-running-operations`.
- [ ] 1.2 Зафиксировать, что `get_active_operation` остаётся focused shortcut, а full
      multi-operation listing/history не входит в этот rollout.

## 2. Runtime Surface

- [ ] 2.1 Добавить public `get_operation_snapshot` tool поверх existing runtime lookup по
      `operationId`.
- [ ] 2.2 Добавить helper для correlation busy-state blockers с tracked operations по project/app
      context, когда корреляция однозначна.
- [ ] 2.3 Заменить opaque busy messages на нормализованные diagnostics с stable reason codes и
      additive `_meta` blocking hint.

## 3. Documentation

- [ ] 3.1 Обновить `README.md` с exact polling по `operationId` и blocking hint semantics.
- [ ] 3.2 Обновить `docs/agent/long-running-ops.md`, чтобы agent/workflow docs объясняли разницу
      между focused polling и exact operation lookup.

## 4. Verification

- [ ] 4.1 Добавить focused unit coverage на `get_operation_snapshot`, not-found semantics и
      blocking hint correlation.
- [ ] 4.2 Проверить live EDT flow: detached continuation lookup по `operationId` и busy-state
      rejection с actionable blocker diagnostics.
