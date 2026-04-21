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
- [x] 4.2 Проверить live EDT flow: detached continuation lookup по `operationId` и busy-state
      rejection с actionable blocker diagnostics.
      Live evidence получен на `E:\Projects\DemoEDT` через repo-scoped endpoint
      `http://172.24.192.1:8766/mcp`: `clean_project` на
      `Демонстрационная_конфигурация_Управляемое_приложение` был auto-promoted в task, отменён для
      detached continuation, `tasks/result` вернул
      `_meta["io.ditrix.edt.mcp/detached-continuation"]` с stable `operationId`, а subsequent
      `get_operation_snapshot` вернул detached snapshot с `found: true`. На том же проекте
      `get_project_errors` во время detached build вернул
      `_meta["io.ditrix.edt.mcp/blocking-operation"]` с `reasonCode=project_build_in_progress`,
      тем же `operationId` и `pollTool=get_operation_snapshot`.
