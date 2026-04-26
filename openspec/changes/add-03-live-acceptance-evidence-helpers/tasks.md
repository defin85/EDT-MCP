## 1. Safety And Capability Contract

- [x] 1.1 Define `revalidate_objects` `dryRun`, `timeoutSeconds`, and `failFast` semantics and the
      response shape for risky or unsupported validation paths.
- [x] 1.2 Define the `describe_capabilities` schema, including versions, tool/resource counts,
      async/task support, runtime debug support, YAxUnit support, extension support, live evidence
      support, and known limitations.
- [x] 1.3 Define which live evidence probes are read-only in the first rollout and which write/post
      dry-run paths are explicitly unsupported until proven safe.

## 2. Static Diagnostics

- [x] 2.1 Implement source-tied query diagnostics for module/method scope, query text extraction,
      validation, and source-location reporting.
- [x] 2.2 Implement form event contract checks for handler existence, metadata binding, and
      signature/availability diagnostics.
- [x] 2.3 Add focused tests for dynamically built query limitations, missing handlers, unbound
      handlers, and valid event bindings.

## 3. Live Evidence And Discovery

- [x] 3.1 Move read-only document movement probes by recorder to
      `add-04-document-movement-live-evidence-probe`; keep this rollout fail-closed instead of
      shipping an unsupported-shaped stub.
- [x] 3.2 Implement read-only live probes for form command availability where EDT/runtime APIs expose
      that state safely.
- [x] 3.3 Add guarded document write/post dry-run only for paths with proven rollback semantics;
      return explicit unsupported outcomes otherwise.
- [x] 3.4 Implement `describe_capabilities` and keep resources/generated docs aligned.

## 4. Verification

- [x] 4.1 Run the smallest relevant Maven/Tycho test gates for validation, query, form, and
      capability code.
- [x] 4.2 Run `openspec validate add-03-live-acceptance-evidence-helpers --strict --no-interactive`.
- [ ] 4.3 After reinstalling the built plugin, live-verify `describe_capabilities`, safe
      revalidation preflight, query diagnostics, form event contract checks, and the live evidence
      guardrail outcomes included in this rollout.
