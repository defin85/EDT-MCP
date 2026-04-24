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

- Decision: `list_debug_sessions` includes every currently visible supported EDT runtime debug
  session, not only sessions created by `debug_launch`.
  - Supported means the Eclipse launch has the EDT runtime launch configuration type
    `com._1c.g5.v8.dt.launching.core.RuntimeClient`, has resolvable EDT project/application
    attributes, and exposes Eclipse debug model elements needed for the requested operation.
  - Alternatives considered:
    - include only sessions launched by this MCP server
    - expose all Eclipse debug launches and let clients filter
  - Rationale: agents often need to attach to a session already started from EDT UI, but exposing
    arbitrary Eclipse launches would make the contract depend on debug models outside the 1C:EDT
    runtime surface.

- Decision: variable inspection is bounded and explicit about truncation.
  - `get_debug_variables` returns a shallow frame view by default and accepts bounded expansion
    controls for a selected variable path in the current frame snapshot.
  - Responses must include `hasChildren`, `truncated`, and `expansionUnsupported` markers where
    relevant instead of silently omitting children.
  - Alternatives considered:
    - recursively serialize the full variable tree
    - require a separate expansion tool in the first rollout
  - Rationale: debug values can be large or slow to resolve, and a single bounded tool keeps the
    first MCP surface small while still allowing clients to drill into values deliberately.

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
- Variable values can expose local secrets or personal data from the debugged infobase; this is
  acceptable only as a local trusted-tool capability and must be documented clearly.
- Step and resume actions are state transitions, not long-running MCP tasks. A control call should
  dispatch the requested action and return the immediate known state plus a poll hint for
  `list_debug_sessions`/`get_debug_stack` instead of blocking until the next suspension point.
- Live verification requires a real suspended EDT runtime session; source-only checks are not
  enough.

## Migration Plan

1. Record the supported live debug-session matrix for the current EDT compatibility line.
2. Add a shared snapshot/adapter layer over the Eclipse debug model.
3. Implement discovery, stack, and variable inspection tools.
4. Implement basic control actions and safe precondition handling.
5. Update README and agent-facing verification notes with the supported debugger matrix.

## Live Discovery Matrix

- 2026-04-24 live EDT target: EDT 2024.2.5.16, DemoEDT workspace, installed bundle
  `1.0.0.202604240918`.
- Launch configuration observed in workspace:
  - type: `com._1c.g5.v8.dt.launching.core.RuntimeClient`
  - project: `Демонстрационная_конфигурация_Управляемое_приложение`
  - applicationId: `8e939665-b67a-4ebe-a233-2a9bbc3c2251`
  - client type: `com._1c.g5.v8.dt.platform.services.core.componentTypes.ThinClient`
- Runtime diagnostics observed active EDT debug infrastructure:
  - `com._1c.g5.v8.dt.internal.debug.core.model.RuntimeDebugTargetThread`
  - `com._1c.g5.v8.dt.internal.debug.core.model.BslStackFrame`
  - `com._1c.g5.v8.dt.internal.debug.core.model.BslVariable`
  - `com._1c.g5.v8.dt.internal.debug.core.model.values.BslPrimitiveValue`
  - `com._1c.g5.v8.dt.debug.core.model.values.BslValuePath`
  - `com._1c.g5.v8.dt.internal.debug.core.runtime.client.RuntimeDebugHttpClient`
  - `com._1c.g5.v8.dt.internal.debug.core.model.RuntimeEventDispatchJob`
- Implementation consequence: compile against standard Eclipse debug interfaces
  (`ILaunch`, `IDebugTarget`, `IThread`, `IStackFrame`, `IVariable`, `IValue`) and keep the
  1C internal classes as runtime evidence only, not plugin dependencies.
