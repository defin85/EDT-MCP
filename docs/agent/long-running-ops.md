# Long-Running Operations

Этот документ собирает fast path для самой дорогой runtime-зоны: progress, task-backed execution и compatibility fallback.

## Scope

- `update_database`
- `get_operation_snapshot`
- `get_active_operation`
- `clean_project`
- `revalidate_objects`
- `debug_launch`

Первая async-default волна: `update_database`, `clean_project`, и full-project `revalidate_objects`.
`debug_launch` остаётся sync-first в этом rollout-е.
Тяжёлые read-only diagnostics (`get_problem_summary`, `get_project_errors`, `validate_query`) тоже остаются sync-first:
для них текущая стратегия — contract shaping через summary/filter/limit, а не task enablement.

## External Contract

Начинай с product docs:

- `README.md` — `Progress Reporting For Long Operations`
- `README.md` — `Experimental MCP Tasks Support`
- `README.md` — `Application Management Tools`

Ключевые contract points:

- `_meta.progressToken` включает `notifications/progress`
- affected bare `tools/call` requests auto-promote into task-backed execution server-side
- explicit task augmentation по-прежнему идёт через `tools/call` + `task`, а `execution.taskSupport` остаётся `optional`
- финальный task-backed result читается через `tasks/result` в той же MCP session
- terminal sync/task payload для supported operations может нести `_meta["io.ditrix.edt.mcp/detached-continuation"]`
- busy-state rejection может нести `_meta["io.ditrix.edt.mcp/blocking-operation"]` с `reasonCode`, `scope` и exact poll hint при однозначной корреляции
- конфликтующие mutable task-backed операции явно отклоняются, а не запускаются параллельно
- тяжёлые diagnostics остаются sync-only и опираются на summary/filter/limit shaping вместо task lifecycle
- `get_operation_snapshot` даёт exact polling по stable `operationId`, а `get_active_operation` остаётся focused polling fallback с `detached: true` и structured `details`
- cleanup/discoverability для `debug_launch` вынесены в отдельный runtime-debug-control трек, а не в этот task rollout

## Code Entry Points

- `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/McpServer.java`
- `.../protocol/McpProtocolHandler.java`
- `.../protocol/jsonrpc/CreateTaskResult.java`
- `.../protocol/jsonrpc/TaskInfo.java`
- `.../protocol/jsonrpc/TasksListResult.java`
- `.../progress/OperationProgressReporter.java`
- `.../progress/ProgressNotificationSender.java`
- `.../progress/ToolExecutionContext.java`
- `.../tasks/TaskRegistry.java`
- `.../tools/impl/UpdateDatabaseTool.java`
- `.../tools/impl/GetOperationSnapshotTool.java`
- `.../tools/impl/GetActiveOperationTool.java`
- `.../tools/impl/CleanProjectTool.java`
- `.../tools/impl/RevalidateObjectsTool.java`
- `.../tools/impl/DebugLaunchTool.java`
- `.../utils/BlockingOperationDiagnostics.java`
- `.../ui/McpStatusContribution.java`

## Runtime Flow

### Progress Notifications

1. Client calls `tools/call`
2. Request includes `_meta.progressToken`
3. Runtime updates flow through progress reporter
4. MCP emits `notifications/progress`
5. Final tool result remains backward-compatible for clients that ignore progress

### Async-First Long Operation

1. Client calls bare `tools/call` or explicit task-augmented `tools/call`
2. Для async-default tools server creates a task even when `task` is omitted
3. Initial response returns `CreateTaskResult`
4. Пока task live, progress может идти через исходный `progressToken`
5. Final payload is retrieved through `tasks/result` in the same MCP session
6. После terminal MCP outcome detached continuation, если она есть, переносится в `get_operation_snapshot` для exact polling по `operationId`
7. Terminal payload/task result может нести machine-readable continuation hint
8. `get_active_operation` остаётся compatibility fallback, когда `operationId` ещё неизвестен

## Evidence And Gaps

- Product/documentation evidence: `README.md`
- Build/test evidence: `tests/TESTING.md`, `mcp/tests/com.ditrix.edt.mcp.server.tests/`
- Current evidence gap: detached tracker lifecycle и continuation hint покрыты unit-level contract tests, но live rebuild/update continuation всё ещё требует running EDT server

## Verify Strategy

### Narrow

```bash
mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server -am -DskipTests compile
```

### Broad

```bash
mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C
```

### Runtime

```bash
curl -sf http://localhost:8765/health
python tests/e2e/run_e2e_tests.py --host localhost --port 8765 --project TestConfiguration
```

Если конкретный change касается task/progress contract и live EDT недоступен, фиксируй это явно как runtime evidence gap.
