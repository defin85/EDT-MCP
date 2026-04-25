## 1. Discovery

- [ ] 1.1 Identify the EDT/Eclipse debug API for evaluating expressions from a suspended BSL stack
      frame, including timeout and unsupported-backend behavior.
- [ ] 1.2 Confirm whether evaluation can be treated as read-only; if not, document the observable
      side-effect risk in tool metadata and docs.
- [ ] 1.3 Confirm the existing breakpoint ownership marker is sufficient for cleanup across
      list/set/remove and temporary run-to-breakpoint flows.

## 2. Debug Helper Implementation

- [ ] 2.1 Implement `evaluate_debug_expression` with frame-scoped preconditions, bounded execution,
      stale-context handling, and structured success/error payloads.
- [ ] 2.2 Implement a one-shot run-to-breakpoint helper that sets or reuses an MCP-owned temporary
      BSL breakpoint, launches or continues execution, waits for suspend or timeout, and returns
      stack/variable evidence.
- [ ] 2.3 Implement MCP-owned breakpoint cleanup with dry-run/count metadata and default protection
      for user-owned breakpoints.
- [ ] 2.4 Register the new tools with conservative descriptions, annotations, and generated catalog
      metadata.

## 3. Verification

- [ ] 3.1 Add focused Tycho/JUnit coverage for expression preconditions, stale frame rejection,
      timeout handling, and unsupported backend classification.
- [ ] 3.2 Add focused coverage for run-to-breakpoint cleanup behavior and user-breakpoint
      preservation.
- [ ] 3.3 Run the smallest relevant Maven/Tycho gate for the touched runtime-debug modules.
- [ ] 3.4 Run `openspec validate add-02-runtime-debug-operator-helpers --strict --no-interactive`.
- [ ] 3.5 After reinstalling the built plugin, live-verify expression evaluation, bounded
      run-to-breakpoint, evidence return, timeout behavior, and MCP-owned cleanup in EDT.
