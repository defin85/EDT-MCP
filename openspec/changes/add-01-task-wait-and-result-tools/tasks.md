## 1. Contract

- [x] 1.1 Define the exact input and output schemas for `list_tasks`, `get_task_result`, and
      `wait_task`.
- [x] 1.2 Define the normalized task/operation evidence envelope and how original tool payloads are
      embedded or referenced.
- [x] 1.3 Confirm ownership behavior for tool wrappers matches `tasks/list`, `tasks/get`, and
      `tasks/result` in the same MCP session.

## 2. Implementation

- [x] 2.1 Implement `list_tasks` as a tool-level view over session-visible task summaries.
- [x] 2.2 Implement `get_task_result` without re-running the original operation.
- [x] 2.3 Implement `wait_task` with bounded polling, terminal-state detection, timeout response,
      and no hidden background wait.
- [x] 2.4 Normalize operation evidence for task-backed update, extension apply, debug launch, and
      YAxUnit flows where the source data is available.
- [x] 2.5 Register the new tools and update generated discovery metadata.

## 3. Verification

- [x] 3.1 Add focused Tycho/JUnit coverage for unknown task IDs, same-session ownership, active task
      snapshots, retained terminal result retrieval, and timeout behavior.
- [x] 3.2 Run the smallest relevant Maven/Tycho gate for task lifecycle code.
- [x] 3.3 Run `openspec validate add-01-task-wait-and-result-tools --strict --no-interactive`.
- [x] 3.4 After reinstalling the built plugin, live-verify an async-first operation from accepted
      task creation through `wait_task` or `get_task_result` terminal evidence.

## Evidence

- `mvn -f mcp/pom.xml -Dtest=TaskLifecycleToolsTest verify --batch-mode --no-transfer-progress -T 1C`
  passed: 8 tests, 0 failures, 0 errors, including lifecycle `result` metadata for active,
  terminal tool payload, and JSON-RPC error outcomes.
- `python3 scripts/generate_agent_refs.py --check` passed.
- `openspec validate add-01-task-wait-and-result-tools --strict --no-interactive` passed.
- Live runtime at `http://172.24.192.1:8766/mcp` after reinstall reported
  `bundleVersion=1.0.0.202604251953`; `tools/list` returned 59 tools including `list_tasks`,
  `get_task_result`, and `wait_task`.
- Live `clean_project` on `Демонстрационная_конфигурация_Управляемое_приложение` accepted task
  `c05bdbae-4884-4f3c-8b89-10f1ead41204`; same-session `list_tasks` showed active lifecycle
  `result.available=false`; `wait_task` returned terminal `completed` with
  `lifecycle.result.kind=toolPayload`; `get_task_result` returned the retained payload without
  rerunning the operation.
- Broader `mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C` currently
  fails outside this change in `YaxUnitRuntimeAdapterCompatibilityTest.testBuildConfigJsonUsesLegacyMinimalShape`:
  the committed adapter emits `showReport`, while the committed test expects it to be absent.
