## ADDED Requirements

### Requirement: BSL Runtime Breakpoint Discovery

The system SHALL expose EDT BSL line breakpoints that are relevant to supported runtime debug
sessions through `list_debug_breakpoints`. The tool SHALL filter out unrelated Eclipse breakpoint
types and SHALL return enough metadata for clients to correlate each breakpoint with a workspace
project, `modulePath`, full workspace path, line number, enabled state, registered state, persisted
state, ownership metadata, and later removal request.

#### Scenario: Client lists workspace BSL breakpoints

- **WHEN** a client invokes `list_debug_breakpoints` for a workspace that contains EDT BSL line
  breakpoints
- **THEN** the result includes only supported BSL line breakpoints
- **AND** each breakpoint includes a stable MCP breakpoint identifier, project name, source
  location, line number, enabled state, registered state, persisted state, ownership metadata, and
  backend capability metadata

### Requirement: BSL Runtime Breakpoint Creation

The system SHALL let clients create a supported EDT BSL line breakpoint through
`set_debug_breakpoint` by providing `projectName`, `modulePath` relative to the project's `src/`
folder, and a 1-based `lineNumber`. The tool SHALL resolve the requested source location to a
workspace-backed EDT BSL file before mutating the breakpoint backend, SHALL use the verified
EDT-compatible breakpoint creation path, and SHALL fail closed if the backend contract has not been
discovered or the source cannot be resolved unambiguously.

#### Scenario: Client sets a supported BSL line breakpoint

- **WHEN** a client invokes `set_debug_breakpoint` with a project, resolvable BSL source location,
  and valid line number
- **THEN** the server creates or reuses the EDT-compatible line breakpoint for that source line
- **AND** the response returns the breakpoint identifier and metadata needed for later listing or
  removal
- **AND** the response states whether the breakpoint was newly created or pre-existing, whether MCP
  owns it for default cleanup, and whether the backend registered or persisted it

#### Scenario: Client targets an unsupported source location

- **WHEN** a client invokes `set_debug_breakpoint` for a missing, ambiguous, non-BSL, or
  out-of-workspace source location
- **THEN** the server returns an actionable fail-closed error
- **AND** the server does not create arbitrary resource markers or fabricate breakpoint state

### Requirement: BSL Runtime Breakpoint Removal

The system SHALL let clients remove a supported EDT BSL line breakpoint through
`remove_debug_breakpoint` using a breakpoint identifier returned by the breakpoint tools. Removal
SHALL be limited to supported breakpoint types and SHALL return explicit not-found or stale-context
errors instead of deleting unrelated Eclipse markers. By default, the tool SHALL remove only
breakpoints that were created or explicitly owned by MCP; removing a pre-existing user breakpoint
SHALL require an explicit override parameter.

#### Scenario: Client removes a supported breakpoint

- **WHEN** a client invokes `remove_debug_breakpoint` with a current identifier for a supported BSL
  line breakpoint created or owned by MCP
- **THEN** the server removes that breakpoint through the verified EDT/Eclipse breakpoint backend
- **AND** subsequent `list_debug_breakpoints` calls no longer report it

#### Scenario: Client attempts default removal of a pre-existing user breakpoint

- **WHEN** a client invokes `remove_debug_breakpoint` for a supported BSL line breakpoint that was
  discovered or reused but not created or owned by MCP
- **THEN** the server returns a protected-user-breakpoint error unless the request includes the
  explicit pre-existing removal override
- **AND** the server leaves the breakpoint registered

### Requirement: Breakpoint-To-Runtime Debug Flow

The system SHALL support an end-to-end local debugging flow where an MCP client sets a BSL
breakpoint, launches or attaches to a supported EDT runtime debug session, observes suspension at
that breakpoint through the existing runtime debug tools, inspects stack and variables, performs a
thread-level execution-control action, and removes the breakpoint.

#### Scenario: MCP-created breakpoint suspends a runtime session

- **WHEN** a client creates a supported BSL line breakpoint through `set_debug_breakpoint`
- **AND** a supported EDT runtime debug session executes that source line
- **THEN** `list_debug_sessions` reports a suspended thread with stack frames
- **AND** `get_debug_stack`, `get_debug_variables`, and `control_debug_session` can operate on that
  suspension point according to their existing preconditions
