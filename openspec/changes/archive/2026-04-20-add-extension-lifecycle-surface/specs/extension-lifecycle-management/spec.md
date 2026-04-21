## ADDED Requirements

### Requirement: Extension Project Discovery And Target Resolution

The system SHALL expose a dedicated discovery surface for extension projects that resolves
extension-specific properties and runtime targets without overloading configuration-only tools.

#### Scenario: Client reads extension project properties

- **WHEN** a client invokes `get_extension_properties` for a valid extension project
- **THEN** the server returns extension-scoped root configuration properties
- **AND** the response includes extension identity and parent configuration linkage when available
- **AND** the server does not silently fall back to an unrelated configuration project

#### Scenario: Client resolves runtime targets for an extension project

- **WHEN** a client invokes `get_extension_runtime_targets` for a valid extension project
- **THEN** the server resolves the parent configuration project through EDT dependent-project APIs
- **AND** the response returns the parent configuration project and available applications derived
  from that parent
- **AND** the response fails with a stable machine-readable category if the extension project has
  no usable parent runtime target

### Requirement: Infobase Extension Inspection And Compatibility Check

The system SHALL expose explicit tools to inspect target infobase extension state and SHALL fail
closed when an extension applicability check is not headless-safe for MCP execution.

#### Scenario: Client lists extensions installed in a target infobase

- **WHEN** a client invokes `list_infobase_extensions` for a selected runtime target
- **THEN** the server uses extension-aware runtime APIs to return installed infobase extension names
- **AND** the response may identify whether the workspace extension name is already present

#### Scenario: Client checks whether a workspace extension can be applied

- **WHEN** a client invokes `check_extension_applicability` for an extension project and runtime
  target
- **THEN** the server does not trigger an interactive EDT runtime path that can prompt for infobase
  credentials or destabilize the MCP runtime bridge
- **AND** the response fails closed with a stable machine-readable category distinct from transport
  failure

### Requirement: Explicit Extension Apply Flow

The system SHALL expose a dedicated long-running tool that applies a workspace extension project to
the selected infobase target without reinterpreting the generic configuration update contract.

#### Scenario: Client applies an extension project to an infobase

- **WHEN** a client invokes `apply_extension_to_infobase`
- **THEN** the server executes an explicit extension lifecycle flow for the selected target
- **AND** the operation does not silently widen into a whole-configuration update for a different
  project kind
- **AND** the final result reports success or a stable machine-readable lifecycle failure

#### Scenario: Client uses task-backed apply flow

- **WHEN** a client invokes `apply_extension_to_infobase` without waiting synchronously
- **THEN** the server may auto-promote the call into task-backed execution
- **AND** progress and final result remain available through the existing MCP tasks/progress
  contract

### Requirement: Legacy Configuration Runtime Boundary Remains Explicit

The system SHALL preserve the existing configuration-only runtime contract for legacy tools even
after dedicated extension lifecycle tools are introduced.

#### Scenario: Client still invokes a legacy configuration runtime tool on an extension project

- **WHEN** a client invokes `get_applications`, `update_database`, or `debug_launch` for an
  extension project after this capability is introduced
- **THEN** the server continues to reject the call with a clear `configuration_only` error
- **AND** the response may point the client to the dedicated extension lifecycle tools
