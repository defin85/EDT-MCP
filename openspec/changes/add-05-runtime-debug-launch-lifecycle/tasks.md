## 1. Contract And Discovery

- [x] 1.1 Define the public response schema for launch snapshots, phase states, sanitized process
      metadata, `/DEBUGGERURL`, debug targets, `projectName`/`applicationId` filters and filtering
      reasons.
- [x] 1.2 Add discovery notes for EDT launch/process APIs available on the supported EDT versions,
      including what can and cannot expose PID and command line on WSL/Windows.
- [x] 1.3 Define duplicate-launch matching rules for `projectName`, `applicationId`, launch config
      type and debug mode, plus session-scoped `launchId` lifetime rules.

## 2. Runtime Debug Launch Bridge

- [x] 2.1 Add a shared launch lifecycle bridge that can enumerate RuntimeClient launches and
      classify states: launch config, runtime process, debugger attach, supported thread visibility
      and unsupported/filter reasons.
- [x] 2.2 Implement process metadata extraction with redaction and explicit
      `process_details_unavailable` outcomes.
- [x] 2.3 Add duplicate-launch preflight shared by `debug_launch` and `run_to_debug_breakpoint`.
- [x] 2.4 Add bounded attach wait that polls the launch lifecycle bridge without installing a
      breakpoint and matches on `projectName` plus `applicationId`.
- [x] 2.5 Add targeted launch termination that requires an unambiguous RuntimeClient/application
      match, uses Eclipse `ILaunch`/`IProcess` termination only, and refuses ambiguous, raw-PID or
      unrelated processes.
- [x] 2.6 Replace unbounded UI-thread launch invocation with a bounded/cancellable launch request
      path that reports `debug_launch_ui_blocked` or in-flight state without claiming fully attached
      debugger readiness.

## 3. MCP Tools And Existing Tool Updates

- [x] 3.1 Register `list_debug_launches` with launch/process/handshake diagnostics.
- [x] 3.2 Update `debug_launch` to return phase evidence and operator-friendly
      `debug_launch_already_running`, `old_debug_session_detected` and `debug_launch_ui_blocked`
      failures.
- [x] 3.3 Register `wait_debug_session(projectName, applicationId, timeoutSeconds)` and reuse it
      from launch workflows where appropriate.
- [x] 3.4 Register `terminate_debug_launch(projectName, applicationId, launchId?)` with strict
      target protection.
- [x] 3.5 Update `run_to_debug_breakpoint` so launch mode refuses duplicate runtime clients and
      points the operator to reuse/wait/terminate/manual attach paths.
- [x] 3.6 Update `list_debug_sessions` so `count=0` can include
      `unsupportedLaunches`/`filteredLaunches` with explicit reasons.

## 4. Documentation And Smoke

- [x] 4.1 Update README and generated agent docs for the new tools and phase semantics.
- [x] 4.2 Document that 1c-mcp `debug_execute_bsl` does not trigger EDT breakpoints; runtime
      variable checks require code executing inside the debug-launched EDT client/server thread.
- [x] 4.3 Add a reproducible variables smoke scenario that sets a known breakpoint, launches or
      waits, reads `threadId`/`frameId`/variables, performs `step_over`, then cleans up breakpoints
      and stale launch state.

## 5. Verification

- [x] 5.1 Add focused Tycho/JUnit coverage for launch classification, duplicate guardrails,
      filtered launch reasons, command-line redaction, wait timeout, bounded UI launch invocation
      outcomes and targeted termination ambiguity.
- [x] 5.2 Run the smallest relevant Maven/Tycho gate for runtime debug modules.
- [x] 5.3 Run `openspec validate add-05-runtime-debug-launch-lifecycle --strict --no-interactive`.
- [x] 5.4 After installing the built bundle into live EDT, run the launch/session/variables smoke on
      a supported workspace or record the exact live evidence gap.

      Evidence gap recorded 2026-04-29: live TP1141 endpoint `http://172.24.192.1:8767/mcp`
      still runs installed bundle `com.ditrix.edt.mcp.server` `1.0.0.202604270759`
      (`buildQualifier=202604270759`, EDT `2024.2.5.16`). `tools/list` exposes only
      `debug_launch` and `list_debug_sessions` from this change area; new source tools
      `list_debug_launches`, `wait_debug_session`, and `terminate_debug_launch` are not installed
      there yet. Live smoke is blocked at installed runtime surface before launch/attach phases.
