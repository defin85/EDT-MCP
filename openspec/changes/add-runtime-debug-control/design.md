## Context

`debug_launch` already uses the Eclipse debug platform to start a supported EDT runtime in
`DEBUG_MODE`, but the repository does not expose any follow-up control surface over MCP. Agents can
start debugging, then immediately lose visibility into the created session. The new capability
should expose enough of the runtime debugger to let an MCP client inspect and drive a suspended
session without pretending that the full EDT debugger UI is remotely available.

## Goals / Non-Goals

- Goals:
  - expose supported EDT runtime debug sessions through MCP
  - let clients inspect suspended stacks and variables without raw Eclipse object handles
  - provide basic execution control (`resume`, `suspend`, `step_*`, `terminate`)
  - keep `debug_launch` backward-compatible and composable with the new tools
- Non-Goals:
  - implement breakpoint management in this rollout
  - implement expression evaluation, watch expressions, or arbitrary debugger UI state
  - expose unrelated Eclipse debug launches that do not match the supported 1C runtime model
  - require MCP Tasks for the first debugger-control rollout

## Decisions

- Decision: keep `debug_launch` as launch orchestration and expose debugging through separate MCP
  tools.
  - Alternatives considered:
    - change `debug_launch` to return stateful debugger handles directly
    - overload `debug_launch` with launch plus first-step inspection data
  - Rationale: launch and debugger control have different lifecycles, and preserving the existing
    `debug_launch` contract avoids unnecessary client breakage.

- Decision: expose snapshot-based identifiers instead of raw Eclipse object references.
  - Alternatives considered:
    - serialize internal object identities directly
    - rely only on array indexes with no snapshot semantics
  - Rationale: raw identities are not portable to MCP clients, while explicit snapshot-scoped IDs
    make stale-context failures understandable and testable.

- Decision: scope the first rollout to supported EDT runtime debug sessions and basic execution
  control only.
  - Alternatives considered:
    - expose every visible Eclipse debug launch
    - add breakpoints and expression evaluation immediately
  - Rationale: the 1C:EDT debug model is the relevant product surface, and narrowing the scope
    keeps verification feasible.

- Decision: return explicit precondition and unsupported-capability errors instead of silently
  degrading.
  - Alternatives considered:
    - fabricate partial data for running threads
    - no-op unsupported actions and report success
  - Rationale: debugger state is highly dynamic, so pretending success would make agent behavior
    unsafe and difficult to diagnose.

## Risks / Trade-offs

- The 1C:EDT debug backend may not expose every desired operation through stable public APIs.
- Thread and frame identities can become stale after resume/step transitions, which increases the
  need for precise error semantics.
- Variable trees may be large or expensive to resolve and may need explicit truncation rules.
- Live verification requires a real suspended EDT runtime session; source-only checks are not
  enough.

## Migration Plan

1. Record the supported live debug-session matrix for the current EDT compatibility line.
2. Add a shared snapshot/adapter layer over the Eclipse debug model.
3. Implement discovery, stack, and variable inspection tools.
4. Implement basic control actions and safe precondition handling.
5. Update README and agent-facing verification notes with the supported debugger matrix.

## Open Questions

- Should `list_debug_sessions` include only sessions created by `debug_launch`, or every supported
  EDT runtime debug session visible in the workspace?
- How much variable-tree expansion should the first rollout return by default before pagination or
  depth limits are required?
