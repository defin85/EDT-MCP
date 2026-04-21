# Capability: Long-Running Operations

## Purpose

Define the progress, task-backed execution, and compatibility fallback contract for long-running EDT operations.
## Requirements
### Requirement: Runtime Progress Reporting

The system SHALL expose runtime progress for supported long-running operations.

#### Scenario: Client requests progress notifications

- **WHEN** a client invokes a supported long-running tool and supplies `_meta.progressToken`
- **THEN** the server may emit `notifications/progress` updates tied to that token
- **AND** the final tool result remains available to clients that do not consume progress notifications

### Requirement: Task-Augmented Tool Execution

The system SHALL treat task-backed execution as the default contract for the verified long-running
mutable tools: `update_database`, `clean_project`, and full-project `revalidate_objects`.

#### Scenario: Client invokes an async-first tool without explicit task metadata

- **WHEN** a client calls `tools/call` for `update_database`, `clean_project`, or full-project
  `revalidate_objects`
- **THEN** the server starts task-backed execution
- **AND** the initial response returns task creation metadata instead of waiting for a final
  synchronous payload
- **AND** the final payload can be retrieved later through task result APIs in the same MCP session

#### Scenario: Client invokes an async-first tool with explicit task metadata

- **WHEN** a client calls `tools/call` for one of the async-first tools and includes `task`
- **THEN** the server executes the request as task-backed work
- **AND** the final payload can be retrieved later through task result APIs in the same MCP session

### Requirement: Compatibility Polling Fallback

The system SHALL provide a compatibility fallback for clients that do not consume progress notifications or task polling.

#### Scenario: Client polls active operation state

- **WHEN** a client invokes `get_active_operation`
- **THEN** the server returns the current tracked operation snapshot when one is available

### Requirement: Structured Sync Operation Progress

The system SHALL capture structured progress for supported synchronous long-running operations in a
shared runtime state that includes stage, message, elapsed time, and recent event history.

#### Scenario: A supported sync operation updates shared progress state

- **WHEN** a supported synchronous long-running operation moves through validation, execution, and
  completion stages
- **THEN** the server updates a shared operation snapshot with the current stage and message
- **AND** recent progress events remain available until the operation reaches a terminal state

### Requirement: EDT UI Shows Focused Operation Detail

The system SHALL render the focused synchronous long-running operation in the EDT status bar and
tooltip using real stage/detail information, and SHALL not fabricate exact percentage values when
EDT does not provide a trustworthy total.

#### Scenario: EDT exposes indeterminate progress

- **WHEN** a supported synchronous long-running operation reports stage changes without a reliable
  total
- **THEN** the EDT UI shows tool or stage detail plus elapsed time
- **AND** the UI does not display a fake exact percentage

### Requirement: Progress Notifications During Sync Calls

The system SHALL emit `notifications/progress` during supported synchronous long-running tool calls
when the client supplied `_meta.progressToken` and the session has a writable SSE stream.

#### Scenario: Client requests sync progress notifications

- **WHEN** a client invokes a supported synchronous long-running tool with `_meta.progressToken`
- **AND** the same MCP session has an attached writable SSE stream
- **THEN** the server emits rate-limited `notifications/progress` updates tied to the original
  token
- **AND** the final tool result remains available through the original synchronous call

### Requirement: Compatibility Snapshot Polling

The system SHALL expose a compatibility snapshot of the focused synchronous operation for clients
or wrappers that cannot consume progress notifications.

#### Scenario: Wrapper polls the focused operation

- **WHEN** a wrapper or client invokes `get_active_operation` during a supported synchronous
  long-running operation
- **THEN** the server returns the focused operation snapshot including status, stage, and progress
  fields
- **AND** the response includes enough detail to distinguish active work from idle state

### Requirement: Task Lifecycle APIs For Approved Long-Running Tools

The system SHALL support task-augmented `tools/call` for approved long-running tools and SHALL
expose task lifecycle APIs to inspect, retrieve, and cancel the created tasks.

#### Scenario: Client starts a task-backed long-running tool

- **WHEN** a client invokes an approved long-running tool with task augmentation
- **THEN** the server returns task creation metadata promptly
- **AND** the final payload is retrievable later through the task lifecycle APIs

### Requirement: Per-Task Progress And Session Ownership

The system SHALL track status, progress, and retained results per task, scoped to the owning MCP
session when session identity is available.

#### Scenario: Owning session inspects a task-backed operation

- **WHEN** the owning client invokes `tasks/get`, `tasks/result`, or `tasks/list` for a
  task-backed operation
- **THEN** the server returns that task's current status and retained result information according
  to the ownership rules
- **AND** the original progress token remains valid for progress updates throughout the task
  lifetime

### Requirement: Conflict-Aware Scheduling For Mutable Tasks

The system SHALL prevent unsafe parallel execution of conflicting mutable long-running tasks.

#### Scenario: Conflicting mutable tasks overlap

- **WHEN** a second task-backed operation conflicts with an active exclusive workspace or infobase
  task
- **THEN** the server explicitly rejects or queues the request according to the configured
  scheduler policy
- **AND** the client receives an actionable status message instead of silent parallel execution

### Requirement: Sync Compatibility During Task Rollout

The system SHALL preserve synchronous `tools/call` behavior for clients that do not request task
augmentation and for tools whose task policy is not task-required.

#### Scenario: Legacy client keeps using synchronous execution

- **WHEN** a client invokes a long-running tool without task augmentation during the first async
  rollout
- **THEN** the server still returns the final tool result through the synchronous call path
- **AND** compatibility surfaces such as `get_active_operation` remain available during the
  transition

### Requirement: No Legacy Sync Mode For Async-First Tools

The system SHALL not expose legacy synchronous execution for the async-first tool set:
`update_database`, `clean_project`, and full-project `revalidate_objects`.

#### Scenario: Client expects a final synchronous payload from an async-first tool

- **WHEN** a client calls `tools/call` for `update_database`, `clean_project`, or full-project
  `revalidate_objects`
- **THEN** the server returns task creation metadata rather than waiting for a final synchronous
  payload
- **AND** the server does not provide a legacy synchronous override for that tool set

### Requirement: Async-First Tool Discovery

The system SHALL describe async-first behavior and legacy sync removal through discovery and
documentation surfaces.

#### Scenario: Client inspects tool catalog or README

- **WHEN** a client reads `tools/list` metadata or repository documentation for
  `update_database`, `clean_project`, or full-project `revalidate_objects`
- **THEN** the surfaces describe these tools as async-first through server-side bare-call
  auto-promotion
- **AND** the surfaces explain that a bare call returns task creation metadata instead of a final
  synchronous payload
- **AND** the surfaces do not claim MCP `execution.taskSupport: "required"` while explicit task
  augmentation remains optional at protocol level
- **AND** the surfaces explain that follow-up task polling and result retrieval use the same
  MCP session

### Requirement: Exact Operation Snapshot Lookup

The system SHALL allow clients to fetch the snapshot of a specific tracked long-running operation by stable `operationId`, independently from the current focused projection.

#### Scenario: Клиент опрашивает известную detached operation

- **WHEN** terminal payload, task result или blocking hint уже содержит `operationId`
- **AND** соответствующая runtime operation всё ещё отслеживается сервером
- **THEN** клиент может вызвать `get_operation_snapshot` с этим `operationId`
- **AND** сервер возвращает snapshot именно этой operation, даже если `get_active_operation`
  сейчас сфокусирован на другой operation
- **AND** snapshot сохраняет ту же logical identity через тот же `operationId`
- **AND** machine-readable hint, который уже содержит этот `operationId`, указывает
  `pollTool: "get_operation_snapshot"` как preferred exact polling surface

#### Scenario: Клиент спрашивает неизвестную или уже очищенную operation

- **WHEN** клиент вызывает `get_operation_snapshot` с неизвестным или уже очищенным `operationId`
- **THEN** сервер возвращает явный not-found outcome с `found: false`
- **AND** response остаётся normal tool result, а не transport-level JSON-RPC error
- **AND** сервер НЕ ДОЛЖЕН подменять ответ текущей focused operation

### Requirement: Actionable Busy-State Blocking Diagnostics

The system SHALL return actionable machine-readable diagnostics when a tool invocation is rejected because a project or infobase is still busy with ongoing EDT work.

#### Scenario: Busy-state blocker однозначно связан с tracked operation

- **WHEN** tool invocation отклоняется из-за project-building или infobase-sync busy state
- **AND** сервер может однозначно связать blocker с tracked runtime operation
- **THEN** tool payload содержит нормализованный human-readable blocker message
- **AND** payload `_meta["io.ditrix.edt.mcp/blocking-operation"]` содержит как минимум
  `reasonCode`, `scope`, `projectName`, `operationId` и `pollTool`
- **AND** при `scope: "application"` hint дополнительно содержит `applicationId` и SHOULD
  содержать `applicationName`, если оно уже известно серверу
- **AND** `pollTool` равен `get_operation_snapshot` и указывает на exact polling surface для этого
  blocker
- **AND** primary human-facing message НЕ ДОЛЖНО опираться на raw Java `toString()` EDT object как
  единственное объяснение причины

#### Scenario: Busy-state blocker не удаётся однозначно коррелировать

- **WHEN** tool invocation отклоняется из-за busy state
- **AND** сервер не может надёжно определить одну blocking operation
- **THEN** tool payload всё равно содержит нормализованный blocker reason code и human-readable
  explanation
- **AND** payload `_meta["io.ditrix.edt.mcp/blocking-operation"]` всё равно содержит `reasonCode`,
  `scope`, `projectName` и известные application identifiers, если blocker относится к infobase
- **AND** сервер НЕ ДОЛЖЕН придумывать `operationId`
- **AND** сервер НЕ ДОЛЖЕН указывать `pollTool`, если exact operation lookup недоступен
- **AND** response остаётся пригодным для retry/wait decision даже без exact operation lookup

