## Context

`add-03-live-acceptance-evidence-helpers` added acceptance-evidence guardrails, but document
movement reads were moved here because they require a real read-only data access path. Existing EDT
runtime helpers cover thick-client launch, YAxUnit, metadata sync, XML/dump/update, and extension
operations. They do not by themselves prove safe arbitrary register-record reads.

## Goals / Non-Goals

- Goals:
  - discover or introduce a headless-safe read-only runtime transport for movement queries
  - query document movements by recorder across supported register kinds
  - bound execution by timeout and samples
  - report unsupported or partial outcomes without mutation
  - live-verify the installed runtime surface, not only checked-in source
- Non-Goals:
  - broad arbitrary query execution for clients
  - document write/post or rollback testing
  - replacing scenario/unit-test frameworks

## Decisions

- Decision: transport proof comes before tool success.
  - The implementation must first prove how it reads live register records without posting,
    writing, launching unsafe interactive flows, or requiring unbounded client-supplied queries.
  - Until then, the tool must return `unsupported` or remain unimplemented.

- Decision: current transport verdict is fail-closed unsupported.
  - Existing EDT-MCP runtime surfaces can resolve configuration projects, infobase applications,
    access settings, thick-client launch, YAxUnit execution, metadata synchronization, XML export,
    and extension synchronization.
  - None of those surfaces is a proven headless-safe register-record read API: thick-client/YAxUnit
    paths execute arbitrary 1C code, synchronization paths are mutation-oriented, and a generic query
    endpoint would accept unbounded client-supplied query text.
  - The first add-04 implementation therefore registers `probe_document_movements` as a bounded
    fail-closed probe. It performs target/access preflight, reports recorder/register intent and
    limitations, and returns `unsupported` with `queryExecution=not_attempted` until a narrow
    read-only runtime transport is introduced and live-proven.

- Decision: the probe is purpose-built, not a generic query endpoint.
  - Inputs identify `projectName`, `applicationId`, `recorder`, optional register filters,
    `timeoutSeconds`, and `sampleLimit`.
  - The server owns generated query text and bounds.
  - Rationale: acceptance evidence needs narrow proof, not an unrestricted extraction surface.

- Decision: live proof is mandatory for completion.
  - Unit tests can cover schema, query construction, bounds, and parsing.
  - The change is not complete until an installed plugin reads at least one real target and reports
    bounded movement evidence or a proven fail-closed runtime limitation.

## Risks / Trade-offs

- Some infobase targets may not allow read-only access without credentials or runtime setup.
- Register schemas differ by register kind and configuration; output needs normalized but lossy
  sample records.
- A generic query transport would be tempting but would expand the security and data-extraction
  surface beyond this change.

## Implementation Sketch

1. Identify the safe runtime read transport and document why it is read-only.
2. Add the `probe_document_movements` tool contract.
3. Implement register discovery/query generation for recorder-scoped reads.
4. Bound timeout, row counts, and samples.
5. Add focused tests for schema, generated query constraints, unsupported runtime paths, and sample
   shaping.
6. Update docs/resources/generated catalog.
7. Reinstall and live-verify against a real EDT/infobase target.

## Implementation Notes

- `add-03-live-acceptance-evidence-helpers` intentionally deferred document movement reads here.
  The current add-04 implementation keeps that split honest by exposing the dedicated probe while
  continuing to fail closed when the installed runtime cannot prove read-only movement access.
