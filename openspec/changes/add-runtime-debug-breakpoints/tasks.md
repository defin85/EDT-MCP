Beads execution: `EDTMCP-zwo` with phase tasks `EDTMCP-zwo.1` through `EDTMCP-zwo.4` and
concrete child tasks `EDTMCP-zwo.1.1` through `EDTMCP-zwo.4.4` mirroring this checklist.

## 1. Live Discovery

- [x] 1.1 Create a BSL line breakpoint manually in live EDT and record its breakpoint class, marker
      type, marker attributes, debug model identifier, enabled-state representation, and workspace
      resource path.
- [x] 1.2 Confirm whether EDT exposes a safe exported helper/factory for BSL breakpoint creation; if
      not, document the verified marker-based creation path or mark creation unsupported.
- [x] 1.3 Confirm whether MCP-created breakpoints can be non-persisted and whether a safe ownership
      marker attribute can identify agent-created breakpoints without breaking EDT.
- [x] 1.4 Confirm the canonical MCP source-location input shape: `projectName`, `modulePath`
      relative to `src/`, and 1-based `lineNumber`; document any EDT-discovered exception before
      implementation.

Discovery note: the earlier manual breakpoint was no longer present in either live EDT breakpoint
manager when probed (`breakpointCount: 0`). The backend contract was captured by static inspection
of the installed EDT debug bundle and by a live attached EDT probe that created, dumped, and deleted
a temporary BSL line breakpoint at
`/Демонстрационная_конфигурация_Управляемое_приложение/src/Documents/Заказ/Forms/ФормаДокумента/Module.bsl:222`.
The verified backend is `BslLineBreakpoint` + marker
`com._1c.g5.v8.dt.debug.core.bslLineBreakpointMarker` + model id
`com._1c.g5.v8.dt.debug`; default attributes include `org.eclipse.debug.core.enabled=true`,
`org.eclipse.debug.core.id=com._1c.g5.v8.dt.debug`, `lineNumber`, current hit count `0`, and
runtime platform version `NONE`. `setPersisted(false)` produced
`org.eclipse.debug.core.persisted=false` and `transient=true`, and a custom MCP ownership marker
attribute was retained until deletion.

## 2. Breakpoint Bridge

- [x] 2.1 Add a shared runtime breakpoint bridge that lists EDT BSL line breakpoints through the
      Eclipse/EDT breakpoint model and filters unrelated breakpoint types.
- [x] 2.2 Add source-resolution logic from `projectName` plus source/module path and line number to a
      workspace-backed EDT file, rejecting absolute paths, paths outside `src/`, closed projects,
      non-existing files, non-BSL files, and out-of-range line numbers.
- [x] 2.3 Add stable MCP breakpoint identifiers and fail-closed errors for missing, stale,
      unsupported, ambiguous, or out-of-workspace breakpoint targets.
- [x] 2.4 Make breakpoint creation idempotent for an existing breakpoint at the same source line and
      return `created`, `preExisting`, ownership, registered, and persisted metadata.
- [x] 2.5 Protect pre-existing user breakpoints from default cleanup; require an explicit override to
      remove a supported breakpoint that was not created or owned by MCP.
- [x] 2.6 Run breakpoint marker mutations under the appropriate workspace/file scheduling rule and
      avoid dependence on an open editor or UI selection.

## 3. MCP Tools

- [x] 3.1 Implement `list_debug_breakpoints` with project/source filters and enabled-state metadata.
- [x] 3.2 Implement `set_debug_breakpoint` for supported BSL line breakpoints.
- [x] 3.3 Implement `remove_debug_breakpoint` by MCP breakpoint identifier, with default protection
      for pre-existing user breakpoints.
- [x] 3.4 Register the new tools and keep existing runtime debug tools backward-compatible.

## 4. Verification

- [x] 4.1 Add focused Tycho/JUnit coverage for breakpoint listing/mapping, source resolution,
      duplicate handling, and fail-closed errors.
- [x] 4.2 Run the smallest relevant local build/test gate for the touched plugin and test modules.
- [x] 4.3 Update `README.md` and generated agent docs with the supported breakpoint surface,
      limitations, and live verification flow.
- [ ] 4.4 Live-verify in EDT: create a breakpoint through MCP, launch/debug the runtime, hit the
      breakpoint, inspect stack and variables using the existing debug tools, execute a successful
      thread-level step/resume action, and remove the breakpoint through MCP.

Verification note: Tycho/JUnit coverage now covers breakpoint tool schemas, fail-closed
input/stale-id handling, fail-closed unsupported backend classification, and a workspace lifecycle
test that executes set/list/duplicate/remove when the EDT debug core bundle is present. The local
headless Tycho target does not include `com._1c.g5.v8.dt.debug.core`, so that lifecycle test
asserts `unsupported_backend_capability` locally; the real backend creation/deletion contract is
covered by the live attached EDT probe recorded above. `RuntimeDebugControlToolTest` now passes
14/14 in the full Tycho run. `mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server,tests/com.ditrix.edt.mcp.server.tests -am -DskipTests compile --batch-mode --no-transfer-progress`
passed. `openspec validate add-runtime-debug-breakpoints --strict --no-interactive`,
`python3 scripts/generate_agent_refs.py --check`, and `git diff --check` passed after the latest
task-note update. The broader `verify` attempt is not green because
`YaxUnitRuntimeAdapterCompatibilityTest.testBuildConfigJsonUsesLegacyMinimalShape` fails in the
pre-existing warm-session/YAxUnit area, outside the breakpoint implementation. MCP-level live
verification remains open until the newly built plugin is installed into EDT. A skipped-test
repository build produced the installable update site at
`mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site/` with
`com.ditrix.edt.mcp.server_1.0.0.202604241333.jar`; the currently running live EDT MCP runtime still
reports installed bundle `1.0.0.202604241112`.
