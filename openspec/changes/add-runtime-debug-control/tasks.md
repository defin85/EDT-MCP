## 1. Capability Contract

- [ ] 1.1 Define the public MCP tool surface for runtime debug control, including supported tool
      names, action names, and snapshot identifier rules.
- [ ] 1.2 Define the supported-session filter and document which EDT runtime debug sessions are in
      scope for this rollout.

## 2. Runtime Adapter

- [ ] 2.1 Add a shared adapter that maps supported Eclipse/EDT debug sessions into stable MCP
      snapshots for sessions, threads, frames, and variable values.
- [ ] 2.2 Add explicit resolution and precondition checks for missing sessions, stale snapshot IDs,
      running threads, and unsupported backend capabilities.

## 3. MCP Tools

- [ ] 3.1 Implement `list_debug_sessions` with session summaries and thread state needed for
      follow-up inspection calls.
- [ ] 3.2 Implement `get_debug_stack` for suspended threads with source-location data.
- [ ] 3.3 Implement `get_debug_variables` for suspended frames with explicit truncation or
      unsupported-expansion markers.
- [ ] 3.4 Implement `control_debug_session` for `resume`, `suspend`, `step_over`, `step_into`,
      `step_return`, and `terminate`.
- [ ] 3.5 Keep `debug_launch` backward-compatible while making launched sessions discoverable
      through the new tools.

## 4. Verification

- [ ] 4.1 Add focused Tycho/JUnit coverage for snapshot mapping, stale-context errors, and basic
      control preconditions.
- [ ] 4.2 Verify the end-to-end flow in a real EDT runtime: `debug_launch` or an equivalent
      supported launch, session discovery, suspended stack inspection, variable inspection, and at
      least one successful step/resume action.
- [ ] 4.3 Update `README.md` and agent-facing docs with the supported debugger matrix, limitations,
      and verification commands.
