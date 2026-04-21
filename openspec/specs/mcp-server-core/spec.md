# Capability: MCP Server Core

## Purpose

Define the baseline MCP transport and discovery surface exposed by the EDT plugin.
## Requirements
### Requirement: Health And MCP Endpoints

The system SHALL expose an HTTP health endpoint and an MCP JSON-RPC endpoint.

#### Scenario: Client checks server availability

- **WHEN** a client calls `/health`
- **THEN** the server returns a health response without requiring a tool call

#### Scenario: Client sends MCP JSON-RPC requests

- **WHEN** a client calls `/mcp`
- **THEN** the server accepts MCP JSON-RPC requests for initialization and tool interaction

### Requirement: Initialization And Tool Discovery

The system SHALL let clients initialize a session and enumerate available tools.

#### Scenario: MCP client initializes

- **WHEN** a client sends `initialize`
- **THEN** the server returns advertised capabilities for the current runtime

#### Scenario: MCP client asks for tool catalog

- **WHEN** a client sends `tools/list`
- **THEN** the server returns the available tool set and execution metadata for those tools

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

