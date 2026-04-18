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

The system SHALL support task-augmented execution for supported long-running operations.

#### Scenario: Client invokes task-backed update

- **WHEN** a client calls `tools/call` for a supported operation with `task`
- **THEN** the server returns task creation metadata first
- **AND** the final payload can be retrieved later through task result APIs

### Requirement: Compatibility Polling Fallback

The system SHALL provide a compatibility fallback for clients that do not consume progress notifications or task polling.

#### Scenario: Client polls active operation state

- **WHEN** a client invokes `get_active_operation`
- **THEN** the server returns the current tracked operation snapshot when one is available
