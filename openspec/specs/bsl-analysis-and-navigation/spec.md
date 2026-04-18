# Capability: BSL Analysis And Navigation

## Purpose

Define the BSL source inspection, analysis, and navigation surface exposed by the EDT MCP server.

## Requirements

### Requirement: Module Discovery And Source Access

The system SHALL let clients discover BSL modules and read or update module and method source.

#### Scenario: Client reads BSL source

- **WHEN** a client invokes module listing, module structure, module source, or method source tools
- **THEN** the server returns BSL module information for the requested project scope

#### Scenario: Client updates BSL source

- **WHEN** a client invokes the module write tool with supported write mode and valid input
- **THEN** the server updates the target module and reports syntax or validation errors when the write is invalid

### Requirement: Semantic Navigation

The system SHALL provide semantic or symbol-oriented navigation for BSL code where the plugin supports it.

#### Scenario: Client resolves symbol location or relationships

- **WHEN** a client invokes find references, go to definition, symbol info, or method call hierarchy
- **THEN** the server returns the best available navigation result for the requested symbol or method

### Requirement: Query And Content Analysis

The system SHALL expose content assist and query validation in project context.

#### Scenario: Client requests content or query analysis

- **WHEN** a client invokes content assist or query validation
- **THEN** the server returns context-aware analysis based on the EDT project and request parameters
