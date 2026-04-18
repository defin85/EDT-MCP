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
