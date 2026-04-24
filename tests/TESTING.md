# Testing EDT MCP Server

## Architecture

The testing infrastructure consists of two layers:

### 1. Unit Tests (Tycho Surefire)

Located in `mcp/tests/com.ditrix.edt.mcp.server.tests/`

These are JUnit 4 tests that run inside the Eclipse/Tycho build without requiring a running EDT instance. They cover:

- **Protocol layer**: `JsonSchemaBuilder`, `JsonUtils`, `GsonProvider`, `McpConstants`
- **JSON-RPC DTOs**: `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError`
- **Tool results**: `ToolResult`, `ToolCallResult`, `ToolsListResult`

**Running locally:**
```bash
cd mcp
mvn clean verify
```

Unit tests run automatically during the Maven build. Results are in:
```
mcp/tests/com.ditrix.edt.mcp.server.tests/target/surefire-reports/
```

### 2. E2E Tests (Python HTTP client)

Located in `tests/e2e/run_e2e_tests.py`

These tests send real HTTP requests to a running MCP server and validate every tool. They require:
- A running EDT instance with the MCP plugin installed
- The `TestConfiguration` project loaded in EDT

**Running locally:**
```bash
# Make sure EDT is running with MCP server on port 8765
python tests/e2e/run_e2e_tests.py

# Or with custom settings:
python tests/e2e/run_e2e_tests.py --host localhost --port 8765 --project TestConfiguration

# Wait for server to start (useful for CI):
python tests/e2e/run_e2e_tests.py --wait 300

# Generate JUnit XML report:
python tests/e2e/run_e2e_tests.py --junit-xml results.xml
```

**E2E tests cover:**

| Category | Tools |
|----------|-------|
| Protocol | health, initialize, tools/list, error handling |
| Standalone | get_edt_version, list_projects, get_platform_documentation, get_check_description |
| Project | get_configuration_properties, get_metadata_objects, get_metadata_details, get_problem_summary, get_project_errors, get_tags, get_bookmarks, get_tasks |
| BSL Code | list_modules, get_module_structure, read_module_source, read_method_source, search_in_code |
| Advanced | find_references, get_applications, get_form_screenshot |

Task capability advertisement, per-tool `execution.taskSupport`, task registry/scheduling behavior,
detached continuation metadata, detached snapshot reporter semantics, and focused infobase detached
projection mapping now have checked-in unit coverage. Live task lifecycle, ownership, conflict control, sync compatibility,
`notifications/progress`, compatibility polling through `get_active_operation`, and detached rebuild
continuation have been manually verified against a real MCP client. Live detached infobase update
continuation still does not have a dedicated runtime E2E suite.

The first `run_unit_tests` cold-run slice now has checked-in unit coverage for:

- request validation and fail-closed provider/scope boundaries
- bare-call task auto-promotion through the MCP protocol layer
- retained `runId` lookup through `get_test_run_report`
- JUnit XML parsing and report-store expiration

The warm-session slice now has checked-in unit coverage for:

- session lifecycle contract and stable wire values
- provider bridge prepare/recycle/execute result boundaries
- `run_unit_tests.sessionMode` schema and fail-closed validation
- reuse policy routing for `cold`, `prefer_warm`, `require_warm`, and `recycle_then_run`
- YAxUnit RPC report payload conversion to retained JUnit XML

Live YAxUnit cold and warm execution on a real EDT contour is still a runtime evidence gap. Do not
close the change on Tycho coverage alone.

### Unit-test execution live verify

Current supported runtime slice:

- `provider=yaxunit` only
- configuration projects only
- task-backed `run_unit_tests`
- retained `get_test_run_report`
- persistent session tools: `prepare_test_session`, `get_test_session_status`, `recycle_test_session`
- warm execution through `run_unit_tests.sessionMode=prefer_warm|require_warm`

Runtime prerequisites for honest live verification:

- running EDT instance with the updated plugin installed
- target application discoverable through `get_applications`
- valid EDT infobase access settings for the selected target
- installed `YAXUNIT` engine extension in the target infobase

Minimal live verification sequence:

```bash
HOST_IP=$(ip route | awk '/default/ {print $3; exit}')
curl -sS -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":401,"method":"tools/call","params":{"name":"get_applications","arguments":{"projectName":"<project>"}}}' \
  "http://$HOST_IP:8765/mcp"

curl -sS -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Session-Id: test-session-1' \
  -d '{"jsonrpc":"2.0","id":402,"method":"tools/call","params":{"name":"run_unit_tests","arguments":{"projectName":"<project>","applicationId":"<app-id>","provider":"yaxunit","scope":"all"},"_meta":{"progressToken":"unit-run-1"}}}' \
  "http://$HOST_IP:8765/mcp"

curl -sS -H 'Content-Type: application/json' \
  -H 'MCP-Session-Id: test-session-1' \
  -d '{"jsonrpc":"2.0","id":403,"method":"tasks/result","params":{"taskId":"<task-id>"}}' \
  "http://$HOST_IP:8765/mcp"

curl -sS -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":404,"method":"tools/call","params":{"name":"get_test_run_report","arguments":{"runId":"<run-id>","format":"manifest"}}}' \
  "http://$HOST_IP:8765/mcp"
```

Warm-session verification sequence:

```bash
curl -sS -H 'Content-Type: application/json' \
  -H 'MCP-Session-Id: test-session-1' \
  -d '{"jsonrpc":"2.0","id":405,"method":"tools/call","params":{"name":"prepare_test_session","arguments":{"projectName":"<project>","applicationId":"<app-id>","provider":"yaxunit"}}}' \
  "http://$HOST_IP:8765/mcp"

curl -sS -H 'Content-Type: application/json' \
  -H 'MCP-Session-Id: test-session-1' \
  -d '{"jsonrpc":"2.0","id":406,"method":"tools/call","params":{"name":"run_unit_tests","arguments":{"projectName":"<project>","applicationId":"<app-id>","provider":"yaxunit","sessionMode":"require_warm","scope":"module","testModule":"<common-module>"}}}' \
  "http://$HOST_IP:8765/mcp"

curl -sS -H 'Content-Type: application/json' \
  -H 'MCP-Session-Id: test-session-1' \
  -d '{"jsonrpc":"2.0","id":407,"method":"tools/call","params":{"name":"get_test_session_status","arguments":{"sessionId":"<session-id>"}}}' \
  "http://$HOST_IP:8765/mcp"

curl -sS -H 'Content-Type: application/json' \
  -H 'MCP-Session-Id: test-session-1' \
  -d '{"jsonrpc":"2.0","id":408,"method":"tools/call","params":{"name":"recycle_test_session","arguments":{"sessionId":"<session-id>"}}}' \
  "http://$HOST_IP:8765/mcp"
```

Expected warm proof:

- `prepare_test_session` returns `state=ready` and provider correlation with `transport=ws`
- `run_unit_tests` with `sessionMode=require_warm` returns `sessionOutcome=reused`
- after `updateBeforeRun=true` or explicit recycle, a later `require_warm` returns `sessionOutcome=stale_rejected` instead of silently launching cold

### Extension lifecycle live probe

The extension lifecycle rollout currently has two live verification layers:

- a repo-owned manual probe for the delivered discovery/inspection/fail-closed slice
- a separate mutation proof for `apply_extension_to_infobase` that is still manual because it
  requires controlled drift in the demo extension workspace

```bash
python tests/e2e/run_extension_lifecycle_probe.py \
  --host "$(ip route | awk '/default/ {print $3; exit}')" \
  --port 8766 \
  --configuration-project 'Демонстрационная_конфигурация_Управляемое_приложение' \
  --extension-project 'Демонстрационная_конфигурация_Управляемое_приложение.ВесТоваров' \
  --expected-installed 'ВесТоваров,Колонтитулы'
```

Canonical live fixture for this probe:

- EDT workspace: `E:\Projects\DemoEDT`
- Configuration project: `Демонстрационная_конфигурация_Управляемое_приложение`
- Extension projects:
  - `Демонстрационная_конфигурация_Управляемое_приложение.ВесТоваров`
  - `Демонстрационная_конфигурация_Управляемое_приложение.Колонтитулы`
- Expected runtime target state before probing: `SYNCHRONIZED`, `EQUAL`, `UPDATED`
- Expected verdict for the base probe:
  - `get_extension_runtime_targets` succeeds
  - `list_infobase_extensions` succeeds
  - `check_extension_applicability` returns fail-closed
    `extension_runtime_headless_unsafe`
  - repeated `list_infobase_extensions` still succeeds after the applicability probe

Additional live proof on the same fixture has been performed manually:

- a controlled source edit in `...ВесТоваров/src/Documents/РасходТовара/ManagerModule.bsl`
  moves the target to `INCREMENTAL_UPDATE_REQUIRED` / `NOT_EQUAL`
- the same internal EDT sync path that now backs `apply_extension_to_infobase` then synchronizes
  the extension back to `UPDATED` / `EQUAL`
- cleanup by reverting the same edit and synchronizing through the same internal path also succeeds
- the public `apply_extension_to_infobase` contract is live-verified on the same fixture:
  - a bare `tools/call` returns `CreateTaskResult`
  - `tasks/result` returns the final tool payload with related-task metadata
  - the same controlled drift is observed by the tool as
    `INCREMENTAL_UPDATE_REQUIRED / NOT_EQUAL`
  - cleanup through the same public tool returns the fixture to `UPDATED / EQUAL`

The scripted probe still verifies only the discovery/inspection/fail-closed boundary. The
extension apply proof is live-verified but not yet packaged as a CI-ready E2E scenario.

## Test Configuration

The `TestConfiguration/` directory contains a minimal 1C:Enterprise configuration for testing:

- **Catalog.Catalog** — with ItemForm
- **CommonModule.OK** — empty, valid module
- **CommonModule.Error** — module with intentional error
- **CommonForm.Form** — common form
- **CommonAttribute.CommonAttribute** — common attribute
- **Subsystem.Subsystem** — subsystem
- **SessionParameter.SessionParameter** — session parameter

The extension lifecycle live probe uses an external demo workspace loaded in EDT rather than a
checked-in repository fixture.

## GitHub Actions

### build.yml (automatic)
Runs unit tests on every push/PR to master. Test results are published to PR checks.

### e2e-tests.yml (manual)
Triggered via `workflow_dispatch`. Requires a running MCP server (self-hosted runner or tunnel).

### Future: Full CI Pipeline
For fully automated E2E on GitHub Actions, the plan is:
1. Build the plugin via Tycho
2. Install EDT headless (if a headless runner/Docker image becomes available)
3. Import TestConfiguration
4. Start MCP server
5. Run E2E tests
6. Publish results

## Project Structure

```
EDT-MCP/
├── mcp/
│   ├── bundles/
│   │   └── com.ditrix.edt.mcp.server/        # Main plugin
│   ├── tests/
│   │   ├── pom.xml                             # Tests parent
│   │   └── com.ditrix.edt.mcp.server.tests/   # Unit test fragment
│   │       ├── META-INF/MANIFEST.MF
│   │       ├── pom.xml
│   │       └── src/                            # JUnit tests
│   └── pom.xml                                 # Root (includes tests module)
├── tests/
│   └── e2e/
│       └── run_e2e_tests.py                    # E2E test script
├── TestConfiguration/                          # Test 1C configuration
│   └── src/
└── .github/workflows/
    ├── build.yml                               # CI with unit tests
    └── e2e-tests.yml                           # E2E test workflow
```
