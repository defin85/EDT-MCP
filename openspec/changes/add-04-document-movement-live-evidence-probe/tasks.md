## 1. Runtime Transport

- [ ] 1.1 Identify and document a headless-safe read-only runtime transport for recorder-scoped
      register reads.
- [ ] 1.2 Add fail-closed preflight for missing applications, missing runtime services, credentials,
      busy runtime bridge state, and unsupported targets.
- [ ] 1.3 Prove that the selected transport does not write, post, or mutate the target infobase.

## 2. Probe Implementation

- [ ] 2.1 Implement `probe_document_movements` with `projectName`, `applicationId`, `recorder`,
      optional register filters, `timeoutSeconds`, and `sampleLimit`.
- [ ] 2.2 Return recorder identity, register names, row counts, bounded sample records, target
      identity, timeout status, and limitations.
- [ ] 2.3 Ensure generated queries are server-owned, recorder-scoped, and bounded.

## 3. Documentation And Discovery

- [ ] 3.1 Update `describe_capabilities` live-evidence status and limitations for document movement
      probes.
- [ ] 3.2 Update README/resources/generated tool catalog after implementation.
- [ ] 3.3 Link this follow-up back to the add-03 deferral in implementation notes.

## 4. Verification

- [ ] 4.1 Run focused Maven/Tycho tests for the probe contract, bounds, and fail-closed outcomes.
- [ ] 4.2 Run `openspec validate add-04-document-movement-live-evidence-probe --strict --no-interactive`.
- [ ] 4.3 Reinstall the built plugin and live-verify document movement evidence against a real
      EDT/infobase target.
