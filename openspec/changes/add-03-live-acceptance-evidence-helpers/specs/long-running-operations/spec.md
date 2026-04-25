## ADDED Requirements

### Requirement: Safer Object Revalidation Modes

The system SHALL extend object-scoped `revalidate_objects` with explicit safety controls where the
backend can enforce them: dry-run preflight, bounded timeout, fail-fast behavior, and risk reporting
for objects or conditions known to be slow or unsafe to validate blindly.

#### Scenario: Client preflights risky object revalidation

- **WHEN** a client invokes `revalidate_objects` with `dryRun` for one or more object FQNs
- **THEN** the server resolves the requested objects and reports whether the validation path is
  supported, risky, busy, or unsupported without scheduling heavy validation work
- **AND** known risky objects or conditions are returned as explicit warnings with recommended
  follow-up behavior

#### Scenario: Client bounds object revalidation

- **WHEN** a client invokes object-scoped `revalidate_objects` with `timeoutSeconds` or `failFast`
- **THEN** the server enforces the requested bound when the EDT backend supports it
- **AND** if the backend cannot safely enforce the bound, the response reports unsupported safety
  semantics instead of pretending bounded validation was guaranteed
