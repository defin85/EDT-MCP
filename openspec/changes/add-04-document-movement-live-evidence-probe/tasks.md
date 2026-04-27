## 1. Runtime Transport

- [x] 1.1 Identify and document a headless-safe read-only runtime transport for recorder-scoped
      register reads.
- [x] 1.2 Add fail-closed preflight for missing applications, missing runtime services, credentials,
      busy runtime bridge state, and unsupported targets.
- [x] 1.3 Prove that the selected transport does not write, post, or mutate the target infobase.

## 2. Probe Implementation

- [x] 2.1 Implement `probe_document_movements` with `projectName`, `applicationId`, `recorder`,
      optional register filters, `timeoutSeconds`, and `sampleLimit`.
- [x] 2.2 Return recorder identity, register names, row counts, bounded sample records, target
      identity, timeout status, and limitations.
- [x] 2.3 Ensure generated queries are server-owned, recorder-scoped, and bounded.

## 3. Documentation And Discovery

- [x] 3.1 Update `describe_capabilities` live-evidence status and limitations for document movement
      probes.
- [x] 3.2 Update README/resources/generated tool catalog after implementation.
- [x] 3.3 Link this follow-up back to the add-03 deferral in implementation notes.

## 4. Verification

- [x] 4.1 Run focused Maven/Tycho tests for the probe contract, bounds, and fail-closed outcomes.
- [x] 4.2 Run `openspec validate add-04-document-movement-live-evidence-probe --strict --no-interactive`.
- [ ] 4.3 Reinstall the built plugin and live-verify document movement evidence against a real
      EDT/infobase target.

## Evidence

- 2026-04-27 local implementation result: no proven headless-safe register-record read transport was
  found in the existing EDT-MCP runtime surfaces, so `probe_document_movements` is registered as a
  bounded fail-closed probe. It performs project/application/access preflight, rejects arbitrary
  client query text, starts no runtime bridge, performs no write/post/dry-run path, and returns
  `status=unsupported`, `performed=false`, and `queryExecution=not_attempted` until a narrow
  read-only transport is introduced.
- Local checks passed: focused Tycho/JUnit
  `AcceptanceEvidenceLiveProbeGuardrailsTest,DescribeCapabilitiesToolTest` (10 tests), strict
  OpenSpec validation, `scripts/verify_agent_surface.sh`, and `git diff --check`.
- Live installed-runtime verification remains open until EDT is reinstalled from the rebuilt update
  site and the installed `probe_document_movements` result is captured from a real application
  target.
