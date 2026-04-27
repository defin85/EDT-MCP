[![GitHub all releases](https://img.shields.io/github/downloads/DitriXNew/EDT-MCP/total)](https://github.com/DitriXNew/EDT-MCP/releases)
![EDT](https://img.shields.io/badge/EDT-2025.2.0+-blue?style=plastic)
# EDT MCP Server

MCP (Model Context Protocol) server plugin for 1C:EDT, enabling AI assistants (Claude, GitHub Copilot, Cursor, etc.) to interact with EDT workspace.

## Features

- 🔧 **MCP Protocol 2025-11-25** - Streamable HTTP transport with SSE support
- 📊 **Project Information** - List workspace projects and configuration properties
- 🔴 **Error Reporting** - Get errors, warnings, problem summaries with filters
- 📝 **Check Descriptions** - Get check documentation from markdown files
- 🔄 **Project Revalidation** - Trigger revalidation when validation gets stuck
- 🔖 **Bookmarks & Tasks** - Access bookmarks and TODO/FIXME markers
- 💡 **Content Assist** - Get type info, method hints and platform documentation at any code position
- 🧪 **Query Validation** - Validate 1C query text in project context (syntax + semantic errors, optional DCS mode)
- 🧪 **Unit Test Execution** - Run retained `YAxUnit` unit-test flows through EDT runtime, reuse warm sessions, and retrieve reports by stable `runId`
- 🧩 **BSL Code Analysis** - Browse modules, inspect structure, read/write methods, search code, and analyze call hierarchy
- 🖼️ **Form Screenshot Capture** - Get PNG screenshots from the form WYSIWYG editor for visual inspection
- 🚀 **Application Management** - Get applications, update database, run unit tests, and launch in debug mode
- 🎯 **Status Bar** - Real-time server status with stage-aware progress, elapsed time, and interactive controls
- ⚡ **Interruptible Operations** - Cancel long-running operations and send signals to AI agent
- 📡 **Progress Reporting** - `update_database` and `apply_extension_to_infobase` can publish MCP `notifications/progress`, with `get_operation_snapshot` for exact polling and `get_active_operation` as focused fallback
- 🏷️ **Metadata Tags** - Organize objects with custom tags, filter Navigator, keyboard shortcuts (Ctrl+Alt+1-0), multiselect support
- 📁 **Metadata Groups** - Create custom folder hierarchy in Navigator tree per metadata collection
- ✏️ **Metadata Refactoring** - Rename/delete metadata objects with full cascading updates across BSL code, forms and metadata; add new attributes to existing objects

## Installation

**Only EDT 2025.2.0+**

### From Update Site

1. In EDT: **Help → Install New Software...**
2. Add update site URL: `https://ditrixnew.github.io/EDT-MCP/`
3. Select **EDT MCP Server Feature**
4. Restart EDT

### From Windows command line</strong> - "one shot" very fast install

Close your EDT (!) and run:

```bash
rem Here  "%VER_EDT% = 2025.2.3+30"  just for example - please, set YOUR actual version !
set VER_EDT=2025.2.3+30

"\your\path\to\EDT\components\1c-edt-%VER_EDT%-x86_64\1cedt.exe" -nosplash ^
    -application org.eclipse.equinox.p2.director ^
    -repository https://ditrixnew.github.io/EDT-MCP/ ^
	-installIU com.ditrix.edt.mcp.server.feature.feature.group ^
	-profileProperties org.eclipse.update.reconcile=true
```

### From local build artifact (development)

After

```bash
mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C
```

the repository module produces:

- `com.ditrix.edt.mcp.server.repository-1.0.0-SNAPSHOT.zip` — mutable latest build
- `com.ditrix.edt.mcp.server.repository-1.0.0-SNAPSHOT-YYYYMMDDHHMMSS.zip` — immutable per-build local update site
- `mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site/` — stable latest-only local update site
- `mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site.zip` — stable ZIP wrapper over the same latest-only local update site
- `mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site-history/` — composite archive of previous local builds
- `mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site-history.zip` — ZIP wrapper over the same history archive

For repeated local reinstall/update testing in EDT, use `local-update-site/` for normal **Update**
operations when you want the stable path to expose only the newest build. Use the timestamped ZIP,
an exact `releases/<version>/` child repository, or `local-update-site-history/` when you need an
immutable reinstall/rollback source.

If you want one constant local source path for repeated **Update** operations, point EDT to
`local-update-site/` (or `local-update-site.zip`). It is a latest-only p2 repository that is
replaced on every `clean verify`, so EDT sees only the most recent local build from that URL.

### Installation Result

<details>
Once the installation has been completed successfully, we will see the following:

![MCP Server After Install](img/AfterInstall.png)
</details>

After that, EDT will automatically monitor the update site and install available updates when detected.

As well, we can also manually check via **Help → About → Installation Details → Select MCP → Update**

### Configuration

Go to **Window → Preferences → MCP Server**:
- **Server Port**: HTTP port (default: 8765)
- **Check descriptions folder**: Path to check description markdown files
- **Auto-start**: Start server on EDT launch
- **Default result limit**: Default number of results returned by tools (default: 100)
- **Maximum result limit**: Maximum number of results that can be requested (default: 1000)
- **Plain text mode (Cursor compatibility)**: Returns results as plain text instead of embedded resources (for AI clients that don't support MCP resources)
- **Show tags in Navigator**: Display tags as decorations in the Navigator tree
- **Tag decoration style**: How tags are displayed — all tags as suffix, first tag only, or tag count

![MCP Server Settings](img/Settings.png)

## Status Bar Controls

The MCP server status bar shows real-time execution status with interactive controls.

**Status Indicator:**
- 🟢 **Green** - Server running, idle
- 🟡 **Yellow blinking** - Tool is executing
- ⚪ **Grey** - Server stopped

![Status Bar Menu](img/StatusButtons.png)

<details>
<summary><strong>User Signal Controls</strong> - Send signals to AI agent during tool execution</summary>

**During Tool Execution:**
- Shows tool name and current stage (e.g., `MCP: update_database - waiting for edt`)
- Shows elapsed time in MM:SS format
- Shows progress percent only when EDT reports real `progress/total`
- Click to access control menu

When a tool is executing, you can send signals to the AI agent to interrupt the MCP call:

| Button | Description | When to Use |
|--------|-------------|-------------|
| **Cancel Operation** | Stops the MCP call and notifies agent | When you want to cancel a long-running operation |
| **Retry** | Tells agent to retry the operation | When an EDT error occurred and you want to try again |
| **Continue in Background** | Notifies agent the operation is long-running | When you want agent to check status periodically |
| **Ask Expert** | Stops and asks agent to consult with you | When you need to provide guidance |
| **Send Custom Message...** | Send a custom message to agent | For any custom instruction |

**How it works:**
1. When you click a button, a dialog appears showing the message that will be sent to the agent
2. You can edit the message before sending
3. The MCP call is immediately interrupted and returns control to the agent
4. The EDT operation continues running in the background
5. Agent receives a response like:
```
USER SIGNAL: Your message here

Signal Type: CANCEL
Tool: update_database
Elapsed: 20s

Note: The EDT operation may still be running in background.
```

**Use cases:**
- Long-running operations (full database update, project validation) blocking the agent
- Need to give the agent additional instructions
- EDT showed an error dialog and you want agent to retry
- Want to switch agent's focus to a different task

</details>

### Progress Reporting For Long Operations

`update_database`, `apply_extension_to_infobase`, `run_unit_tests`, `clean_project`, and full-project
`revalidate_objects` publish the same runtime
progress model to both EDT UI and MCP clients.

- The status bar keeps showing the active EDT operation even after **Continue in Background** interrupted the HTTP call.
- MCP `notifications/progress` are sent only when the client provided `_meta.progressToken` in the original `tools/call` request and has an SSE stream attached to the same `MCP-Session-Id`.
- When EDT work survives the original task/call lifetime, `get_operation_snapshot` can return the detached snapshot for the stable `operationId`, while `get_active_operation` remains the focused fallback projection with `detached: true` and structured `details`.
- Detached continuation relies on `get_operation_snapshot` plus the EDT status bar; the original `progressToken` is not kept alive after the MCP task/call becomes terminal.
- For `update_database`, `apply_extension_to_infobase`, `run_unit_tests`, `clean_project`, and full-project `revalidate_objects`, a bare call now returns task creation metadata; the final payload is retrieved later through `tasks/result` in the same MCP session.
- Sync-only tools still return the normal final tool result directly when clients ignore progress notifications.
- `get_active_operation` can still be used as a polling fallback when a client does not support or does not consume `notifications/progress`.
- Busy project/application rejections now include `_meta["io.ditrix.edt.mcp/blocking-operation"]` with stable reason codes, scope identifiers, and exact `operationId` hints when correlation is unambiguous.

Minimal request requirements for progress notifications:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "update_database",
    "arguments": {
      "projectName": "MyProject",
      "applicationId": "app-id"
    },
    "_meta": {
      "progressToken": "update-1"
    }
  }
}
```

When supported, the server may emit:

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/progress",
  "params": {
    "progressToken": "update-1",
    "progress": 3,
    "message": "waiting for edt"
  }
}
```

### Experimental MCP Tasks Support

The server now exposes experimental MCP Tasks support for long-running tool execution.

- `initialize` advertises `capabilities.tasks.list`, `capabilities.tasks.cancel`, and `capabilities.tasks.requests.tools.call`
- `tools/list` exposes `execution.taskSupport` for every tool
- `update_database`, `apply_extension_to_infobase`, `run_unit_tests`, and `clean_project` keep `execution.taskSupport: "optional"` and are async-first at runtime: bare calls auto-promote into task-backed execution
- `revalidate_objects` keeps `execution.taskSupport: "optional"` and is async-first only for full-project revalidation; partial object revalidation stays synchronous and task-augmented partial requests are rejected with an actionable error
- `tasks/get`, `tasks/list`, `tasks/result`, and `tasks/cancel` are available over the same `/mcp` endpoint
- Tool-level wrappers `list_tasks`, `get_task_result`, and `wait_task` expose the same session-owned lifecycle through `tools/list`; `wait_task` is bounded and returns timeout as a normal tool outcome
- Follow-up `tasks/get`, `tasks/result`, and `tasks/cancel` calls must use the same `MCP-Session-Id` that created the task
- The original `_meta.progressToken` stays valid for task-backed `update_database`, `apply_extension_to_infobase`, `run_unit_tests`, `clean_project`, and full-project `revalidate_objects` calls while the task is live; after a terminal MCP outcome, detached continuation moves to `get_operation_snapshot` for exact polling by `operationId`
- When cancellation can leave EDT work running in background, terminal sync/task payloads include `_meta["io.ditrix.edt.mcp/detached-continuation"]` with the stable `operationId` and `pollTool: "get_operation_snapshot"`
- Busy project/application rejections may include `_meta["io.ditrix.edt.mcp/blocking-operation"]` with `reasonCode`, `scope`, `projectName`, known application identifiers, and exact polling hints when the blocker maps to a single tracked operation
- Conflicting mutable task-backed operations are rejected explicitly instead of running in unsafe parallel
- Heavy read-only diagnostics such as `get_problem_summary` and `get_project_errors` remain synchronous in this rollout; use summary/filter/limit shaping instead of task augmentation
- `get_active_operation` remains available as a compatibility fallback for clients that do not consume Tasks yet
- `debug_launch` remains sync-first during this rollout; debug session discoverability and cleanup stay on the separate runtime debug-control track

Minimal bare `update_database` request. The server auto-promotes it into task-backed execution:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "update_database",
    "arguments": {
      "projectName": "MyProject",
      "applicationId": "app-id"
    },
    "_meta": {
      "progressToken": "update-task-1"
    }
  }
}
```

Explicit task-augmented `update_database` request:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "update_database",
    "arguments": {
      "projectName": "MyProject",
      "applicationId": "app-id"
    },
    "task": {
      "ttl": 600000
    },
    "_meta": {
      "progressToken": "update-task-1"
    }
  }
}
```

The initial response returns `CreateTaskResult` in both cases, and the final tool payload is retrieved later through `tasks/result` in the same MCP session.

## Connecting AI Assistants

### VS Code / GitHub Copilot

Create `.vscode/mcp.json`:
```json
{
  "servers": {
    "EDT MCP Server": {
      "type": "sse",
      "url": "http://localhost:8765/mcp"
    }
  }
}
```

<details>
<summary><strong>Other AI Assistants</strong> - Cursor, Claude Code, Claude Desktop</summary>

### Cursor IDE

> **Note:** Cursor doesn't support MCP embedded resources. Enable **"Plain text mode (Cursor compatibility)"** in EDT preferences: **Window → Preferences → MCP Server**.

Create `.cursor/mcp.json`:
```json
{
  "mcpServers": {
    "EDT MCP Server": {
      "url": "http://localhost:8765/mcp"
    }
  }
}
```

### Claude Code

> **Note:** By editing the file `.claude.json` can be added to the MCP either to a specific project or to any project (at the root). If there is no mcpServers section, add it.

Add to `.claude.json` (in Windows `%USERPROFILE%\.claude.json`):
```json
"mcpServers": {
  "EDT MCP Server": {
    "type": "http",
    "url": "http://localhost:8765/mcp"
  }
}
```

### Claude Desktop

Add to `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "EDT MCP Server": {
      "url": "http://localhost:8765/mcp"
    }
  }
}
```

### Cline - extension for VSCode.

```json
{
  "mcpServers": {
    "EDTMCPServer": {
      "type": "streamableHttp",
      "url": "http://localhost:8765/mcp"
    }
  }
}
```

</details>

## Available Tools

| Tool | Description |
|------|-------------|
| `get_edt_version` | Returns current EDT version |
| `get_server_build_info` | Returns exact EDT-MCP runtime build information from installed OSGi bundle metadata |
| `describe_capabilities` | Describes installed runtime capabilities, registered tools/resources, and known fail-closed limitations |
| `list_projects` | Lists workspace projects with project kind, capability hints, and extension metadata |
| `get_configuration_properties` | Gets 1C configuration properties (configuration-only in this rollout) |
| `get_project_errors` | Returns EDT problems with severity/checkId/objects filters |
| `get_problem_summary` | Problem counts grouped by project and severity |
| `clean_project` | Cleans project markers and triggers full revalidation; async-first at runtime |
| `revalidate_objects` | Revalidates specific objects by FQN (e.g. "Document.MyDoc"); full-project mode is async-first at runtime |
| `get_bookmarks` | Returns workspace bookmarks |
| `get_tasks` | Returns TODO/FIXME task markers |
| `list_tasks` | List MCP tasks visible to the current `MCP-Session-Id` |
| `get_task_result` | Retrieve a retained task result or latest snapshot without re-running the original operation |
| `wait_task` | Bounded wait helper for a session-owned task; returns terminal evidence or timeout snapshot |
| `get_check_description` | Returns check documentation from .md files |
| `get_content_assist` | Get content assist proposals (type info, method hints) |
| `get_platform_documentation` | Get platform type documentation (methods, properties, constructors) |
| `get_metadata_objects` | Get list of metadata objects from 1C configuration |
| `get_metadata_details` | Get detailed properties of metadata objects (attributes, tabular sections, etc.) |
| `find_references` | Find all references to a metadata object (in metadata, BSL code, forms, roles, etc.) — top-level objects only |
| `rename_metadata_object` | Rename a metadata object or attribute with full refactoring: cascading updates in BSL code, forms, and metadata. Preview + confirm workflow; extension write/refactor remains guarded |
| `delete_metadata_object` | Delete a metadata object or attribute with reference cleanup. Preview + confirm workflow; extension write/refactor remains guarded |
| `add_metadata_attribute` | Add a new attribute to a metadata object (Catalog, Document, Register, etc.); extension write/refactor remains guarded |
| `get_tags` | Get list of all tags defined in the project with descriptions and object counts |
| `get_objects_by_tags` | Get metadata objects filtered by tags with tag descriptions and object FQNs |
| `get_extension_properties` | Extension lifecycle discovery: read extension-project root properties; use `get_extension_runtime_targets` next when a target is needed |
| `get_extension_runtime_targets` | Extension lifecycle discovery: resolve parent configuration project and `applicationId` values for inspect/apply/probe tools |
| `list_infobase_extensions` | Extension lifecycle discovery: list installed extensions in the selected target before applying or diagnosing |
| `check_extension_applicability` | Extension lifecycle guardrail: return headless-safe applicability status; fail-closed and not the apply backend |
| `apply_extension_to_infobase` | Extension lifecycle mutation: synchronize an extension project to a selected `applicationId`; async-first with final result via `tasks/result` |
| `probe_extension_sync_bridge` | Developer-oriented extension probe: invoke the internal EDT synchronization bridge used by apply for diagnostics/proof |
| `probe_extension_xml_contract` | Developer-oriented extension probe: compare exported EDT XML layout with workspace `src`; diagnostic only |
| `get_applications` | Get list of applications (infobases) for a project with update state; configuration-only in this rollout |
| `update_database` | Update database (infobase) with full or incremental update mode; async-first at runtime, explicit task augmentation, and configuration-only extension rejection |
| `run_unit_tests` | Run `YAxUnit`-backed unit tests for a configuration project/application target; async-first at runtime with retained `runId` results and optional warm-session reuse |
| `prepare_test_session` | Prepare or attach a persistent `YAxUnit` warm session for a project/application target |
| `get_test_session_status` | Inspect a persistent unit-test session by stable `sessionId` |
| `recycle_test_session` | Invalidate or terminate a persistent unit-test session by stable `sessionId` |
| `get_test_run_report` | Read retained summary, manifest, or JUnit payload for a completed unit-test run by stable `runId` |
| `get_operation_snapshot` | Get the progress snapshot for a specific tracked long-running operation by `operationId` |
| `get_active_operation` | Get the current long-running operation progress snapshot for polling fallback |
| `debug_launch` | Launch application in debug mode (auto-updates database before launch); configuration-only in this rollout |
| `list_debug_sessions` | List active supported EDT runtime debug sessions and thread summaries |
| `list_debug_breakpoints` | List supported EDT BSL line breakpoints visible to the Eclipse breakpoint manager |
| `set_debug_breakpoint` | Set a supported EDT BSL line breakpoint by project, module path, and 1-based line |
| `remove_debug_breakpoint` | Remove a supported EDT BSL line breakpoint by MCP `breakpointId`, protecting user breakpoints by default |
| `cleanup_mcp_debug_breakpoints` | Remove only MCP-owned BSL breakpoints, reporting protected user breakpoints separately |
| `get_debug_stack` | Inspect stack frames for a suspended debug thread |
| `get_debug_variables` | Inspect bounded frame variables or one expanded variable path |
| `evaluate_debug_expression` | Evaluate a bounded expression in a current suspended debug frame |
| `control_debug_session` | Dispatch basic debug actions: resume, suspend, step over/into/return, terminate |
| `run_to_debug_breakpoint` | One-shot helper: set/reuse a temporary breakpoint, launch or resume, wait boundedly for suspension |
| `get_form_screenshot` | Capture PNG screenshot of form WYSIWYG editor (embedded image resource) |
| `list_modules` | List all BSL modules in a project with module type and parent object |
| `get_module_structure` | Get BSL module structure: procedures/functions, signatures, regions, parameters |
| `read_module_source` | Read BSL module source code with line numbers (full file or line range) |
| `write_module_source` | Write BSL source code to metadata object modules (searchReplace, replace, append) with syntax check; extension writes remain guarded |
| `read_method_source` | Read a specific procedure/function from a BSL module by name |
| `search_in_code` | Full-text/regex search across BSL modules with outputMode: full/count/files |
| `get_method_call_hierarchy` | Find method callers or callees via semantic BSL analysis |
| `go_to_definition` | Navigate to symbol definition (method by name, metadata object by FQN) |
| `get_symbol_info` | Get type/hover info about a symbol at a BSL code position (inferred types, signatures, docs) |
| `validate_query` | Validate 1C query text in project context (syntax + semantic errors, optional DCS mode) |
| `diagnose_bsl_queries` | Extract and validate source-tied query text from BSL module or method scope |
| `check_form_event_contract` | Check form metadata event bindings against form module handlers |
| `probe_form_command_availability` | Fail-closed read-only guardrail for form command availability evidence |
| `probe_document_write_post_dry_run` | Fail-closed document write/post dry-run guardrail; performs no mutation until rollback is proven |
| `probe_document_movements` | Fail-closed document movement evidence probe by recorder; performs no runtime query until read-only transport is proven |

## MCP Discovery Resources

`initialize` advertises the MCP `resources` capability. The server exposes static markdown resources
through `resources/list` and `resources/read` so agents can discover multi-tool workflows without
bloating `tools/list` descriptions.

Initial resources:

- `edt-mcp://capabilities/yaxunit-runtime-testing`
- `edt-mcp://capabilities/runtime-debug-control`
- `edt-mcp://workflows/yaxunit-warm-session`
- `edt-mcp://workflows/runtime-debug-breakpoint`
- `edt-mcp://capabilities/extension-lifecycle`
- `edt-mcp://workflows/extension-apply`
- `edt-mcp://capabilities/live-acceptance-evidence`
- `edt-mcp://limitations/runtime-testing-and-debug`

These resources are guidance only. Live state for tasks, warm sessions, retained reports, debug
sessions, frames, variables, and breakpoints remains authoritative through the corresponding tools.
Unknown resource URIs fail closed with JSON-RPC error `-32002` and the requested URI in `error.data`.

## Project Kinds And Extension Support

`list_projects` remains markdown-friendly for people and now also carries additive deterministic
project records through MCP `structuredContent`. Each EDT project record exposes:

- `projectKind`: `configuration`, `extension`, or `unknown`
- `capabilityCategories`: `metadataRead`, `moduleRead`, `mutationRefactor`, `runtimeApplication`
- extension metadata when the project is an EDT extension project

Verified first-wave extension support in this rollout:

- `get_metadata_objects`
- `get_metadata_details`
- `list_modules`
- `read_module_source`
- `read_method_source`
- `get_module_structure`
- `search_in_code`

Experimental extension lifecycle discovery/runtime surface in this rollout:

- `get_extension_properties`
- `get_extension_runtime_targets`
- `list_infobase_extensions`
- `check_extension_applicability`
- `apply_extension_to_infobase`
- `probe_extension_sync_bridge` (developer-oriented internal sync probe)
- `probe_extension_xml_contract` (developer-oriented XML contract probe)

The runtime-side extension lifecycle tools use a split safety model:

- `list_infobase_extensions` enters the EDT runtime only after infobase-access preflight and fails
  with a dedicated busy category instead of hanging the transport when a previous bridge probe got
  stuck
- `check_extension_applicability` is intentionally fail-closed in MCP because live testing showed
  that the current EDT applicability-check path can open interactive infobase-access dialogs and
  destabilize the runtime bridge
- `apply_extension_to_infobase` does not use the unsafe public applicability/XML path as its
  execution backend; it uses the verified internal EDT synchronization bridge
  (`IInfobaseSynchronizationManager.updateInfobase` / `reloadInfobase`) on the extension project
  and stays on the existing task/progress runtime surface
- `probe_extension_sync_bridge` is the developer-oriented companion for that backend: it exists to
  prove or diagnose the internal synchronization path on a disposable target before widening the
  public lifecycle contract
- `probe_extension_xml_contract` is a guarded diagnostics tool: it exports the selected extension
  through EDT XML export APIs and compares the exported layout with the workspace `src` tree; this
  is meant for implementation proof and staging decisions, not for regular lifecycle automation

Stable extension failure categories:

- `configuration_only`: runtime/application flows and `get_configuration_properties` stay configuration-only
- `unsupported_extension_operation`: the tool is outside the verified extension matrix for this rollout
- `extension_model_unavailable`: EDT did not provide the extension-compatible metadata or BSL model needed by a supported read path
- `extension_parent_missing`: the extension project has no usable parent configuration project for runtime routing
- `extension_target_not_found`: the requested runtime target application could not be resolved
- `extension_runtime_service_unavailable`: EDT runtime execution services are not available in the current environment
- `extension_runtime_access_settings_required`: the selected infobase target is missing valid access settings for the EDT runtime bridge
- `extension_runtime_bridge_busy`: the extension runtime bridge is already busy or a previous probe timed out and EDT should be restarted before retry
- `extension_runtime_headless_unsafe`: the current EDT runtime path is intentionally disabled for MCP because it is not headless-safe
- `extension_runtime_check_failed`: the runtime-side inspection/check command failed while talking to Designer/thick client
- `extension_apply_failed`: the dedicated extension apply flow failed while synchronizing the extension project to the target infobase

Current non-goals for extension projects in this rollout:

- no extension debug-launch flow: `get_applications`, `update_database`, and `debug_launch` remain configuration-only legacy tools
- no extension configuration-properties contract: `get_configuration_properties`
- no extension mutation/refactor flows: `add_metadata_attribute`, `rename_metadata_object`, `delete_metadata_object`, `write_module_source`
- no advanced semantic navigation outside the verified matrix: `find_references`, `go_to_definition`, `get_method_call_hierarchy`, `get_symbol_info`, `get_content_assist`

<details>
<summary><strong>Tool Details</strong> - Parameters and usage examples for each tool</summary>

### Content Assist Tool

**`get_content_assist`** - Get content assist proposals at a specific position in BSL code. Returns type information, available methods, properties, and platform documentation.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `filePath` | Yes | Path relative to `src/` folder (e.g. `CommonModules/MyModule/Module.bsl`) |
| `line` | Yes | Line number (1-based) |
| `column` | Yes | Column number (1-based) |
| `limit` | No | Maximum proposals to return (default: from preferences) |
| `offset` | No | Skip first N proposals (for pagination, default: 0) |
| `contains` | No | Filter by display string containing these substrings (comma-separated, e.g. `Insert,Add`) |
| `extendedDocumentation` | No | Return full documentation (default: false, only display string) |

**Important Notes:**
1. **Save the file first** - EDT must read the current content from disk to provide accurate proposals
2. **Column position** - Place cursor after the dot (`.`) for method/property suggestions
3. **Pagination** - Use `offset` to get next batch of proposals (e.g., first call with limit=5, second call with offset=5, limit=5)
4. **Filtering** - Use `contains` to filter by method/property name (case-insensitive)
5. **Works for:**
   - Global platform methods (e.g. `NStr(`, `Format(`)
   - Methods after dot (e.g. `Structure.Insert`, `Array.Add`)
   - Object properties and fields
   - Configuration objects and modules

### Validation Tools

- **`clean_project`**: Refreshes project from disk, clears all validation markers, and triggers full revalidation using EDT's ICheckScheduler
- **`revalidate_objects`**: Revalidates specific metadata objects by their FQN:
  - `Document.MyDocument`, `Catalog.MyCatalog`, `CommonModule.MyModule`
  - `Document.MyDoc.Form.MyForm` for nested objects
- **`validate_query`**: Validates query language text in project context and returns syntax/semantic errors.
  - Parameters: `projectName` (required), `queryText` (required), `dcsMode` (optional, default `false`)
  - Use `dcsMode=true` for Data Composition System (DCS) queries
- **`diagnose_bsl_queries`**: Extracts static query text assignments from BSL module/method scope,
  validates them in project context, and returns source locations plus extraction limitations.
- **`check_form_event_contract`**: Compares form metadata event bindings with form module handlers so
  handler existence is not mistaken for actual event wiring.
- **`probe_form_command_availability`**: Returns bounded fail-closed live evidence for form command
  availability; unsupported runtime command state is reported explicitly.
- **`probe_document_write_post_dry_run`**: Returns `unsupported_safe_dry_run` and performs no
  write/post until rollback and side-effect isolation are proven.
- **`probe_document_movements`**: Returns recorder-scoped document movement evidence when a
  headless-safe read transport is proven; otherwise returns explicit `unsupported` evidence without
  executing runtime queries, writes, posts, or arbitrary client-supplied query text.

### Project Errors Tool

**`get_project_errors`** - Get detailed configuration problems from EDT with multiple filter options.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | No | Filter by project name |
| `severity` | No | Filter by severity: `ERRORS`, `BLOCKER`, `CRITICAL`, `MAJOR`, `MINOR`, `TRIVIAL` |
| `checkId` | No | Filter by check ID substring (e.g. `ql-temp-table-index`) |
| `objects` | No | Filter by object FQNs (array). Returns errors only from specified objects |
| `limit` | No | Maximum results (default: 100, max: 1000) |

**Objects filter format:**
- Array of FQN strings: `["Document.SalesOrder", "Catalog.Products"]`
- Case-insensitive partial matching
- Matches against error location (objectPresentation)
- FQN examples:
  - `Document.SalesOrder` - all errors in document
  - `Catalog.Products` - all errors in catalog
  - `CommonModule.MyModule` - all errors in common module
  - `Document.SalesOrder.Form.ItemForm` - errors in specific form

### Platform Documentation Tool

**`get_platform_documentation`** - Get documentation for platform types (ValueTable, Array, Structure, Query, etc.) and built-in functions (FindFiles, Message, Format, etc.)

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `typeName` | Yes | Type or function name (e.g. `ValueTable`, `Array`, `FindFiles`, `Message`) |
| `category` | No | Category: `type` (platform types), `builtin` (built-in functions). Default: `type` |
| `projectName` | No | EDT project name (uses first available project if not specified) |
| `memberName` | No | Filter by member name (partial match) - only for `type` category |
| `memberType` | No | Filter: `method`, `property`, `constructor`, `event`, `all` (default: `all`) - only for `type` category |
| `language` | No | Output language: `en` or `ru` (default: `en`) |
| `limit` | No | Maximum results (default: 50) - only for `type` category |

### Metadata Objects Tool

**`get_metadata_objects`** - Get list of metadata objects from 1C configuration.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `metadataType` | No | Filter: `all`, `documents`, `catalogs`, `informationRegisters`, `accumulationRegisters`, `commonModules`, `enums`, `constants`, `reports`, `dataProcessors`, `exchangePlans`, `businessProcesses`, `tasks`, `commonAttributes`, `eventSubscriptions`, `scheduledJobs` (default: `all`) |
| `nameFilter` | No | Partial name match filter (case-insensitive) |
| `limit` | No | Maximum results (default: 100) |
| `language` | No | Language code for synonyms (e.g. `en`, `ru`). Uses configuration default if not specified |

### Metadata Details Tool

**`get_metadata_details`** - Get detailed properties of metadata objects.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `objectFqns` | Yes | Array of FQNs (e.g. `["Catalog.Products", "Document.SalesOrder"]`) |
| `full` | No | Return all properties (`true`) or only key info (`false`). Default: `false` |
| `language` | No | Language code for synonyms. Uses configuration default if not specified |

### Find References Tool

**`find_references`** - Find all references to a metadata object. Returns all places where the object is used: in other metadata objects, BSL code, forms, roles, subsystems, etc. Matches EDT's built-in "Find References" functionality.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `objectFqn` | Yes | Fully qualified name (e.g. `Catalog.Products`, `Document.SalesOrder`, `CommonModule.Common`) |
| `limit` | No | Maximum results per category (default: 100, max: 500) |

**Returns markdown with references in EDT-compatible format:**

```markdown
# References to Catalog.Items

**Total references found:** 122

- Catalog.ItemKeys - Attributes.Item.Type - Type: types
- Catalog.ItemKeys.Form.ChoiceForm.Form - Items.List.Item.Data path - Type: types
- Catalog.Items - Attributes.PackageUnit.Choice parameter links - Ref
- Catalog.Items.Form.ItemForm.Form - Items.GroupTop.GroupMainAttributes.Code.Data path - Type: types
- CommonAttribute.Author - Content - metadata
- Configuration - Catalogs - catalogs
- DefinedType.typeItem - Type - Type: types
- EventSubscription.BeforeWrite_CatalogsLockDataModification - Source - Type: types
- Role.FullAccess.Rights - Role rights - object
- Subsystem.Settings.Subsystem.Items - Content - content

### BSL Modules

- CommonModules/GetItemInfo/Module.bsl [Line 199; Line 369; Line 520]
- Catalogs/Items/Forms/ListForm/Module.bsl [Line 18; Line 19]
```

**Reference types included:**
- **Metadata references** - Attributes, form items, command parameters, type descriptions
- **Type usages** - DefinedTypes, ChartOfCharacteristicTypes, type compositions
- **Common attributes** - Objects included in common attribute content
- **Event subscriptions** - Source objects for subscriptions
- **Roles** - Objects with role permissions
- **Subsystems** - Subsystem content
- **BSL code** - References in BSL modules with line numbers

> **Note:** `find_references` supports top-level metadata objects only (e.g. `Catalog.DataAreas`, `CommonModule.Saas`). Passing a sub-object FQN such as `Catalog.DataAreas.Attribute.DataAreaStatus` returns a descriptive error indicating that sub-objects are not supported. Use `rename_metadata_object` or `delete_metadata_object` to work with attributes and nested objects.

### Metadata Refactoring Tools

#### Rename Metadata Object Tool

**`rename_metadata_object`** - Rename a metadata object or attribute with full refactoring support. All references in BSL code, forms, and metadata are updated automatically.

**Workflow:**
1. Call without `confirm` to preview all change points
2. Review change point indices and optionally skip some with `disableIndices`
3. Call with `confirm=true` to apply

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `objectFqn` | Yes | FQN of the object to rename. Top-level: `Catalog.Products`. Nested: `Document.SalesOrder.Attribute.Amount` |
| `newName` | Yes | New name for the object |
| `confirm` | No | `true` to execute the rename. Default `false` = preview only |
| `disableIndices` | No | Comma-separated indices of optional change points to skip (e.g. `'2,3,5'`) |
| `maxResults` | No | Max change points to show in preview (default: 20, `0` = no limit) |

**Supported child types in FQN:** `Attribute`, `TabularSection`, `Dimension`, `Resource`

#### Delete Metadata Object Tool

**`delete_metadata_object`** - Delete a metadata object or attribute. References in BSL code, forms, and other metadata are cleaned up automatically.

**Workflow:**
1. Call without `confirm` to preview affected references and problems
2. Call with `confirm=true` to apply

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `objectFqn` | Yes | FQN of the object to delete (e.g. `Catalog.Products`, `Document.SalesOrder.Attribute.Amount`) |
| `confirm` | No | `true` to execute the deletion. Default `false` = preview only |

#### Add Metadata Attribute Tool

**`add_metadata_attribute`** - Add a new attribute to a metadata object via BM write transaction. The attribute is created with default properties.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `parentFqn` | Yes | FQN of the parent object (e.g. `Catalog.Products`, `Document.SalesOrder`) |
| `attributeName` | Yes | Name for the new attribute |

**Supported parent types:** `Catalog`, `Document`, `ExchangePlan`, `ChartOfCharacteristicTypes`, `ChartOfAccounts`, `ChartOfCalculationTypes`, `BusinessProcess`, `Task`, `DataProcessor`, `Report`, `InformationRegister`, `AccumulationRegister`, `AccountingRegister`

### Tag Management Tools

#### Get Tags Tool

**`get_tags`** - Get list of all tags defined in the project. Tags are user-defined labels for organizing metadata objects.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |

**Returns:** Markdown table with tag name, color, description, and number of assigned objects.

#### Get Objects By Tags Tool

**`get_objects_by_tags`** - Get metadata objects filtered by tags. Returns objects that have any of the specified tags.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `tags` | Yes | Array of tag names to filter by (e.g. `["Important", "NeedsReview"]`) |
| `limit` | No | Maximum objects per tag (default: 100) |

**Returns:** Markdown with sections for each tag including:
- Tag color and description
- Table of object FQNs assigned to the tag
- Summary with total objects found

### Application Management Tools

#### Get Extension Properties Tool

**`get_extension_properties`** - Get extension-project root configuration properties through EDT's extension-aware project model.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | Extension project name |

#### Get Extension Runtime Targets Tool

**`get_extension_runtime_targets`** - Resolve the parent configuration project and available infobase applications for an extension project.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | Extension project name |

#### List Infobase Extensions Tool

**`list_infobase_extensions`** - List configuration extensions currently installed in a selected infobase target for an extension project.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | Extension project name |
| `applicationId` | Yes | Application ID from `get_extension_runtime_targets` |

**Notes:**
- Fails with `extension_runtime_access_settings_required` if EDT has no valid stored infobase access settings for that target
- Fails with `extension_runtime_bridge_busy` instead of hanging if a previous runtime probe wedged the EDT bridge; restart EDT before retrying

#### Check Extension Applicability Tool

**`check_extension_applicability`** - Check whether the selected infobase target can apply the workspace extension.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | Extension project name |
| `applicationId` | Yes | Application ID from `get_extension_runtime_targets` |

**Notes:**
- Returns `applicable=false` with `extension_runtime_headless_unsafe`
- Does not enter the EDT runtime bridge from MCP because live testing showed that the current
  applicability-check path can open interactive infobase-access dialogs and wedge the bridge

#### Apply Extension To Infobase Tool

**`apply_extension_to_infobase`** - Apply an extension project to a selected infobase target through the dedicated extension lifecycle flow.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | Extension project name |
| `applicationId` | Yes | Application ID from `get_extension_runtime_targets` |
| `fullReload` | No | If `true`, use full reload instead of incremental synchronization (default: `false`) |
| `autoRestructure` | No | Automatically apply restructurization if needed (default: `true`) |

**Notes:**
- Async-first at runtime: bare calls auto-promote into task-backed execution, and the final result is retrieved through `tasks/result`
- Uses the internal EDT synchronization bridge on the extension project rather than the unsafe public applicability/XML path
- Conflicts with mutable synchronization work on the same parent configuration target through task scheduling and busy-state diagnostics

#### Probe Extension Sync Bridge Tool

**`probe_extension_sync_bridge`** - Developer-oriented probe that invokes the internal EDT synchronization bridge for an extension project on a selected target.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | Extension project name |
| `applicationId` | Yes | Application ID from `get_extension_runtime_targets` |
| `fullReload` | No | Use `reloadInfobase` instead of incremental `updateInfobase` (default: `false`) |
| `autoConfirmRestructure` | No | Automatically confirm database restructurization if EDT asks for it (default: `false`) |
| `allowDrift` | No | Allow probing when the target is not already `UPDATED` (default: `false`) |
| `timeoutSeconds` | No | Guard timeout in seconds for the probe (default: `30`, max: `300`) |

**Notes:**
- Intended for live proof and diagnostics of the internal synchronization path, not for normal client automation
- With `allowDrift=false`, it fails closed on `INCREMENTAL_UPDATE_REQUIRED` / `FULL_UPDATE_REQUIRED` targets instead of mutating them
- This is the developer-facing proof surface behind the public `apply_extension_to_infobase` contract

#### Probe Extension XML Contract Tool

**`probe_extension_xml_contract`** - Developer-oriented probe that exports the selected extension
from a target infobase via EDT XML export APIs and compares the exported directory tree with the
workspace `src` layout.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | Extension project name |
| `applicationId` | Yes | Application ID from `get_extension_runtime_targets` |
| `sampleLimit` | No | Maximum number of sample paths per diff bucket (default: 20, max: 100) |
| `cleanupExport` | No | Delete the temporary exported XML tree after comparison (default: false) |

**Notes:**
- Uses `exportConfigurationToXml(..., HIERARCHICAL, PLAIN_FILES)` under the same guarded runtime bridge as `list_infobase_extensions`
- Intended for implementation proof and diagnostics; it does not apply or mutate the infobase
- Current live demo evidence shows that EDT workspace `src` does not match the XML export/import layout directly, so a future apply flow needs a separate export/staging adapter

#### Get Applications Tool

**`get_applications`** - Get list of applications (infobases) for a project. Returns application ID, name, type, and current update state. Use this to get application IDs for `update_database` and `debug_launch` tools.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |

#### Update Database Tool

**`update_database`** - Update database (infobase) configuration. Supports full and incremental update modes.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `applicationId` | Yes | Application ID from `get_applications` |
| `fullUpdate` | No | If true - full reload, if false - incremental update (default: false) |
| `autoRestructure` | No | Automatically apply restructurization if needed (default: true) |

**Progress behavior:**
- Tracks stage-aware runtime progress in the EDT status bar
- Emits MCP `notifications/progress` only when the client supplies `_meta.progressToken`
- Is async-first at runtime: a bare `tools/call` request auto-promotes into task-backed execution
- Keeps `execution.taskSupport: "optional"` for explicit task augmentation instead of advertising MCP `required`
- Returns `CreateTaskResult` first; the final task result is retrieved via `tasks/result` in the same MCP session
- `tasks/get` and `tasks/list` provide status polling during execution

Typical stages:
- `validation`
- `sync_state_check`
- `update_start`
- `waiting_for_edt`
- `final_state_check`
- `completion` / `failure`

#### Run Unit Tests Tool

**`run_unit_tests`** - Run `YAxUnit`-backed unit tests for a configuration project and runtime application target.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT configuration project name |
| `applicationId` | Yes | Application ID from `get_applications` |
| `provider` | No | Supported provider (`yaxunit` only in this rollout) |
| `sessionMode` | No | `cold`, `prefer_warm`, `require_warm`, or `recycle_then_run` (default: `cold`) |
| `scope` | No | `all`, `module`, `suite`, or `test` |
| `testExtension` | No | YAxUnit test extension/filter root (default: `tests`) |
| `testModule` | No | Required for `scope=module` |
| `testPath` | No | Required for `scope=test` |
| `suiteName` | No | Required for `scope=suite` |
| `tagsInclude` | No | Optional YAxUnit tags filter |
| `updateBeforeRun` | No | If true, perform incremental infobase update before launch |
| `timeoutSeconds` | No | Bounded EDT runtime wait before the tool fails closed |

**Contract notes:**
- Only configuration-project targets are supported in the first rollout
- Bare calls auto-promote into task-backed execution
- The final task result returns a stable `runId`, machine-readable status, summary counts, and retained report formats
- `sessionMode=cold` always uses the existing cold launch path
- `sessionMode=prefer_warm` reuses a healthy prepared session when available and otherwise falls back to cold launch
- `sessionMode=require_warm` fails closed with `sessionOutcome=stale_rejected` when a matching session is missing, busy, stale, dead, or not backed by a provider bridge
- `sessionMode=recycle_then_run` recycles the matching session before starting a new run
- Warm reuse is an optimization, not a correctness shortcut: infobase update before a run invalidates matching warm sessions
- Current warm YAxUnit RPC execution supports one common-module run at a time (`scope=module` or `scope=test`); broader scopes can still use cold launch
- `tagsExclude`, BDD/scenario flows, extension-project targets, and debug-mode execution stay fail-closed in this rollout
- If the EDT runtime bridge or YAxUnit engine is unavailable, the tool returns an actionable failure instead of launching partially

Typical stages:
- `validation`
- `preflight`
- `optional_update`
- `prepare_provider`
- `launch`
- `parse_report`
- `completion` / `failure`

#### Unit Test Session Tools

**`prepare_test_session`** - Prepare or attach a persistent `YAxUnit` warm session for a configuration project/application target.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT configuration project name |
| `applicationId` | Yes | Application ID from `get_applications` |
| `provider` | No | Supported provider (`yaxunit` only in this rollout) |

**Returns:** stable `sessionId`, lifecycle `state`, target identity, reuse scope, and provider correlation such as RPC transport/port and Enterprise process id.

**`get_test_session_status`** - Return current state for a persistent unit-test session by `sessionId`.

**`recycle_test_session`** - Mark stale, terminate, or replace a persistent unit-test session by `sessionId`.

Warm-session states are `starting`, `ready`, `busy`, `stale`, and `dead`. Stale reasons are explicit (`infobase_sync_performed`, `heartbeat_lost`, `explicit_recycle`, `provider_error`, etc.) so clients can decide whether to retry, recycle, or use cold launch.

#### Get Test Run Report Tool

**`get_test_run_report`** - Return a retained unit-test summary, report manifest, or JUnit XML payload by `runId`.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `runId` | Yes | Stable run identifier from `run_unit_tests` |
| `format` | No | `summary`, `manifest`, or `junit` (default: `summary`) |

**Returns:**
- `found=false` for unknown or expired `runId`
- summary counts and status for `format=summary`
- report availability/retention metadata for `format=manifest`
- retained raw JUnit XML for `format=junit`

**`get_operation_snapshot`** - Return a JSON snapshot for a specific tracked long-running EDT operation by stable `operationId`. Useful after detached-continuation or blocking-operation hints already provided the exact operation identity.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `operationId` | Yes | Stable operation ID from progress/task metadata |

**Returns:**
- `found` - whether the requested operation is still tracked
- `operationId`, `toolName`, `status`, `detached`, `stage`, `message`
- `progress`, `total`, `indeterminate`
- `elapsedSeconds`, `startedAt`
- `details`, `recentEvents`

#### Get Active Operation Tool

**`get_active_operation`** - Return a JSON snapshot of the currently focused long-running EDT operation, if any. Useful as a polling fallback when the client does not yet know an `operationId`.

**Parameters:** none

**Returns:**
- `active` - whether an operation is currently tracked
- `toolName`, `status`, `stage`, `message`
- `progress`, `total`, `indeterminate`
- `elapsedSeconds`, `startedAt`
- `recentEvents`

#### Debug Launch Tool

**`debug_launch`** - Launch application in debug mode. Automatically updates database before launching and finds existing launch configuration.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `applicationId` | Yes | Application ID from `get_applications` |
| `updateBeforeLaunch` | No | If true - update database before launching (default: true) |

**Notes:**
- Requires a launch configuration to be created in EDT first (Run → Run Configurations...)
- If no configuration exists, returns list of available configurations
- `updateBeforeLaunch=true` skips update if database is already up to date
- `debug_launch` is intentionally sync-first; task-style debug session lifecycle is handled separately from the MCP Tasks rollout

#### Runtime Debug Control Tools

**`list_debug_sessions`** - List active supported EDT runtime debug sessions. A supported session is an active Eclipse debug launch with launch type `com._1c.g5.v8.dt.launching.core.RuntimeClient`, resolvable EDT project/application attributes, and Eclipse debug model elements.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | No | Optional EDT project name filter |
| `applicationId` | No | Optional application ID filter |

**`list_debug_breakpoints`** - List supported EDT BSL line breakpoints visible to the Eclipse breakpoint manager.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | No | Optional EDT project name filter |
| `modulePath` | No | Optional BSL module path relative to `src` |

**`set_debug_breakpoint`** - Set a supported EDT BSL line breakpoint.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `modulePath` | Yes | BSL module path relative to `src`, e.g. `Documents/Заказ/Forms/ФормаДокумента/Module.bsl` |
| `lineNumber` | Yes | 1-based source line number |
| `persisted` | No | Persist the breakpoint in the EDT workspace. Default: `false` for MCP-created breakpoints |

**`remove_debug_breakpoint`** - Remove a supported EDT BSL line breakpoint by `breakpointId`.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `breakpointId` | Yes | Breakpoint ID returned by `list_debug_breakpoints` or `set_debug_breakpoint` |
| `removeUserBreakpoint` | No | Allow removal of a pre-existing breakpoint not created by MCP. Default: `false` |

**`cleanup_mcp_debug_breakpoints`** - Remove only MCP-owned supported EDT BSL line breakpoints.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | No | Optional EDT project name filter |
| `modulePath` | No | Optional BSL module path relative to `src` |
| `dryRun` | No | Report matching breakpoints without deleting them. Default: `false` |

**`get_debug_stack`** - Read stack frames for a suspended thread returned by `list_debug_sessions`.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `threadId` | Yes | Thread ID returned by `list_debug_sessions` |
| `maxFrames` | No | Maximum frames to return (default 100, max 200) |

**`get_debug_variables`** - Read bounded variables for a suspended frame returned by `get_debug_stack`.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `frameId` | Yes | Frame ID returned by `get_debug_stack` |
| `variablePath` | No | Optional variable path array to expand from the frame |
| `maxVariables` | No | Maximum variables to return (default 100, max 200) |

**`evaluate_debug_expression`** - Evaluate a bounded BSL expression in a suspended frame returned by `get_debug_stack`.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `frameId` | Yes | Frame ID returned by `get_debug_stack` |
| `expression` | Yes | BSL expression evaluated in the current suspended frame context |
| `timeoutSeconds` | No | Bounded evaluation wait (default 5, max 60) |
| `maxValueLength` | No | Maximum value string length (default 500, max 4000) |
| `maxChildren` | No | Maximum child variables to include for object values (default 20, max 200) |

The response always reports `sideEffectFreeGuaranteed=false`: EDT/BSL watch-expression evaluation
does not prove that an expression is side-effect-free, so agents must not treat it as a pure proof
surface.

**`control_debug_session`** - Dispatch a basic debug action and return immediate state plus a polling hint.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `action` | Yes | `resume`, `suspend`, `step_over`, `step_into`, `step_return`, or `terminate` |
| `threadId` | No | Required for `resume`, `suspend`, and step actions; returned by `list_debug_sessions` |
| `sessionId` | No | Session ID returned by `list_debug_sessions`; usable for session-level `terminate` |

**`run_to_debug_breakpoint`** - Set or reuse a temporary MCP-owned BSL line breakpoint, then launch or resume and wait until a supported thread suspends at that source line or the timeout expires.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `modulePath` | Yes | BSL module path relative to `src` |
| `lineNumber` | Yes | 1-based source line number |
| `applicationId` | No | Application ID for launch mode; optional when `threadId` is provided |
| `threadId` | No | Existing thread ID to resume; if omitted, `applicationId` is launched |
| `updateBeforeLaunch` | No | If launching, update database first. Default: `true` |
| `timeoutSeconds` | No | Bounded wait for suspend at the target line (default 30, max 300) |
| `maxVariables` | No | Maximum variables to return from the matched top frame (default 100, max 200) |
| `cleanupOnTimeout` | No | Remove a newly-created temporary breakpoint on timeout. Default: `true` |
| `cleanupOnSuspend` | No | Remove a newly-created temporary breakpoint after a match. Default: `false` |

**Runtime matrix and limitations:**
- Initial live discovery on EDT 2024.2.5.16 observed 1C debug model classes `RuntimeDebugTargetThread`, `BslStackFrame`, `BslVariable`, `BslPrimitiveValue`, and `BslValuePath`.
- The MCP bridge intentionally compiles against standard Eclipse `org.eclipse.debug.core.model` interfaces instead of 1C internal debug classes.
- Stack and variables require a suspended thread/frame. Running or stale snapshots fail explicitly instead of fabricating partial data.
- EDT target-level suspend can report a suspended debug target without thread stack frames; use thread-level control after an actual breakpoint/suspension point.
- Variable reads are local trusted-tool operations; returned values may include application data visible to the debugger.
- Expression evaluation uses the EDT/Eclipse watch-expression delegate for the frame debug model and is bounded by timeout/result-size controls.
- Expression evaluation may call BSL functions or getters depending on the expression and backend; responses and metadata do not claim read-only semantics.
- Breakpoint management is limited to EDT BSL line breakpoints backed by workspace `.bsl` files.
- MCP-created breakpoints are non-persisted by default, carry an MCP ownership marker, and can be removed without deleting pre-existing user breakpoints.
- `breakpointId` values are stable only within the current EDT workspace session/snapshot cache; refresh with `list_debug_breakpoints` after EDT restart or workspace reload.
- Conditional breakpoints, hit-count conditions, and value mutation are outside this rollout.

**Live verification command shape:**

```bash
curl -sS -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_debug_sessions","arguments":{}}}' \
  "http://<edt-host>:<port>/mcp"
```

### BSL Code Analysis Tools

#### List Modules Tool

**`list_modules`** - List all BSL modules in an EDT project. Can filter by metadata type or specific object name. Returns module path, type, and parent object.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `metadataType` | No | Filter: `all`, `documents`, `catalogs`, `commonModules`, `informationRegisters`, `accumulationRegisters`, `reports`, `dataProcessors`, `exchangePlans`, `businessProcesses`, `tasks`, `constants`, `commonCommands`, `commonForms`, `webServices`, `httpServices` (default: `all`) |
| `objectName` | No | Name of specific metadata object to list modules for (e.g. `Products`) |
| `nameFilter` | No | Substring filter on module path (case-insensitive) |
| `limit` | No | Maximum results (default: 200, max: 1000) |

#### Get Module Structure Tool

**`get_module_structure`** - Get structure of a BSL module: all procedures/functions with signatures, line numbers, regions, execution context (`&AtServer`, `&AtClient`), export flag, and parameters.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `modulePath` | Yes | Path from `src/` folder (e.g. `CommonModules/MyModule/Module.bsl`) |
| `includeVariables` | No | Include module-level variable declarations (default: `false`) |
| `includeComments` | No | Include doc-comments for methods (default: `false`) |

**Returns:** Markdown with:

- Module summary (procedure/function counts, total lines)
- Regions list with line ranges
- Methods table: type, name, export, context, lines, parameters, region, description (when `includeComments=true`)
- Variables table: name, export flag, line, region (when `includeVariables=true`)

#### Read Module Source Tool

**`read_module_source`** - Read BSL module source code from EDT project. Returns source with line numbers. Supports reading full file or a specific line range. Max 5000 lines per call.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `modulePath` | Yes | Path from `src/` folder (e.g. `CommonModules/MyModule/Module.bsl` or `Documents/SalesOrder/ObjectModule.bsl`) |
| `startLine` | No | Start line number (1-based, inclusive). If omitted, reads from beginning |
| `endLine` | No | End line number (1-based, inclusive). If omitted, reads to end |

#### Write Module Source Tool

**`write_module_source`** - Write BSL source code to 1C metadata object modules. Modes: searchReplace (content-based find and replace, default), replace (replace entire file), append (add to end). Specify modulePath or objectName + moduleType. Automatically checks BSL syntax before writing.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `modulePath` | No* | Path from `src/` folder (e.g. `Documents/MyDoc/ObjectModule.bsl`). Alternative to objectName + moduleType |
| `objectName` | No* | Full object name (e.g. `Document.MyDoc`, `CommonModule.MyModule`). Supports Russian names |
| `moduleType` | No | Module type: `ObjectModule` (default), `ManagerModule`, `FormModule`, `CommandModule`, `RecordSetModule` |
| `source` | Yes | BSL source code to write. For `searchReplace`: new code replacing `oldSource`. For `replace`: complete module content. For `append`: code to add |
| `oldSource` | No** | Existing code to find and replace (required for `searchReplace` mode). Must match exactly one location in the file. Serves as proof that you have read the current file content |
| `mode` | No | Write mode: `searchReplace` (default), `replace`, `append` |
| `formName` | No | Form name, required when `moduleType=FormModule` |
| `commandName` | No | Command name, required when `moduleType=CommandModule` |
| `skipSyntaxCheck` | No | Skip BSL syntax validation (default: `false`). Checks balanced `Procedure/EndProcedure`, `Function/EndFunction`, `If/EndIf`, `While/EndDo`, `For/EndDo`, `Try/EndTry` |

*One of `modulePath` or `objectName` is required.

**Required for `searchReplace` mode.

**Notes:**

- **Content-based editing**: `searchReplace` mode finds `oldSource` in the file and replaces it with `source`. If `oldSource` is not found or matches multiple locations, the operation fails safely. This eliminates line-number drift issues when making multiple edits
- Creates new module file if it does not exist (only in `replace` mode)
- Preserves UTF-8 BOM encoding
- Syntax check validates the complete resulting file, not just the inserted fragment

#### Read Method Source Tool

**`read_method_source`** - Read a specific procedure/function from a BSL module by name. Returns method source code with line numbers and signature. If method not found, returns list of all available methods.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `modulePath` | Yes | Path from `src/` folder (e.g. `CommonModules/MyModule/Module.bsl`) |
| `methodName` | Yes | Name of the procedure/function to read (case-insensitive) |

**Returns:** Method source code with:

- Method type (Procedure/Function), signature, export flag
- Line range and line count
- Source code with line numbers

#### Search in Code Tool

**`search_in_code`** - Full-text search across all BSL modules in a project. Supports plain text and regex patterns, case sensitivity, context lines around matches, and file path filtering.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `query` | Yes | Search string or regex pattern |
| `caseSensitive` | No | Case-sensitive search (default: `false`) |
| `isRegex` | No | Treat query as regular expression (default: `false`) |
| `maxResults` | No | Maximum number of matches to return with context (default: 100, max: 500) |
| `contextLines` | No | Lines of context before/after each match (default: 2, max: 5) |
| `fileMask` | No | Filter by module path substring (e.g. `CommonModules` or `Documents/SalesOrder`) |
| `outputMode` | No | Output mode: `full` (matches with context, default), `count` (only total count, fast), `files` (file list with match counts, no context) |
| `metadataType` | No | Filter by metadata type: `documents`, `catalogs`, `commonModules`, `informationRegisters`, `accumulationRegisters`, `reports`, `dataProcessors`, `exchangePlans`, `businessProcesses`, `tasks`, `constants`, `commonCommands`, `commonForms`, `webServices`, `httpServices` |

#### Get Method Call Hierarchy Tool

**`get_method_call_hierarchy`** - Find method call hierarchy: who calls this method (callers) or what this method calls (callees). Uses semantic BSL analysis via BM-index, not text search.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `modulePath` | Yes | Path from `src/` folder (e.g. `CommonModules/MyModule/Module.bsl`) |
| `methodName` | Yes | Name of the procedure/function (case-insensitive) |
| `direction` | No | `callers` (who calls this method, default) or `callees` (what this method calls) |
| `limit` | No | Maximum results (default: 100, max: 500) |

**Notes:**

- Requires EMF model (BSL AST) — does not work in text fallback mode
- `callers` uses IReferenceFinder to search across the entire project
- `callees` traverses the method's AST to find all invocations

### Go To Definition Tool

**`go_to_definition`** - Navigate to the definition of a symbol. Resolves method calls like `CommonModuleName.MethodName` to the actual definition with source code, signature, and location. Also resolves metadata object FQNs like `Catalog.Products`. Supports both English and Russian metadata type names.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `symbol` | Yes | Symbol to find definition for. Formats: `ModuleName.MethodName` (method in a common module), `MethodName` (method in context module, requires `modulePath`), `Catalog.Products` (metadata object FQN). Russian metadata type names are also supported |
| `modulePath` | No | Context module path from `src/` folder (e.g. `Documents/SalesOrder/ObjectModule.bsl`). Required when symbol is an unqualified method name |
| `includeSource` | No | Include method source code in the response (default: `true`) |

**Returns:** Markdown with:

- Method signature, export flag, line range
- Source code with line numbers (when `includeSource=true`)
- File path for navigation
- For metadata objects: FQN, synonym, available modules

### Get Symbol Info Tool

**`get_symbol_info`** - Get type and hover information about a symbol at a specific position in a BSL module. Returns inferred types, signatures, and documentation — the same info that EDT shows on mouse hover. Useful for understanding variable types in dynamically-typed BSL code.

**Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectName` | Yes | EDT project name |
| `filePath` | Yes | Path to BSL file relative to project's `src/` folder (e.g. `CommonModules/MyModule/Module.bsl`) |
| `line` | Yes | Line number (1-based) |
| `column` | Yes | Column number (1-based) |

**Returns:** Markdown with symbol information. Uses a multi-level approach:

1. **Editor hover** (best): Returns inferred types, method signatures, documentation — same as IDE hover tooltip
2. **EObject analysis** (fallback): Returns structural info — symbol kind, name, signature, export flag, line range
3. **EMF model** (last resort): Basic node info without opening editor

**Use cases:**

- Determine the inferred type of a variable (BSL is dynamically typed)
- Get method signature and documentation at a call site
- Inspect property types on objects accessed via dot notation
- Understand platform method parameter types

### Output Formats

- **Markdown tools**: return Markdown as EmbeddedResource with `mimeType: text/markdown`; selected tools can additionally attach additive `structuredContent` for deterministic discovery or stable failure categories (`list_projects` is the primary discovery example)
- **MCP resources**: `resources/list` and `resources/read` expose static markdown capability/workflow resources; these are separate from tool-call EmbeddedResource payloads and never expose live runtime state
- **JSON tools**: `get_server_build_info`, `describe_capabilities`, `get_configuration_properties`, `get_extension_properties`, `get_extension_runtime_targets`, `list_infobase_extensions`, `check_extension_applicability`, `apply_extension_to_infobase`, `probe_extension_sync_bridge`, `probe_extension_xml_contract`, `clean_project`, `revalidate_objects`, `run_unit_tests`, `list_tasks`, `get_task_result`, `wait_task`, `get_test_run_report`, `diagnose_bsl_queries`, `check_form_event_contract`, `probe_form_command_availability`, `probe_document_write_post_dry_run`, `probe_document_movements` - return JSON with `structuredContent`
- **Text tools**: `get_edt_version` - return plain text

</details>

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp` | POST | MCP JSON-RPC (`initialize`, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `tasks/get`, `tasks/list`, `tasks/result`, `tasks/cancel`) |
| `/mcp` | GET | Server info |
| `/health` | GET | Health check |

## Metadata Tags

Organize your metadata objects with custom tags for easier navigation and filtering.

### Why Use Tags?

Tags help you:
- Group related objects across different metadata types (e.g., all objects for a specific feature)
- Quickly find objects in large configurations
- Filter the Navigator to focus on specific areas of the project
- Share object organization with your team via version control

### Getting Started

**Assigning Tags to Objects:**

1. Right-click on any metadata object in the Navigator
2. Select **Tags** from the context menu
3. Check the tags you want to assign, or select **Manage Tags...** to create new ones

![Tags Context Menu](img/tags-context-menu.png)

**Managing Tags:**

In the Manage Tags dialog you can:
- Create new tags with custom names, colors, and descriptions
- Edit existing tags (name, color, description)
- Delete tags
- See all available tags for the project

![Manage Tags Dialog](img/tags-manage-dialog.png)

### Viewing Tags in Navigator

Tagged objects show their tags as a suffix in the Navigator tree:

![Navigator with Tags](img/tags-navigator.png)

**To enable/disable tag display:**
- **Window → Preferences → General → Appearance → Label Decorations**
- Toggle "Metadata Tags Decorator"

### Filtering Navigator by Tags

Filter the entire Navigator to show only objects with specific tags:

1. Click the tag filter button in the Navigator toolbar (or right-click → **Tags → Filter by Tag...**)
2. Select one or more tags
3. Click **Set** to apply the filter

![Filter by Tag Dialog](img/tags-filter-dialog.png)

The Navigator will show only:
- Objects that have ANY of the selected tags
- Parent folders containing matching objects

**To clear the filter:** Click **Turn Off** in the dialog or use the toolbar button again.

### Keyboard Shortcuts for Tags

Quickly toggle tags on selected objects using keyboard shortcuts:

| Shortcut | Action |
|----------|--------|
| **Ctrl+Alt+1** | Toggle 1st tag |
| **Ctrl+Alt+2** | Toggle 2nd tag |
| **...** | ... |
| **Ctrl+Alt+9** | Toggle 9th tag |
| **Ctrl+Alt+0** | Toggle 10th tag |

**Features:**
- Works with multiple selected objects
- Supports cross-project selection (each object uses tags from its own project)
- Pressing the same shortcut again removes the tag (toggle behavior)
- Tag order is configurable in the Manage Tags dialog (Move Up/Move Down buttons)

**To customize shortcuts:** Window → Preferences → General → Keys → search for "Toggle Tag"

### Filtering Untagged Objects

Find metadata objects that haven't been tagged yet:

1. Open Filter by Tag dialog (toolbar button or Tags → Filter by Tag...)
2. Check the **"Show untagged objects only"** checkbox
3. Click **Set**

The Navigator will show only objects that have no tags assigned, making it easy to identify objects that need categorization.

### Multi-Select Tag Assignment

Assign or remove tags from multiple objects at once:

1. Select multiple objects in the Navigator (Ctrl+Click or Shift+Click)
2. Right-click → **Tags**
3. Select a tag to toggle it on/off for ALL selected objects

**Behavior:**
- ✓ Checked = all selected objects have this tag
- ☐ Unchecked = none of the selected objects have this tag
- When objects are from different projects, only objects from projects that have the tag will be affected

### Tag Filter View

For advanced filtering across multiple projects, use the Tag Filter View:

**Window → Show View → Other → MCP Server → Tag Filter**

This view provides:
- **Left panel**: Select tags from all projects in your workspace
- **Right panel**: See all matching objects with search and navigation
- **Search**: Filter results by object name using regex
- **Double-click**: Navigate directly to the object

### Where Tags Are Stored

Tags are stored in `.settings/metadata-tags.yaml` file in each project. This file:
- Can be committed to version control (VCS friendly)
- Is automatically updated when you rename or delete objects
- Uses YAML format for easy readability

**Example:**
```yaml
assignments:
  CommonModule.Utils:
    - Utils
  Document.SalesOrder:
    - Important
    - Sales
tags:
  - color: '#FF0000'
    description: Critical business logic
    name: Important
  - color: '#00FF00'
    description: ''
    name: Utils
  - color: '#0066FF'
    description: Sales department documents
    name: Sales
```

## Metadata Groups

Organize your Navigator tree with custom groups to create a logical folder structure for metadata objects.

### Why Use Groups?

Groups help you:
- Create custom folder hierarchy in the Navigator tree
- Organize objects by business area, feature, or any logical structure
- Navigate large configurations faster with nested groups
- Separate grouped objects from ungrouped ones

### Getting Started

**Creating a Group:**

1. Right-click on any metadata folder (e.g., Catalogs, Common modules) in the Navigator
2. Select **New Group...** from the context menu
3. Enter the group name and optional description
4. Click **OK** to create the group

![New Group Context Menu](img/groups-context-menu.png)

**Create Group Dialog:**

![New Group Dialog](img/groups-new-dialog.png)

**Adding Objects to a Group:**

1. Right-click on any metadata object in the Navigator
2. Select **Add to Group...**
3. Choose the target group from the list

![Add to Group Menu](img/groups-add-remove-menu.png)

**Removing Objects from a Group:**

1. Right-click on an object inside a group
2. Select **Remove from Group**

### Viewing Groups in Navigator

Grouped objects appear inside their group folders in the Navigator tree:

![Navigator with Groups - Common Modules](img/groups-navigator-common-modules.png)

![Navigator with Groups - Catalogs](img/groups-navigator-catalogs.png)

**Key Features:**
- Groups are created per metadata collection (Catalogs, Common modules, Documents, etc.)
- Objects inside groups are still accessible via standard EDT navigation
- Ungrouped objects appear at the end of the list

### Group Operations

| Action | How to Do It |
|--------|--------------|
| Create group | Right-click folder → **New Group...** |
| Add object to group | Right-click object → **Add to Group...** |
| Remove from group | Right-click object in group → **Remove from Group** |
| Copy group name | Select group → **Ctrl+C** |
| Delete group | Right-click group → **Delete** |
| Rename group | Right-click group → **Rename...** |

### Where Groups Are Stored

Groups are stored in `.settings/groups.yaml` file in each project. This file:
- Can be committed to version control (VCS friendly)
- Uses YAML format for easy readability
- Is automatically updated when you rename or delete objects

**Example:**
```yaml
groups:
- name: "Products & Inventory"
  description: "Product and inventory catalogs"
  path: Catalog
  order: 0
  children:
    - Catalog.ItemKeys
    - Catalog.Items
    - Catalog.ItemSegments
    - Catalog.Units
    - Catalog.UnitsOfMeasurement
- name: "Organization"
  description: "Organization structure catalogs"
  path: Catalog
  order: 1
  children:
    - Catalog.Companies
    - Catalog.Stores
- name: "Core Functions"
  description: "Core shared functions used across the application"
  path: CommonModule
  order: 0
  children:
    - CommonModule.CommonFunctionsClient
    - CommonModule.CommonFunctionsServer
    - CommonModule.CommonFunctionsClientServer
- name: "Localization"
  description: "Multi-language support modules"
  path: CommonModule
  order: 1
  children:
    - CommonModule.Localization
    - CommonModule.LocalizationClient
    - CommonModule.LocalizationServer
    - CommonModule.LocalizationReuse
```

## Requirements

- 1C:EDT 2025.2 (Ruby) or later
- Java 17+

## License
# Copyright (C) 2026 DitriX
# Licensed under GNU AGPL v3.0
