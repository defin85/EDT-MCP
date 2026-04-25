## ADDED Requirements

### Requirement: Runtime Capability Discovery Tool

The system SHALL expose a structured `describe_capabilities` tool that summarizes the installed
runtime capability surface for agents. The response SHALL include server and bundle versions,
supported tools and resources, known limitations, async/task support, runtime debug support, YAxUnit
support, extension lifecycle support, and live evidence support.

#### Scenario: Agent selects an available verification path

- **WHEN** an agent invokes `describe_capabilities`
- **THEN** the response returns current runtime capability facts derived from tool/resource
  registrations and runtime feature flags where possible
- **AND** the response distinguishes supported, partially supported, unsupported, and unknown
  capability areas
- **AND** known limitations are presented as machine-readable entries suitable for fail-closed
  planning

#### Scenario: Capability differs from checked-in source

- **WHEN** the installed runtime bundle is older or newer than the checked-in repository state
- **THEN** `describe_capabilities` reports the installed runtime version and available runtime
  surface
- **AND** the response does not imply that checked-in but uninstalled code is currently available
