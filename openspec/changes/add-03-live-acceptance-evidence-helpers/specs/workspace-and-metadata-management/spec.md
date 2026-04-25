## ADDED Requirements

### Requirement: Form Event Contract Checks

The system SHALL provide a form event contract check that verifies both the form module procedure
and the form metadata event binding for a requested form event. The result SHALL distinguish missing
procedure, unbound event, mismatched handler name, unsupported form metadata, and valid binding.

#### Scenario: Handler procedure exists but event is unbound

- **WHEN** a client checks a form event whose handler procedure exists in the form module
- **AND** the corresponding `Form.form` metadata does not bind that event to the procedure
- **THEN** the response reports an unbound-event diagnostic
- **AND** it includes both the module procedure location and the inspected form metadata location

#### Scenario: Form event contract is valid

- **WHEN** the form metadata binds the event to an existing compatible handler procedure
- **THEN** the response reports a valid binding
- **AND** it includes enough evidence for an agent to cite the form path, event name, handler name,
  and procedure location

### Requirement: Bounded Live Infobase Evidence Probes

The system SHALL expose narrow live infobase evidence probes for common acceptance checks. Probes
SHALL be bounded by timeout, SHALL identify the target application/infobase, SHALL be read-only by
default, and SHALL fail closed when safe live evidence cannot be collected.

#### Scenario: Client reads document movements by recorder

- **WHEN** a client invokes a document-movement evidence probe for a recorder reference in a selected
  application target
- **THEN** the server reads matching movement records without mutating the infobase
- **AND** the response includes the recorder identity, register names, row counts, bounded sample
  records, target identity, and timeout status

#### Scenario: Client requests unsafe write or post dry-run

- **WHEN** a client requests a document write or post dry-run for a path whose rollback or side
  effect boundary is not proven
- **THEN** the server returns an explicit unsupported-safe-dry-run outcome
- **AND** it does not perform the write or post operation
