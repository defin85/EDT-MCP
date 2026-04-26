## ADDED Requirements

### Requirement: Document Movement Live Evidence Probe

The system SHALL provide a bounded read-only live evidence probe for document movements by recorder.
The probe SHALL identify the selected application/infobase target, SHALL read matching movement
records without mutating the infobase, SHALL bound execution by timeout and sample size, and SHALL
fail closed when the runtime cannot prove safe read-only access.

#### Scenario: Client reads document movements by recorder

- **WHEN** a client invokes a document-movement evidence probe for a recorder reference in a selected
  application target
- **THEN** the server reads matching movement records without writing, posting, or otherwise mutating
  the infobase
- **AND** the response includes the recorder identity, register names, row counts, bounded sample
  records, target identity, and timeout status

#### Scenario: Runtime cannot prove safe read-only access

- **WHEN** a client invokes the probe for a target whose runtime access path cannot prove read-only
  movement-record access
- **THEN** the server returns an explicit unsupported or error outcome
- **AND** it does not run a write/post/dry-run path or an unrestricted client-supplied query
