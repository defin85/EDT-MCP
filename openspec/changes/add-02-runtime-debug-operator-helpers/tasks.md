## 1. Discovery

- [x] 1.1 Identify the EDT/Eclipse debug API for evaluating expressions from a suspended BSL stack
      frame, including timeout and unsupported-backend behavior.
- [x] 1.2 Confirm whether evaluation can be treated as read-only; if not, document the observable
      side-effect risk in tool metadata and docs.
- [x] 1.3 Confirm the existing breakpoint ownership marker is sufficient for cleanup across
      list/set/remove and temporary run-to-breakpoint flows.

## 2. Debug Helper Implementation

- [x] 2.1 Implement `evaluate_debug_expression` with frame-scoped preconditions, bounded execution,
      stale-context handling, and structured success/error payloads.
- [x] 2.2 Implement a one-shot run-to-breakpoint helper that sets or reuses an MCP-owned temporary
      BSL breakpoint, launches or continues execution, waits for suspend or timeout, and returns
      stack/variable evidence.
- [x] 2.3 Implement MCP-owned breakpoint cleanup with dry-run/count metadata and default protection
      for user-owned breakpoints.
- [x] 2.4 Register the new tools with conservative descriptions, annotations, and generated catalog
      metadata.

## 3. Verification

- [x] 3.1 Add focused Tycho/JUnit coverage for expression preconditions, stale frame rejection,
      timeout handling, and unsupported backend classification.
- [x] 3.2 Add focused coverage for run-to-breakpoint cleanup behavior and user-breakpoint
      preservation.
- [x] 3.3 Run the smallest relevant Maven/Tycho gate for the touched runtime-debug modules.
- [x] 3.4 Run `openspec validate add-02-runtime-debug-operator-helpers --strict --no-interactive`.
- [x] 3.5 After reinstalling the built plugin, live-verify expression evaluation, bounded
      run-to-breakpoint, evidence return, timeout behavior, and MCP-owned cleanup in EDT.

Evidence note (2026-04-26):
- Focused Tycho gate passed with 23 tests:
  `mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server,tests/com.ditrix.edt.mcp.server.tests -am -Dtest=RuntimeDebugControlToolTest,RuntimeDebugModelBridgeTest verify --batch-mode --no-transfer-progress`.
- Local update site was rebuilt by full `mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C`;
  repository artifacts were produced with bundle qualifier `202604261929`.
- The full suite still failed on unrelated `YaxUnitRuntimeAdapterCompatibilityTest.testBuildConfigJsonUsesLegacyMinimalShape`:
  the test expects no `showReport`, while the current checked-in adapter emits `showReport=false`.
- After reinstall, live EDT at `172.24.192.1:8766` reports installed bundle
  `1.0.0.202604261929`, and `tools/list` exposes `evaluate_debug_expression`,
  `run_to_debug_breakpoint`, and `cleanup_mcp_debug_breakpoints`.
- Live fail-closed expression smoke: `evaluate_debug_expression` with stale `frameId` returns
  `reason=stale_frame_id` and `sideEffectFreeGuaranteed=false`.
- Live timeout smoke: `run_to_debug_breakpoint` on `Configuration/ManagedApplicationModule.bsl`
  line 8 returns bounded `outcome=timeout`, reports running debug session/thread evidence, and
  removes the temporary MCP-owned breakpoint.
- Live success smoke: `run_to_debug_breakpoint` on `Configuration/ManagedApplicationModule.bsl`
  line 11 returns `outcome=suspended`, top frame `BslStackFrame`, bounded variable evidence, and
  a current `frameId`; `evaluate_debug_expression` on that frame evaluates `1 + 1` to `valueString=2`.
- Live cleanup smoke: `cleanup_mcp_debug_breakpoints` removes the MCP-owned line-11 breakpoint
  (`removedCount=1`), final `list_debug_sessions` and `list_debug_breakpoints` both return
  `count=0`, and the application is back to `UPDATED / EQUAL`.
