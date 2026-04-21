## ADDED Requirements

### Requirement: Task Capability Advertisement

The system SHALL advertise MCP task capability during initialization only when task lifecycle
handlers are implemented and safe to expose.

#### Scenario: Task-capable server initializes

- **WHEN** a client sends `initialize` to a build that implements the supported task lifecycle
  handlers
- **THEN** the initialize result advertises `tasks` capability for tool requests
- **AND** unsupported or unsafe task handlers are not advertised as available

### Requirement: Task-Aware Tool Discovery

The system SHALL describe each tool's task-execution policy in `tools/list`.

#### Scenario: Client enumerates tool async support

- **WHEN** a client requests `tools/list`
- **THEN** each tool definition includes `execution.taskSupport`
- **AND** the value accurately reflects whether task-augmented execution is forbidden, optional, or
  required
