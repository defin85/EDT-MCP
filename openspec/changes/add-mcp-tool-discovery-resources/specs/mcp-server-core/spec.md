## ADDED Requirements

### Requirement: Tool Discovery Annotations

The system SHALL expose standards-aligned tool discovery annotations in `tools/list` for tools whose
safety and execution characteristics are known.

#### Scenario: Client inspects annotated tool catalog

- **WHEN** a client sends `tools/list`
- **THEN** each annotated tool includes its supported annotation fields in the tool definition
- **AND** existing `name`, `description`, `inputSchema`, and `execution.taskSupport` fields remain
  available for existing clients
- **AND** tools without a reliable annotation value omit that annotation instead of guessing

#### Scenario: Client distinguishes read-only inspection from runtime mutation

- **WHEN** a client compares discovery metadata for inspection tools and state-changing runtime
  tools
- **THEN** read-only tools advertise read-only status where supported
- **AND** mutating YAxUnit session, debug control, breakpoint, launch, and recycle tools are not
  misrepresented as read-only

### Requirement: Workflow-Aware Tool Descriptions

The system SHALL keep runtime tool descriptions concise while including enough workflow handoff
context for agents to choose the next tool in YAxUnit and runtime-debug flows.

#### Scenario: Client reads YAxUnit tool descriptions

- **WHEN** a client inspects `run_unit_tests`, `prepare_test_session`, `get_test_session_status`,
  `recycle_test_session`, or `get_test_run_report` in `tools/list`
- **THEN** the descriptions identify the tool role in the YAxUnit runtime testing flow
- **AND** the descriptions name the follow-up identifiers or tools needed for task result, retained
  report, or warm-session reuse

#### Scenario: Client reads runtime debug tool descriptions

- **WHEN** a client inspects `debug_launch`, `list_debug_sessions`, `set_debug_breakpoint`,
  `list_debug_breakpoints`, `get_debug_stack`, `get_debug_variables`, `control_debug_session`, or
  `remove_debug_breakpoint` in `tools/list`
- **THEN** the descriptions identify the tool role in the runtime debug or breakpoint flow
- **AND** the descriptions name the identifiers or follow-up tools needed for stack inspection,
  variable inspection, execution control, or cleanup

### Requirement: MCP Resource Discovery

The system SHALL expose MCP resource discovery and read methods for static repo-owned capability and
workflow guidance.

#### Scenario: Client initializes against a resource-capable server

- **WHEN** a client sends `initialize`
- **THEN** the server advertises the MCP `resources` capability
- **AND** the server does not advertise resource subscriptions or list-changed notifications unless
  those handlers and notifications are implemented

#### Scenario: Client lists server resources

- **WHEN** a client sends `resources/list`
- **THEN** the server returns registered resource descriptors with stable URI, name, description,
  and MIME type fields
- **AND** the resource list includes capability and workflow resources for YAxUnit runtime testing
  runtime debug control, and extension lifecycle operations
- **AND** the server may omit `nextCursor` when all static resources fit in one page

#### Scenario: Client reads a known workflow resource

- **WHEN** a client sends `resources/read` for a registered workflow resource URI
- **THEN** the server returns a `contents` array with the requested URI, MIME type, and text content
- **AND** the content describes the relevant tool chain, transferred identifiers, polling/report
  rules, cleanup responsibilities, and known limitations

#### Scenario: Client reads an unknown resource

- **WHEN** a client sends `resources/read` for an unknown resource URI
- **THEN** the server returns a JSON-RPC resource-not-found error with code `-32002`
- **AND** the server does not fabricate resource content

#### Scenario: Client supplies a custom resource URI

- **WHEN** a client sends `resources/read` for an `edt-mcp://` URI
- **THEN** the server resolves the URI only by exact match against the registered static resource
  catalog
- **AND** the server does not treat the URI as a filesystem path, URL to fetch, or executable lookup

### Requirement: Capability Workflow Resources

The system SHALL provide resource content that explains the new runtime testing and debugging
contours without duplicating live state APIs.

#### Scenario: Client reads the YAxUnit runtime testing capability resource

- **WHEN** a client reads `edt-mcp://capabilities/yaxunit-runtime-testing`
- **THEN** the content explains cold execution, warm-session preparation, reuse policy, async-first
  task result retrieval, retained report lookup, and session recycle/status tools
- **AND** the content points to live-state tools instead of embedding stale session or run state

#### Scenario: Client reads the runtime debug capability resource

- **WHEN** a client reads `edt-mcp://capabilities/runtime-debug-control`
- **THEN** the content explains debug launch, session discovery, breakpoint creation/list/removal,
  stack and variable inspection, execution control actions, and cleanup responsibilities
- **AND** the content points to live-state tools instead of embedding stale session, thread, frame,
  variable, or breakpoint state
