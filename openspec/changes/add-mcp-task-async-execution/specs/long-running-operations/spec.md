## ADDED Requirements

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
