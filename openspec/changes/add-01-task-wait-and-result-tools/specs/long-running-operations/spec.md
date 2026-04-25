## ADDED Requirements

### Requirement: Tool-Level Task Lifecycle Access

The system SHALL expose task lifecycle access through explicit MCP tools for agents and clients
that discover workflows primarily through `tools/list`. These tools SHALL delegate to the existing
task lifecycle registry and SHALL preserve the same ownership, retention, and result semantics as
the protocol-level task APIs.

#### Scenario: Client lists session-visible tasks

- **WHEN** a client invokes `list_tasks`
- **THEN** the server returns task summaries visible to the current MCP session
- **AND** each summary includes enough lifecycle metadata for the client to decide whether to wait,
  retrieve a result, or abandon the task

#### Scenario: Client retrieves a retained task result

- **WHEN** a client invokes `get_task_result` for a terminal task owned by the same MCP session
- **THEN** the server returns the retained final payload or a manifest pointer according to existing
  task retention rules
- **AND** the server does not re-execute the original long-running operation

### Requirement: Bounded Task Wait Tool

The system SHALL expose `wait_task` as a bounded polling helper that waits for a task to reach a
terminal state or until the requested timeout expires. The tool SHALL return timeout as a normal
tool outcome with the latest known task snapshot and SHALL NOT continue waiting in the background
after it returns.

#### Scenario: Task reaches terminal state before timeout

- **WHEN** a client invokes `wait_task` for a session-owned active task
- **AND** the task completes, fails, or is cancelled before the timeout
- **THEN** the response returns the terminal state and retained result metadata
- **AND** the lifecycle fields clearly distinguish accepted, active, completed, failed, and
  cancelled states

#### Scenario: Wait timeout expires first

- **WHEN** a client invokes `wait_task` and the task is still active when `timeoutSeconds` expires
- **THEN** the response reports a timeout outcome
- **AND** it includes the latest task snapshot and preferred follow-up polling tool
- **AND** no server-side wait continues on behalf of that call

### Requirement: Normalized Task And Operation Evidence Envelope

The system SHALL return a normalized lifecycle envelope from task lifecycle tools and from
task-backed operation surfaces where source data is available. The envelope SHALL include stable
identity, state, timing, result, warning, and source-tool metadata without flattening away
tool-specific final payloads.

#### Scenario: Agent compares different task-backed operations

- **WHEN** an agent inspects task-backed outcomes from database update, extension apply, debug
  launch, YAxUnit execution, or other approved task-backed tools
- **THEN** the lifecycle envelope consistently exposes `taskId` or `operationId`, `state`,
  `startedAt`, `finishedAt` when known, `result`, `warnings`, and source tool metadata
- **AND** operation-specific payloads remain accessible in a documented field or manifest
