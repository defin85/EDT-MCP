# Long-Running Operations

Этот документ собирает fast path для самой дорогой runtime-зоны: progress, task-backed execution и compatibility fallback.

## Scope

- `update_database`
- `get_active_operation`
- `clean_project`
- `revalidate_objects`
- `debug_launch`

Из них только `update_database` сейчас явно документирован как task-augmented tool surface.

## External Contract

Начинай с product docs:

- `README.md` — `Progress Reporting For Long Operations`
- `README.md` — `Experimental MCP Tasks Support`
- `README.md` — `Application Management Tools`

Ключевые contract points:

- `_meta.progressToken` включает `notifications/progress`
- task augmentation идёт через `tools/call` + `task`
- финальный task-backed result читается через `tasks/result`
- `get_active_operation` остаётся polling fallback

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
- `.../tools/impl/GetActiveOperationTool.java`
- `.../tools/impl/CleanProjectTool.java`
- `.../tools/impl/RevalidateObjectsTool.java`
- `.../tools/impl/DebugLaunchTool.java`
- `.../ui/McpStatusContribution.java`

## Runtime Flow

### Progress Notifications

1. Client calls `tools/call`
2. Request includes `_meta.progressToken`
3. Runtime updates flow through progress reporter
4. MCP emits `notifications/progress`
5. Final tool result remains backward-compatible for clients that ignore progress

### Task-Augmented Update

1. Client calls `tools/call` with `task`
2. Initial response returns `CreateTaskResult`
3. Progress may continue through the original `progressToken`
4. Client polls `tasks/get`, `tasks/list`, or reads final payload through `tasks/result`
5. `get_active_operation` remains compatibility fallback

## Evidence And Gaps

- Product/documentation evidence: `README.md`
- Build/test evidence: `tests/TESTING.md`, `mcp/tests/com.ditrix.edt.mcp.server.tests/`
- Current evidence gap: dedicated checked-in automated coverage for task-backed `update_database` / `notifications/progress` is not yet obvious as a focused suite; runtime verification still depends on a live EDT server

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
