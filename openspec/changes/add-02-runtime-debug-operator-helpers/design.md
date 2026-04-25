## Context

`add-runtime-debug-control` and `add-runtime-debug-breakpoints` intentionally exposed primitive
debugger operations first. That was the right base layer, but it leaves frequent operator checks too
manual for agents:

- inspect a value or condition at the current suspended frame
- drive execution to a specific BSL line and immediately collect evidence
- clean up all temporary breakpoints created by the MCP bridge

The next layer should be one-shot and bounded. It should compose the existing primitives without
claiming EDT can provide a full remote debugger UI.

## Goals / Non-Goals

- Goals:
  - evaluate expressions in the context of a current suspended BSL frame when EDT supports it
  - provide a run-to-breakpoint helper that returns direct evidence for the reached code path
  - remove MCP-owned temporary breakpoints as a hygiene operation
  - preserve user-owned breakpoints by default
  - report timeouts and unsupported backend capabilities as normal fail-closed tool outcomes
- Non-Goals:
  - mutate variable values or arbitrary debug state through expression evaluation
  - implement conditional breakpoints or hit-count breakpoints
  - expose arbitrary Eclipse debug models outside supported 1C:EDT runtime sessions
  - make wait helpers unbounded or task-backed by default

## Decisions

- Decision: make expression evaluation frame-scoped.
  - `evaluate_debug_expression` uses a current snapshot frame identifier from `get_debug_stack`.
  - The tool fails closed for stale frames, running threads, unsupported backend capability, and
    timeout.
  - Rationale: expression semantics depend on the stack frame and current suspension state; session
    or thread-level evaluation would hide stale-context bugs.

- Decision: treat evaluation as potentially observable and report the risk explicitly.
  - The implementation should prefer EDT APIs that evaluate without resuming execution.
  - If EDT cannot prove that an expression is side-effect-free, the tool response and discovery
    metadata must not describe it as a read-only proof surface.
  - Rationale: BSL expressions can call functions or traverse live objects; planner safety must be
    honest.

- Decision: implement run-to-breakpoint as a bounded workflow helper, not as a hidden background
  process.
  - The helper may create a temporary MCP-owned breakpoint, launch or continue the selected runtime,
    poll for suspend until `timeoutSeconds`, and return terminal state plus evidence.
  - On timeout it returns the last known debug/session state and cleanup outcome instead of leaving
    an invisible wait loop running.
  - Rationale: agents need a single operator action, but they also need deterministic control over
    local EDT runtime state.

- Decision: clean up temporary breakpoints by ownership metadata.
  - Cleanup targets only breakpoints created or explicitly marked as MCP-owned.
  - User-owned breakpoints are reported but not removed unless a later approved requirement adds an
    explicit override.
  - Rationale: breakpoint cleanup is useful only if it cannot erase developer state.

## Risks / Trade-offs

- EDT may not expose a stable expression-evaluation API for every supported runtime target.
- Expression evaluation can expose sensitive local runtime values and may execute getters or
  functions, depending on EDT/BSL semantics.
- Run-to-breakpoint can start or resume user-visible runtime processes; the response must make that
  state transition explicit.
- A timeout does not prove that the source line is unreachable; it only proves the bounded wait did
  not observe suspension.
- Cleanup relies on ownership metadata already preserved by the breakpoint bridge.

## Implementation Sketch

1. Discover the EDT expression-evaluation API available from suspended BSL stack frames.
2. Add `evaluate_debug_expression` with frame, expression, timeout, and bounded presentation
   controls.
3. Add a run-to-breakpoint service that reuses source resolution and breakpoint bridge code.
4. Add a cleanup service for MCP-owned breakpoints with dry-run/count metadata.
5. Register tools with conservative annotations and concise workflow descriptions.
6. Update README and generated agent docs.
7. Verify locally with unit tests and live in EDT with a suspended runtime session.
