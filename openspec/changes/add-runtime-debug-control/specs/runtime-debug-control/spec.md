## ADDED Requirements

### Requirement: Debug Session Discovery For Supported Runtime Launches

The system SHALL expose supported EDT runtime debug sessions through `list_debug_sessions` and
SHALL filter out unrelated Eclipse debug launches that do not satisfy the supported runtime debug
model for this capability. A supported session is an active Eclipse debug launch for the EDT runtime
launch configuration type `com._1c.g5.v8.dt.launching.core.RuntimeClient` whose project and
application identifiers resolve to a configuration-project application target and whose debug model
exposes the standard Eclipse session/thread/frame capabilities required by the requested operation.

#### Scenario: Client lists active supported debug sessions

- **WHEN** a client invokes `list_debug_sessions`
- **THEN** the result includes only supported EDT runtime debug sessions
- **AND** each session returns a session identifier, lifecycle state, project or application
  context, and thread summaries sufficient for later inspection calls

### Requirement: Snapshot-Based Stack Inspection

The system SHALL expose stack frames and source locations for suspended threads through
`get_debug_stack` using snapshot-scoped thread and frame identifiers rather than raw Eclipse object
handles.

#### Scenario: Client inspects a suspended thread stack

- **WHEN** a client invokes `get_debug_stack` for a suspended thread in a supported debug session
- **THEN** the server returns ordered stack frames with frame identifiers valid for the current
  suspended snapshot
- **AND** each frame includes enough source information for the client to correlate it with EDT
  modules or an explicit unsupported-source marker

### Requirement: Snapshot-Based Variable Inspection

The system SHALL expose variables and nested value metadata for a selected suspended frame through
`get_debug_variables` without requiring the client to understand Eclipse debug model classes. The
tool SHALL bound variable expansion by default and SHALL make truncation or unsupported expansion
explicit in the response.

#### Scenario: Client inspects variables in a suspended frame

- **WHEN** a client invokes `get_debug_variables` for a valid suspended frame from the current
  session snapshot
- **THEN** the server returns variable names, display values, type hints or capability flags, and
  child-expansion metadata
- **AND** the response includes explicit `hasChildren`, `truncated`, and `expansionUnsupported`
  indicators where applicable

### Requirement: Basic Execution Control For Supported Sessions

The system SHALL let clients issue basic execution-control actions for supported debug sessions or
their threads through `control_debug_session`. Control actions SHALL dispatch the requested debugger
state transition and return the immediate known state plus the next inspection or polling hint; the
tool SHALL NOT block indefinitely waiting for a later suspension point. `resume`, `suspend`, and
step actions SHALL require a thread identifier from a supported debug-session snapshot; session-level
control SHALL be limited to `terminate` in this rollout.

#### Scenario: Client steps a suspended thread

- **WHEN** a client invokes `control_debug_session` with a supported thread action such as `resume`,
  `suspend`, `step_over`, `step_into`, or `step_return`
- **AND** the addressed thread satisfies the action preconditions
- **THEN** the server applies the action through the supported EDT debug backend
- **AND** the response reports the resulting state or the next required client action

### Requirement: Honest Preconditions And Backward-Compatible Launch Integration

The system SHALL fail safely when a debug request targets a missing session, a stale snapshot
identifier, a running thread, or an unsupported backend capability, and SHALL keep `debug_launch`
backward-compatible while making launched sessions discoverable through the new debug tools.

#### Scenario: Client requests frame variables after execution resumed

- **WHEN** a client invokes `get_debug_variables` with a frame identifier from an older suspended
  snapshot after the thread has resumed or the stack has changed
- **THEN** the server returns an actionable stale-context or precondition error
- **AND** the response does not fabricate stack or variable data

#### Scenario: Client launches runtime and then discovers the session

- **WHEN** a client invokes `debug_launch` for a supported application and the EDT runtime creates a
  supported debug session
- **THEN** a later `list_debug_sessions` call can discover that session
- **AND** the `debug_launch` response remains compatible with existing clients
