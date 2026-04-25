## ADDED Requirements

### Requirement: Suspended Frame Expression Evaluation

The system SHALL expose `evaluate_debug_expression` for supported EDT runtime debug sessions when a
current suspended BSL stack frame can evaluate the requested expression. The tool SHALL use a
snapshot-scoped frame identifier, SHALL bound evaluation by timeout and result-size controls, and
SHALL fail closed for running threads, stale frame identifiers, unsupported backend capability, or
evaluation errors.

#### Scenario: Client evaluates an expression in a suspended frame

- **WHEN** a client invokes `evaluate_debug_expression` with a current suspended frame identifier
  and an expression supported by the EDT debug backend
- **THEN** the server evaluates the expression in that frame context without resuming the thread
- **AND** the response returns the evaluated presentation, type or capability metadata, and bounded
  value details
- **AND** the response states whether the backend can guarantee side-effect-free evaluation

#### Scenario: Client evaluates against stale or running debug state

- **WHEN** a client invokes `evaluate_debug_expression` after the addressed thread has resumed or
  the frame identifier no longer belongs to the current suspended snapshot
- **THEN** the server returns a stale-context or precondition failure
- **AND** the response does not fabricate an expression value

### Requirement: Bounded Run-To-Breakpoint Debug Helper

The system SHALL expose a one-shot helper for running a supported runtime debug session to a BSL
source line by creating or reusing an MCP-owned temporary breakpoint, launching or continuing
execution as requested, waiting until suspend or timeout, and returning direct debug evidence.

#### Scenario: Client reaches a target BSL line

- **WHEN** a client invokes the run-to-breakpoint helper for a resolvable BSL source location and a
  supported launch or debug session
- **AND** execution reaches the target line before the requested timeout
- **THEN** the response reports a suspended outcome
- **AND** it includes the breakpoint metadata, session/thread identifiers, top stack frame source
  location, and bounded variable evidence or a pointer to the next inspection call

#### Scenario: Target line is not reached before timeout

- **WHEN** the helper does not observe a matching suspended thread before `timeoutSeconds`
- **THEN** the response reports a timeout outcome with the last known launch/session state
- **AND** the tool does not continue waiting in the background after returning
- **AND** temporary breakpoint cleanup status is reported explicitly

### Requirement: MCP-Owned Breakpoint Cleanup

The system SHALL expose an MCP-owned breakpoint cleanup tool that removes only supported BSL line
breakpoints created or explicitly owned by the MCP bridge. The tool SHALL preserve user-owned
breakpoints by default and SHALL report skipped protected breakpoints separately from removed
breakpoints.

#### Scenario: Client cleans up temporary MCP breakpoints

- **WHEN** a client invokes the cleanup tool after one or more temporary MCP-owned BSL breakpoints
  exist
- **THEN** the server removes those MCP-owned breakpoints through the supported breakpoint backend
- **AND** the response reports removed, skipped, failed, and remaining counts
- **AND** user-owned breakpoints remain registered
