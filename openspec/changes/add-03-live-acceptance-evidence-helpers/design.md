## Context

The current MCP surface is strong at metadata/source inspection and increasingly strong at live
runtime workflows. The missing layer is acceptance evidence: tools that answer "can I prove this
specific behavior or wiring now?" without asking the agent to manually scrape source strings, form
XML, query text, and live infobase state.

The change should stay narrow. It is not a general-purpose live test framework. It is a set of
bounded probes and diagnostics that bridge common gaps between static EDT state and live behavior.

## Goals / Non-Goals

- Goals:
  - make risky or slow object revalidation explicit before execution
  - validate BSL query text with source locations from a module or method
  - detect form event handlers that exist in code but are not bound in form metadata
  - provide read-only live infobase probes for common acceptance evidence
  - expose a structured capability summary for agents
- Non-Goals:
  - replace Vanessa/scenario testing or YAxUnit
  - implement broad arbitrary SQL-like live reads
  - mutate production infobases without explicit rollback/dry-run guarantees
  - hide unsupported runtime evidence behind success-shaped responses

## Decisions

- Decision: make revalidation safety a first-class contract, not just a timeout wrapper.
  - `revalidate_objects` should support `dryRun`, `timeoutSeconds`, and `failFast` where the backend
    can enforce them.
  - The response should call out known risky objects or conditions before scheduling heavy work.
  - Rationale: previous hangs around object-scoped validation need an operator-safe preflight.

- Decision: extract query diagnostics from source using BSL/module structure, not manual string
  grep.
  - The tool should accept module/method scope, find query text assignments, validate extracted
    texts in project context, and return diagnostics tied to source ranges.
  - Rationale: plain `validate_query` is useful, but manual extraction loses location evidence.

- Decision: inspect both sides of form event contracts.
  - A handler procedure in a form module is insufficient evidence unless the corresponding form
    metadata event binding points to it.
  - Rationale: the recent `BeforeWriteAtServer` class of bug is a wiring issue, not just a missing
    method issue.

- Decision: live infobase probes are read-only by default and bounded.
  - Write/post dry-run is supported only when the platform path can guarantee rollback or otherwise
    prove side-effect boundaries.
  - Unsupported dry-run paths return explicit unsupported outcomes instead of pretending safety.
  - Rationale: live evidence is valuable only if it does not silently mutate operator data.

- Decision: `describe_capabilities` complements resources and generated docs.
  - Resources remain longer workflow guidance.
  - `describe_capabilities` returns current runtime capability facts and limitations in a compact
    structured tool result.
  - Rationale: agents need a fail-closed way to choose what verification paths are available in the
    installed runtime.

## Risks / Trade-offs

- Query extraction can miss dynamically built query text; the tool must report extraction limits.
- Form metadata parsing must handle generated or localized form structures without brittle string
  matching.
- Live probes need careful scoping to avoid becoming an unrestricted data extraction surface.
- Dry-run write/post behavior may be impossible for some runtime paths; unsupported is better than
  unsafe.
- Capability discovery can drift if it is hand-maintained; it should be derived from registrations
  and runtime feature flags where possible.

## Implementation Sketch

1. Add `revalidate_objects` safety parameters and risk/preflight DTOs.
2. Add source query extraction and validation diagnostics tied to module/method locations.
3. Add form event contract inspection over form metadata plus form module structure.
4. Add a small live evidence probe registry with read-only probes first.
5. Add `describe_capabilities` over tool/resource registrations and runtime feature flags.
6. Update resources, README, and generated agent docs.
7. Verify locally and then live against EDT/infobase targets for runtime-dependent probes.
