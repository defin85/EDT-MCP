## ADDED Requirements

### Requirement: EDT Ruby 2024.2 Target Compatibility

The plugin distribution SHALL resolve and package against the official EDT Ruby 2024.2 update site
without requiring install units that exist only in newer EDT lines.

#### Scenario: Build target is prepared for 2024.2

- **WHEN** maintainers build the 2024.2.5 compatibility line
- **THEN** the target platform resolves from the official EDT Ruby 2024.2 update site
- **AND** the build does not require newer-line-only install units to package the plugin

### Requirement: 2024.2-Compatible Runtime Tool Integrations

The server SHALL provide `get_applications`, `update_database`, and `debug_launch` behavior on EDT
Ruby 2024.2.5 using APIs available in that EDT line.

#### Scenario: Client invokes runtime tools on 2024.2.5

- **WHEN** the plugin runs inside EDT Ruby 2024.2.5 and a client invokes one of the supported
  runtime tools
- **THEN** the server uses 2024.2-compatible infobase synchronization APIs
- **AND** the tool returns a normal success or actionable failure result instead of relying on
  missing newer EDT methods

### Requirement: Honest Full Update Compatibility

The server SHALL make any degraded `fullUpdate` behavior explicit when the EDT 2024.2 APIs cannot
preserve the semantics of newer EDT lines.

#### Scenario: Client requests `fullUpdate` on the compatibility line

- **WHEN** a client invokes `update_database` with `fullUpdate` on the EDT 2024.2.5 line
- **THEN** the server either performs the supported compatibility mapping or returns an explicit
  limitation
- **AND** the response does not imply unsupported parity with newer EDT update semantics

### Requirement: Honest Version Documentation

The repository SHALL document the supported EDT line and compatibility limitations for the
2024.2.5 build.

#### Scenario: User reads support documentation for the compatibility line

- **WHEN** a user reads the compatibility branch README or release notes
- **THEN** the documentation states the supported EDT version line
- **AND** any known limitations or behavior differences are described without ambiguity
