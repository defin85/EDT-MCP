## ADDED Requirements

### Requirement: Debug Launch Inventory And Handshake Diagnostics

The system SHALL expose `list_debug_launches` for launch-level runtime debug diagnostics. The tool
SHALL enumerate relevant Eclipse debug launches and RuntimeClient launch configurations, report
project/application context when resolvable, classify launch state, expose runtime process metadata
when safely available, and explain why each launch is supported, unsupported, or filtered out of
`list_debug_sessions`. Launch identity SHALL include project/application context when available;
`launchId` values returned for targeted cleanup SHALL be valid only for the current EDT
workspace/runtime snapshot and clients SHALL refresh them after EDT restart or workspace reload.

#### Scenario: Client inspects active debug launches

- **WHEN** a client invokes `list_debug_launches`
- **THEN** the response includes launch identifiers, launch configuration names, launch type,
  project/application identifiers, lifecycle state, debug mode and debug target summaries
- **AND** for runtime processes it includes PID, executable, sanitized command-line metadata and
  `/DEBUGGERURL` presence or value when available
- **AND** unavailable process metadata is reported as an explicit diagnostic state instead of being
  guessed
- **AND** raw command-line details are redacted before response serialization and before MCP-owned
  logging

#### Scenario: Debug target exists but is not supported

- **WHEN** a launch exists in Eclipse `DebugPlugin` but cannot be used by the supported runtime
  debug bridge
- **THEN** `list_debug_launches` reports the launch in an unsupported or filtered section
- **AND** the response includes a reason such as `wrong_launch_type`, `no_project_mapping`,
  `no_application_mapping`, `no_debug_targets`, `no_threads` or `unsupported_debug_model`

### Requirement: Phased Debug Launch Result And Bounded Attach Wait

The system SHALL make `debug_launch` honest about launch lifecycle phases and SHALL provide
`wait_debug_session` for waiting until a supported EDT runtime debug session is visible without
requiring a breakpoint. Launch lifecycle phases SHALL include at least `launch_config_started`,
`runtime_process_started`, `debug_target_attached`, and `supported_thread_visible`. The launch
invocation itself SHALL be bounded; MCP request handling SHALL NOT block indefinitely inside an EDT
UI-thread launch call.

#### Scenario: Launch request starts a client but debugger attach is still pending

- **WHEN** a client invokes `debug_launch` and EDT accepts the launch request
- **AND** no supported runtime debug thread is visible yet
- **THEN** the response remains backward-compatible for the accepted launch request
- **AND** the phase payload marks `supported_thread_visible` as pending, false or timeout
- **AND** the response points the client to `wait_debug_session` or `list_debug_launches` for the
  next bounded inspection step

#### Scenario: EDT UI launch invocation blocks

- **WHEN** `debug_launch` cannot enter or finish the EDT UI launch callback within the bounded call
  window
- **THEN** the response fails closed with reason `debug_launch_ui_blocked` or reports an explicit
  in-flight launch invocation state
- **AND** it states whether the callback was cancelled before launch or had already entered EDT code
- **AND** if the callback was cancelled before launch, it does not start the runtime after returning

#### Scenario: Client waits for attach without a breakpoint

- **WHEN** a client invokes `wait_debug_session` with `projectName`, `applicationId` and
  `timeoutSeconds`
- **AND** a supported runtime debug thread becomes visible before the timeout
- **THEN** the response returns the matching session/thread identifiers and the final phase state
- **AND** the tool stops waiting after returning and does not leave a background poller

#### Scenario: Attach wait times out

- **WHEN** no supported runtime debug thread becomes visible before the requested timeout
- **THEN** the response reports a timeout outcome with the last known launch/process/debug-target
  diagnostics
- **AND** it does not claim that stack or variables are available

### Requirement: Duplicate Debug Launch Guardrail

The system SHALL prevent MCP tools from starting a second `/DEBUG` runtime client for the same
project/application when a matching RuntimeClient launch or debug process is already running. The
guardrail SHALL match on `projectName`, `applicationId`, RuntimeClient launch type and debug mode,
and SHALL apply to `debug_launch` and to launch-mode `run_to_debug_breakpoint`.

#### Scenario: Client launches while matching runtime already exists

- **WHEN** a client invokes `debug_launch` for a `projectName` and `applicationId`
- **AND** a matching RuntimeClient launch or debug process already exists for that project and
  application
- **THEN** the tool fails closed with reason `debug_launch_already_running`
- **AND** the response includes enough launch/session diagnostics to choose one of: reuse a
  `threadId`, call `wait_debug_session`, call `terminate_debug_launch`, or attach/clean up manually

#### Scenario: Run-to-breakpoint would start a duplicate runtime

- **WHEN** a client invokes `run_to_debug_breakpoint` without `threadId`
- **AND** a matching RuntimeClient launch or debug process already exists for the requested
  `projectName` and `applicationId`
- **THEN** the helper does not start another client
- **AND** it returns the same duplicate-launch reason and operator choices as `debug_launch`

### Requirement: Targeted Debug Launch Termination

The system SHALL expose `terminate_debug_launch` for cleaning up a selected stale RuntimeClient
debug launch. The tool SHALL terminate only the launch/process that maps to the requested
project/application and SHALL fail closed when the target is ambiguous or unrelated. Termination
SHALL use Eclipse `ILaunch`/`IProcess` termination capabilities only; raw OS PID termination is out
of scope for this capability.

#### Scenario: Client terminates one stale launch

- **WHEN** a client invokes `terminate_debug_launch` with `projectName` and `applicationId`
- **AND** exactly one matching RuntimeClient debug launch is found
- **THEN** the tool terminates that launch and associated runtime process if supported
- **AND** the response reports the launch id, process id if known, termination method and final
  observed state

#### Scenario: Multiple matching launches exist

- **WHEN** more than one RuntimeClient debug launch matches the requested project/application
- **THEN** the tool fails with reason `multiple_matching_launches`
- **AND** the response requires a `launchId` from `list_debug_launches` before terminating anything

#### Scenario: Target does not map to the requested project/application

- **WHEN** the requested launch or process cannot be proven to belong to the requested
  project/application
- **THEN** the tool fails closed
- **AND** it does not terminate EDT, unrelated 1C clients, or raw OS PIDs outside the Eclipse
  launch/process model

### Requirement: Session Discovery Reports Filtered Launches

The system SHALL improve `list_debug_sessions` so that an empty supported-session result can still
return launch-level diagnostics when a launch or runtime debug process exists but was not accepted
by the supported session filter.

#### Scenario: Supported session count is zero but launch exists

- **WHEN** a client invokes `list_debug_sessions`
- **AND** the supported session `count` is zero
- **AND** matching launch or runtime process diagnostics exist
- **THEN** the response includes `unsupportedLaunches` or `filteredLaunches`
- **AND** each entry explains why it is not a supported session

### Requirement: Reproducible Runtime Variables Smoke And Tooling Boundary

The repository SHALL provide a reproducible smoke workflow for runtime variables and SHALL document
that 1c-mcp `debug_execute_bsl` is not an EDT breakpoint trigger. Runtime variable verification
SHALL execute code inside a debug-launched EDT runtime client or server thread.

#### Scenario: Smoke collects variables from a guaranteed breakpoint

- **WHEN** the smoke is run against a configured live EDT workspace and application
- **THEN** it sets or reuses a known executable BSL breakpoint, launches or waits for the runtime
  debug session, obtains `threadId`, `frameId` and variables, performs `step_over`, and cleans up
  MCP-owned breakpoints and stale launch state
- **AND** if live EDT runtime evidence is unavailable, the smoke reports the exact blocked phase
  instead of claiming variable support

#### Scenario: Operator tries to use debug_execute_bsl for EDT breakpoint proof

- **WHEN** documentation or workflow describes runtime-variable verification
- **THEN** it states that 1c-mcp `debug_execute_bsl` does not trigger EDT breakpoints
- **AND** it directs operators to execute code in the debug-launched EDT client/server thread for
  breakpoint and variable evidence
