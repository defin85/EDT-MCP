## ADDED Requirements

### Requirement: Early Object Revalidate Diagnostics

The system SHALL emit early-stage diagnostic checkpoints for object-scoped
`revalidate_objects` so maintainers can distinguish refresh-path hangs from
later build or derived-data waits.

#### Scenario: Object-scoped revalidate enters the refresh path

- **WHEN** a client invokes `revalidate_objects` with a non-empty object list
- **THEN** the server logs checkpoints covering project-handle resolution,
  project existence/open checks, refresh-stage entry, and `refreshLocal`
  start/completion before later validation scheduling
- **AND** each checkpoint carries stable correlation identifiers sufficient to
  tie the log entries back to the originating request and operation
- **AND** the absence of later checkpoints can be interpreted against these
  earlier markers instead of only against a single `start` line

### Requirement: Watchdog Evidence For Early Revalidate Hangs

The system SHALL emit watchdog evidence when object-scoped `revalidate_objects`
stalls in the early refresh stage.

#### Scenario: Early refresh stage exceeds the watchdog threshold

- **WHEN** object-scoped `revalidate_objects` stops making progress before
  object lookup or validation scheduling returns
- **THEN** the server logs a one-shot warning tied to the same correlation label
- **AND** the watchdog emits thread-dump evidence for the suspected hang window
- **AND** the diagnostics distinguish this early-stage stall from later
  build-job or derived-data waits

### Requirement: Honest Revalidate Incident Runbook

The repository SHALL document how to collect and interpret early
`revalidate_objects` hang evidence.

#### Scenario: Maintainer investigates an early revalidate hang

- **WHEN** a maintainer or agent follows the documented runbook
- **THEN** the docs identify the relevant workspace log, checkpoint names, and
  watchdog markers to inspect
- **AND** the docs explain that these diagnostics narrow the failing stage
  without overstating the proof of the underlying EDT root cause
