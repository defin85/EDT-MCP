## 1. Safety And Capability Contract

- [ ] 1.1 Define `revalidate_objects` `dryRun`, `timeoutSeconds`, and `failFast` semantics and the
      response shape for risky or unsupported validation paths.
- [ ] 1.2 Define the `describe_capabilities` schema, including versions, tool/resource counts,
      async/task support, runtime debug support, YAxUnit support, extension support, live evidence
      support, and known limitations.
- [ ] 1.3 Define which live evidence probes are read-only in the first rollout and which write/post
      dry-run paths are explicitly unsupported until proven safe.

## 2. Static Diagnostics

- [ ] 2.1 Implement source-tied query diagnostics for module/method scope, query text extraction,
      validation, and source-location reporting.
- [ ] 2.2 Implement form event contract checks for handler existence, metadata binding, and
      signature/availability diagnostics.
- [ ] 2.3 Add focused tests for dynamically built query limitations, missing handlers, unbound
      handlers, and valid event bindings.

## 3. Live Evidence And Discovery

- [ ] 3.1 Implement read-only live probes for document movements by recorder.
- [ ] 3.2 Implement read-only live probes for form command availability where EDT/runtime APIs expose
      that state safely.
- [ ] 3.3 Add guarded document write/post dry-run only for paths with proven rollback semantics;
      return explicit unsupported outcomes otherwise.
- [ ] 3.4 Implement `describe_capabilities` and keep resources/generated docs aligned.

## 4. Verification

- [ ] 4.1 Run the smallest relevant Maven/Tycho test gates for validation, query, form, and
      capability code.
- [ ] 4.2 Run `openspec validate add-03-live-acceptance-evidence-helpers --strict --no-interactive`.
- [ ] 4.3 After reinstalling the built plugin, live-verify `describe_capabilities`, safe
      revalidation preflight, query diagnostics, form event contract checks, and at least one
      read-only live infobase evidence probe.
