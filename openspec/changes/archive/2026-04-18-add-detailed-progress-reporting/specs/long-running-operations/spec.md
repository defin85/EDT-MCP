## ADDED Requirements

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
