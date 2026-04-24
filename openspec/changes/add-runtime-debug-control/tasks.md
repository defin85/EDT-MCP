## 1. Capability Contract

- [x] 1.1 Define the public MCP tool surface for runtime debug control, including supported tool
      names, action names, bounded variable-expansion parameters, and snapshot identifier rules.
- [x] 1.2 Define the supported-session filter and document which EDT runtime debug sessions are in
      scope for this rollout, including the observed EDT launch/debug model identifiers from a live
      `debug_launch`.

## 2. Runtime Adapter

- [x] 2.1 Add a shared adapter that maps supported Eclipse/EDT debug sessions into stable MCP
      snapshots for sessions, threads, frames, and variable values.
- [x] 2.2 Add explicit resolution and precondition checks for missing sessions, stale snapshot IDs,
      running threads, and unsupported backend capabilities.

## 3. MCP Tools

- [x] 3.1 Implement `list_debug_sessions` with session summaries and thread state needed for
      follow-up inspection calls.
- [x] 3.2 Implement `get_debug_stack` for suspended threads with source-location data.
- [x] 3.3 Implement `get_debug_variables` for suspended frames with explicit truncation or
      unsupported-expansion markers and bounded expansion controls.
- [x] 3.4 Implement `control_debug_session` for `resume`, `suspend`, `step_over`, `step_into`,
      `step_return`, and `terminate`.
      - Live EDT 2024.2.5.16 verification showed target-level `suspend` can report a suspended
        debug target without thread stack frames and without a usable target-level `resume`;
        `resume`, `suspend`, and step actions are therefore thread-level only in this rollout.
- [x] 3.5 Keep `debug_launch` backward-compatible while making launched sessions discoverable
      through the new tools.

## 4. Verification

- [x] 4.1 Add focused Tycho/JUnit coverage for snapshot mapping, stale-context errors, and basic
      control preconditions.
- [x] 4.2 Verify the end-to-end flow in a real EDT runtime: `debug_launch` or an equivalent
      supported launch, session discovery, suspended stack inspection, variable inspection, and at
      least one successful step/resume action.
      - Live evidence on 2026-04-24: bundle `1.0.0.202604241112` was installed from the
        local update site and exposed all debug tools; `debug_launch` started the thin client;
        `list_debug_sessions` discovered the EDT runtime target and two `RuntimeDebugTargetThread`
        entries. With a real BSL breakpoint, the server thread became suspended with stack frames;
        `get_debug_stack` returned two `BslStackFrame` entries, including the top BSL frame at
        line 222 with variables; `get_debug_variables` returned four bounded `BslVariable` entries
        without truncation; `control_debug_session` executed `step_over` successfully on the
        suspended thread; post-step polling and stack inspection showed the server thread suspended
        again at line 227.
      - Fail-closed checks also covered `thread_running` for stack reads before the breakpoint,
        `thread_id_required` for session-level `suspend`, and `unsupported_backend_capability` when
        EDT reported `canSuspend=false` for a running thread.
- [x] 4.3 Update `README.md` and agent-facing docs with the supported debugger matrix, limitations,
      and verification commands.
