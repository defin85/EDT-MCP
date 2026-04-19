## ADDED Requirements

### Requirement: Detached EDT Continuation Visibility

The system SHALL preserve visibility of supported EDT work when that work continues after the originating MCP task or synchronous call is no longer the live execution owner.

#### Scenario: Rebuild continues after task cancellation

- **WHEN** a supported rebuild-oriented operation such as `clean_project` or full-project
  `revalidate_objects` already started EDT rebuild or derived-data processing
- **AND** the originating MCP task becomes terminal because of cancellation or other detach
- **THEN** `get_active_operation` continues to return a detached operation snapshot until the
  underlying EDT work actually becomes idle or the bridge times out
- **AND** the detached snapshot remains distinguishable from idle state and from an actively running
  MCP call
- **AND** the detached snapshot exposes an explicit detached marker or equivalent field that clients
  can read without inferring transport-local state
- **AND** the detached snapshot keeps the same `operationId` when it is the continuation of the same
  logical runtime operation
- **AND** the terminal task status remains truthful (`cancelled`, `failed`, or `completed` as
  applicable)

#### Scenario: Infobase update continues after request detach

- **WHEN** `update_database` detached from the original MCP request while EDT synchronization still
  continues for the target infobase
- **THEN** the EDT status bar and `get_active_operation` continue to expose the current detached
  synchronization state
- **AND** the terminal payload exposes a machine-readable continuation hint in `_meta`
- **AND** clients can keep polling the detached snapshot without relying on the original task/call
  lifetime

### Requirement: Honest Detached Progress Semantics

The system SHALL derive detached progress only from trustworthy EDT state sources and SHALL not fabricate exact percentage values when the source does not expose a reliable total.

#### Scenario: Derived-data pipeline exposes stage without linear total

- **WHEN** detached rebuild visibility is driven by `IDerivedDataManager` / `DerivedDataStatus`
- **THEN** the snapshot exposes stage, pipeline status, active segments, ready segments, and recent
  events as available
- **AND** the snapshot remains indeterminate when EDT does not expose a trustworthy linear total
- **AND** no fake exact percentage is emitted

#### Scenario: Infobase synchronization exposes state transitions without exact progress

- **WHEN** detached infobase update visibility is driven by
  `IInfobaseSynchronizationManager` listener/state APIs
- **THEN** the snapshot exposes current synchronization/equality state and recent state transitions
- **AND** the surface omits exact percentage unless the EDT source provides a trustworthy total
