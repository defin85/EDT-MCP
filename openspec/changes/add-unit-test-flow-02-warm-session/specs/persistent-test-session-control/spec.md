## ADDED Requirements

### Requirement: Explicit Warm Test Session Preparation

The system SHALL provide an explicit MCP surface for preparing a persistent unit-test session for a
supported target before running tests.

#### Scenario: Client prepares a warm session

- **WHEN** a client invokes `prepare_test_session` for a supported `projectName`, `applicationId`,
  and provider
- **THEN** the server creates or attaches a persistent test session for that exact target
- **AND** the result includes stable `sessionId`, lifecycle state, and target identity fields
- **AND** the server does not pretend the session is ready if provider/runtime preparation failed

### Requirement: Warm Session Reuse For Unit Test Runs

The system SHALL allow `run_unit_tests` to reuse a healthy persistent test session according to an
explicit reuse policy.

#### Scenario: Client reruns tests on a healthy warm session

- **WHEN** a client invokes `run_unit_tests` with a reuse policy that allows warm execution
- **AND** a matching persistent session is in `ready` state for the same target identity
- **THEN** the server reuses that session instead of forcing a cold runtime launch
- **AND** the final result explicitly indicates that the run used warm-session reuse

### Requirement: Fail-Closed Session Invalidation

The system SHALL fail closed when a persistent test session can no longer be trusted for reuse.

#### Scenario: Warm session became stale

- **WHEN** the server detects target mutation, runtime disconnect, explicit recycle, or another
  invalidation event after session preparation
- **THEN** the session state changes to `stale` or `dead` with a machine-readable reason
- **AND** the server SHALL NOT silently reuse that session for `require_warm` execution
- **AND** the client receives an actionable outcome instead of an optimistic rerun on stale state

### Requirement: Session Status Inspection And Recycling

The system SHALL provide read-only inspection and explicit recycle control for persistent unit-test
sessions.

#### Scenario: Client inspects current session state

- **WHEN** a client invokes `get_test_session_status` with a known `sessionId`
- **THEN** the server returns current lifecycle state, liveness, stale reason when present, and
  target identity fields

#### Scenario: Client explicitly recycles a warm session

- **WHEN** a client invokes `recycle_test_session` for a known session
- **THEN** the server invalidates or restarts that session according to the supported policy
- **AND** the result makes it explicit whether the old session was terminated, replaced, or only
  marked stale
