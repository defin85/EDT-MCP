## ADDED Requirements

### Requirement: Project Kind Discovery And Capability Hints

The system SHALL distinguish configuration projects from extension projects and expose project-kind
and capability hints to clients before tool execution.

#### Scenario: Client lists EDT projects

- **WHEN** a client invokes `list_projects`
- **THEN** each returned EDT project identifies whether it is a configuration or extension project
- **AND** the result includes a stable documented capability vocabulary sufficient for tool
  selection
- **AND** the vocabulary covers at minimum metadata reads, module reads, mutation/refactor flows,
  and runtime/application flows

### Requirement: Extension Metadata And Module Read Support

The system SHALL support verified read-only metadata and module inspection flows for extension
projects when EDT exposes a compatible model.

#### Scenario: Client reads supported extension metadata

- **WHEN** a client invokes a documented first-wave read-only metadata or module tool against a
  valid extension project
- **THEN** the server resolves the request through shared project context
- **AND** the tool returns extension-scoped data or a precise model-limitation error

#### Scenario: Client invokes a read-only tool outside the verified extension matrix

- **WHEN** a client invokes a read-only metadata or module tool that is not documented as verified
  for extension projects
- **THEN** the server returns an actionable unsupported-operation or model-limitation error
- **AND** the server does not silently fall back to configuration-project semantics for a different
  project kind

### Requirement: Guarded Extension Write And Refactor Flows

The system SHALL apply explicit capability checks to write or refactoring tools on extension
projects and SHALL fail safely when the requested operation is not verified for that project kind.

#### Scenario: Unsupported extension write flow is rejected safely

- **WHEN** a client invokes a write or refactoring tool on an extension project that lacks verified
  support
- **THEN** the server returns an actionable unsupported-operation error
- **AND** the failure does not silently mutate unrelated configuration objects

### Requirement: Configuration-Only Runtime Tools Reject Extension Projects

The system SHALL reject runtime/application tools on extension projects until a dedicated
extension lifecycle surface is approved.

#### Scenario: Client invokes a configuration-only runtime tool on an extension project

- **WHEN** a client invokes `get_applications`, `update_database`, or `debug_launch` for an
  extension project in this rollout
- **THEN** the server returns a clear error explaining that runtime/application flows are
  configuration-only
- **AND** the response does not attempt infobase actions for the extension project

#### Scenario: Client invokes configuration-properties flow on an extension project

- **WHEN** a client invokes `get_configuration_properties` for an extension project in this rollout
- **THEN** the server returns a clear error explaining that extension-specific property semantics
  are not part of the configuration-properties contract yet
- **AND** the response does not silently return properties for a different configuration project
