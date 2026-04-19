## MODIFIED Requirements

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
