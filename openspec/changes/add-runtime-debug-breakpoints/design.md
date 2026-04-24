## Context

`add-runtime-debug-control` deliberately excluded breakpoint management. The live verification for
that change proved the rest of the bridge works once a real BSL breakpoint suspends a runtime thread:
`list_debug_sessions` saw the suspended server thread, `get_debug_stack` returned BSL frames,
`get_debug_variables` returned bounded variables, and `control_debug_session` executed `step_over`.

The remaining manual handoff is breakpoint placement. A trustworthy implementation must not guess a
marker schema. EDT may store BSL breakpoints through Eclipse breakpoint markers, EDT-specific marker
types, or helper APIs. The first implementation step is therefore live discovery against an actual
EDT-created BSL breakpoint.

## Goals / Non-Goals

- Goals:
  - let MCP clients list BSL line breakpoints visible to the EDT debugger
  - let MCP clients set a BSL line breakpoint by project/source/line
  - let MCP clients remove a breakpoint by stable MCP breakpoint identifier
  - fail closed when source resolution or the EDT breakpoint backend is unknown
  - verify that an MCP-created breakpoint can suspend a real EDT runtime session
- Non-Goals:
  - conditional breakpoints and hit-count conditions
  - watch expressions or expression evaluation
  - value mutation
  - arbitrary Java/Eclipse/non-BSL breakpoint management
  - breakpoint synchronization across remote machines or external workspaces

## Decisions

- Decision: scope the first rollout to workspace-backed BSL line breakpoints.
  - Alternatives considered:
    - expose every Eclipse breakpoint from `DebugPlugin.getDefault().getBreakpointManager()`
    - implement conditional and non-line breakpoints immediately
  - Rationale: the runtime bridge is for 1C:EDT BSL debugging. A narrow source-backed contract is
    easier to verify and avoids exposing unrelated Eclipse debugger state.

- Decision: require explicit source resolution before creating or removing breakpoints.
  - Creation inputs are `projectName`, `modulePath`, and `lineNumber`, where `modulePath` is
    relative to the project's `src/` folder and follows the same convention as existing BSL tools
    such as `read_module_source`, `get_symbol_info`, and `get_content_assist`.
  - Listing responses may also include the full workspace path and backend source presentation, but
    those are output metadata rather than the primary creation contract.
  - The bridge must resolve the location to an EDT workspace `IFile` before touching the breakpoint
    backend and must reject absolute paths, paths outside `src/`, closed projects, non-existing
    files, non-BSL files, and out-of-range line numbers.
  - Rationale: UI selections, display labels, and source lookup strings are not stable MCP inputs.

- Decision: perform live discovery of EDT-created BSL breakpoints before implementing creation.
  - The discovery should record marker type, marker attributes, breakpoint class, debug model
    identifier, enabled-state handling, and whether EDT exposes a public helper/factory.
  - Implementation may use Eclipse `IBreakpoint`/`ILineBreakpoint` interfaces for listing where
    possible, but creation must use the verified EDT-compatible path.
  - Rationale: creating an arbitrary resource marker can corrupt user state or create a marker the
    EDT debugger ignores.

- Decision: use stable MCP breakpoint identifiers, not raw marker IDs.
  - Identifiers should remain valid within the current EDT workspace session after a
    `list_debug_breakpoints` or `set_debug_breakpoint` response and return explicit stale-context or
    not-found errors after marker deletion or workspace refresh.
  - Cross-EDT-restart stability is not guaranteed in this rollout; clients must refresh through
    `list_debug_breakpoints` after restart or workspace reload.
  - Rationale: marker IDs and resource paths are implementation details; clients need predictable
    error semantics.

- Decision: protect user-owned breakpoint state by default.
  - `set_debug_breakpoint` must report whether it created a new breakpoint or reused a pre-existing
    breakpoint on the same source line.
  - Newly created MCP breakpoints should be registered and non-persisted by default when the backend
    supports that distinction. If the backend cannot create a non-persisted breakpoint safely, the
    tool must make persistence explicit in the response.
  - `remove_debug_breakpoint` should remove MCP-created breakpoints by default. Removing a
    pre-existing user breakpoint requires an explicit override parameter so cleanup code cannot
    silently delete user state.
  - Rationale: breakpoint operations mutate local developer state and cleanup after an agent-created
    flow must not erase a breakpoint that existed before the agent touched the line.

- Decision: use Eclipse breakpoint manager operations for registered breakpoint lifecycle.
  - Listing should start from `DebugPlugin.getDefault().getBreakpointManager()` and filter by the
    verified EDT BSL marker type/model identifier rather than scanning arbitrary resource markers.
  - Removal should call the breakpoint manager with marker deletion for supported breakpoints rather
    than deleting marker resources directly.
  - Creation must still use the verified EDT-compatible creation path found during live discovery,
    then register through the breakpoint manager if the creation path does not do so already.
  - Rationale: the Eclipse debug API treats registered breakpoints as more than plain markers; the
    manager is the supported workspace collection boundary.

- Decision: perform marker mutations as bounded workspace operations.
  - Breakpoint creation/removal should run with the workspace/file scheduling rule that covers the
    target resource and should not depend on an open editor or UI selection.
  - The tools should return promptly after the workspace/debug backend accepts or rejects the
    mutation; waiting for execution to hit the breakpoint remains a polling concern for the existing
    runtime debug tools.
  - Rationale: breakpoint markers are workspace state. The implementation must avoid UI-thread-only
    behavior and avoid racing resource changes from EDT background jobs.

- Decision: keep breakpoint tools synchronous for the first rollout.
  - Breakpoint creation/removal should return after EDT accepts or rejects the marker operation.
  - Runtime suspension remains observed through `list_debug_sessions`/`get_debug_stack`.
  - Rationale: setting a local breakpoint should be quick; waiting for execution to hit it belongs
    to the existing debug-session polling flow.

## Risks / Trade-offs

- Live discovery evidence, 2026-04-24:
  - Environment: EDT MCP build `1.0.0.202604241112`, EDT `2024.2.5.16`, EDT install
    `C:\Program Files\1C\1CE\components\1c-edt-2024.2.5+16-x86_64`, workspace
    `E:\Projects\DemoEDT`.
  - The live EDT breakpoint managers in the two `1cedt.exe` JVMs were attach-readable but empty
    after the earlier manual debug session (`breakpointCount: 0`), so no pre-existing manual
    marker instance remained to dump.
  - Installed EDT backend declares BSL line breakpoints in
    `com._1c.g5.v8.dt.debug.core_16.1.100.v202504211014.jar`:
    breakpoint class `com._1c.g5.v8.dt.internal.debug.core.model.breakpoints.BslLineBreakpoint`,
    breakpoint id `com._1c.g5.v8.dt.debug.core.lineBreakpoint`, marker type
    `com._1c.g5.v8.dt.debug.core.bslLineBreakpointMarker`, model id
    `com._1c.g5.v8.dt.debug`.
  - The exported API surface includes
    `com._1c.g5.v8.dt.debug.core.model.breakpoints.IBslBreakpointFactory#createLineBreakpoint(IResource,int)`
    and `IBslLineBreakpoint`; the UI adapter uses the factory, then registers the returned
    breakpoint through `DebugPlugin.getDefault().getBreakpointManager().addBreakpoint(...)`.
  - A live attached EDT probe created and immediately deleted a temporary line breakpoint at
    `/Демонстрационная_конфигурация_Управляемое_приложение/src/Documents/Заказ/Forms/ФормаДокумента/Module.bsl:222`.
    The runtime dump reported `registered=true`, `persisted=true`, marker resource location
    `E:/Projects/DemoEDT/.../Module.bsl`, and `afterDeleteCount=0`.
  - Default marker attributes for the temporary breakpoint were:
    `org.eclipse.debug.core.enabled=true`, `org.eclipse.debug.core.id=com._1c.g5.v8.dt.debug`,
    `lineNumber=222`, `com._1c.g5.v8.dt.debug.core.currentHitCount=0`,
    `com._1c.g5.v8.dt.debug.core.runtimePlatformVersion=NONE`, plus EDT/Eclipse-generated
    `message`.
  - `IBreakpoint#setPersisted(false)` is accepted for a BSL line breakpoint and materializes marker
    attributes `org.eclipse.debug.core.persisted=false` and `transient=true`; a custom ownership
    marker attribute `com.ditrix.edt.mcp.server.owner=probe` was retained until deletion.
  - Source line numbering is 1-based: EDT UI adapter converts `ITextSelection.getStartLine() + 1`,
    and the live probe created the marker with `lineNumber=222` against a workspace `IResource`.

- EDT BSL breakpoint creation may require internal APIs rather than stable public Eclipse classes.
  The implementation must either find a safe exported API or fail closed with documented runtime
  limitations.
- Source paths reported by `get_debug_stack` may not match workspace module paths one-to-one.
  The tool contract needs explicit source-resolution errors instead of fuzzy best-effort matching.
- Breakpoint creation mutates local developer state. The response must make idempotent existing
  breakpoints explicit and avoid duplicate markers for the same source line.
- Removing pre-existing breakpoints is the highest local-state risk. Default cleanup must remove
  only breakpoints that the bridge created or explicitly owns.
- Non-persisted breakpoints reduce long-term state pollution but may not survive EDT restart; this is
  intentional for agent-created debug flows and must be visible in the response.
- Live verification requires a real EDT runtime and a code path that executes the requested line.

## Migration Plan

1. Live-discover one manually created BSL line breakpoint in EDT and record the marker/API contract.
2. Add a breakpoint bridge that resolves workspace source locations and maps EDT/Eclipse breakpoints
   to MCP DTOs.
3. Implement `list_debug_breakpoints`, `set_debug_breakpoint`, and `remove_debug_breakpoint`.
4. Add focused Tycho/JUnit coverage for source resolution, duplicate handling, stale/not-found
   errors, and unsupported backend cases.
5. Update README and generated agent docs.
6. Live-verify: set a breakpoint through MCP, launch/debug the runtime, hit the breakpoint, inspect
   stack/variables, and execute a thread-level step/resume action.

## Resolved Discovery Questions

- EDT 2024.2.5.16 uses `com._1c.g5.v8.dt.debug.core.bslLineBreakpointMarker` with model id
  `com._1c.g5.v8.dt.debug` for BSL line breakpoints.
- EDT exposes the exported `IBslBreakpointFactory#createLineBreakpoint(IResource,int)` contract; if
  the MCP bundle cannot obtain the injected factory, the only acceptable fallback is the verified
  marker path recorded above.
- MCP-created breakpoints can be marked non-persisted through `IBreakpoint#setPersisted(false)`, and
  EDT preserved a custom ownership marker attribute during the live probe.
