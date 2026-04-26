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
  - provide fail-closed live evidence guardrails for common acceptance checks
  - expose a structured capability summary for agents
- Non-Goals:
  - replace Vanessa/scenario testing or YAxUnit
  - implement broad arbitrary SQL-like live reads
  - read document movement records by recorder in this rollout; that is tracked by
    `add-04-document-movement-live-evidence-probe`
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

- Decision: defer document movement reads to a dedicated follow-up.
  - `add-04-document-movement-live-evidence-probe` owns the runtime transport/query path for reading
    register records by recorder.
  - This change must not satisfy that requirement with an unsupported-shaped stub.
  - Rationale: the current EDT runtime bridge exposes thick-client launch, YAxUnit, metadata sync,
    XML/dump/update, and extension operations, but not a proven headless-safe read-only query API for
    register records.

- Decision: `describe_capabilities` complements resources and generated docs.
  - Resources remain longer workflow guidance.
  - `describe_capabilities` returns current runtime capability facts and limitations in a compact
    structured tool result.
  - Rationale: agents need a fail-closed way to choose what verification paths are available in the
    installed runtime.

## Implementation Contracts

### Revalidation Safety

`revalidate_objects` keeps the existing execution paths, but every safety-oriented request is
preflighted before refresh/build/check scheduling.

Inputs:
- `dryRun`: when `true`, the tool resolves the project and requested object FQNs, builds the
  preflight payload, and returns without calling `refreshLocal`, `project.build`,
  `checkScheduler.scheduleValidation`, or `BuildUtils.waitForBuildAndDerivedData`.
- `timeoutSeconds`: bounded wait request. `1..300` is accepted as a requested bound; values outside
  that range are invalid. The tool reports `unsupported_safety_semantics` instead of scheduling when
  the selected EDT path cannot safely enforce the bound.
- `failFast`: when `true`, any `preflight.status` other than `supported` returns immediately with
  `executed=false`.

Preflight response shape:

```json
{
  "success": true,
  "project": "ProjectName",
  "mode": "preflight|full|objects",
  "dryRun": true,
  "executed": false,
  "preflight": {
    "status": "supported|risky|busy|unsupported|invalid",
    "wouldSchedule": "none|full_project_revalidation|object_validation",
    "safetySemantics": {
      "timeout": "supported|unsupported|not_requested",
      "failFast": "supported",
      "dryRun": "supported"
    },
    "risks": [
      {
        "id": "full_project_revalidation|many_objects|unsupported_timeout|project_not_ready",
        "severity": "info|warning|blocker",
        "message": "Human-readable explanation",
        "recommendation": "Actionable next step"
      }
    ],
    "objects": {
      "requested": 0,
      "found": [],
      "notFound": [],
      "normalized": []
    }
  }
}
```

Status semantics:
- `supported`: the requested safety controls can be honored and the operation may proceed.
- `risky`: the operation is available but known to be broad or slow; `failFast=true` prevents
  scheduling.
- `busy`: the project/workspace is not ready for safe scheduling.
- `unsupported`: a requested safety control cannot be guaranteed, so the tool returns without
  scheduling.
- `invalid`: project, object, or parameter validation failed.

Initial timeout support is conservative. Full-project revalidation may rely on task/cancellation
infrastructure and detached progress. Object-scoped validation is synchronous in the current code
path, so a requested hard timeout for that path is reported as unsupported until implementation can
prove an interruptible backend boundary.

### Capability Discovery

`describe_capabilities` is a runtime discovery tool, not a source-code inventory. It reports the
installed bundle and the tools/resources currently registered in that running server.

Top-level response shape:

```json
{
  "success": true,
  "server": {
    "serverName": "edt-mcp-server",
    "protocolVersion": "2025-11-25",
    "bundleSymbolicName": "com.ditrix.edt.mcp.server",
    "bundleVersion": "1.0.0.qualifier",
    "buildQualifier": "qualifier",
    "pluginVersion": "1.0.0",
    "edtVersion": "2024.2.5.16"
  },
  "tools": {
    "count": 0,
    "names": [],
    "items": [
      {
        "name": "tool_name",
        "responseType": "JSON|MARKDOWN|TEXT|IMAGE",
        "taskSupport": "forbidden|optional|required",
        "annotations": {}
      }
    ]
  },
  "resources": {
    "count": 0,
    "uris": []
  },
  "capabilities": {
    "asyncTasks": {"status": "supported|partial|unsupported|unknown", "tools": []},
    "runtimeDebug": {"status": "supported|partial|unsupported|unknown", "tools": []},
    "yaxUnit": {"status": "supported|partial|unsupported|unknown", "tools": []},
    "extensionLifecycle": {"status": "supported|partial|unsupported|unknown", "tools": []},
    "liveEvidence": {
      "status": "supported|partial|unsupported|unknown",
      "readOnlyProbes": [],
      "mutationDryRun": {"status": "unsupported|partial|supported", "reason": "string"}
    }
  },
  "limitations": [
    {"id": "installed_runtime_only", "area": "runtime", "severity": "info", "message": "string"}
  ]
}
```

Capability status values are deliberately fail-closed:
- `supported`: all required tools/resources for that area are registered.
- `partial`: at least one tool/resource is registered, but required pieces or safety guarantees are
  missing.
- `unsupported`: no installed runtime surface exists for that area.
- `unknown`: the server cannot derive the status reliably.

### Live Evidence Safety

The first rollout exposes only bounded fail-closed live evidence helpers unless rollback semantics
or read-only runtime access are proven in code and live verification.

Read-only probe contract:
- Inputs identify `projectName`, `applicationId`, a narrow target, `timeoutSeconds`, and
  target-specific bounds such as `sampleLimit` where samples are returned.
- Responses include `target`, `application`, `readOnly=true`, `status`, `timeout`, bounded counts,
  bounded samples where available, and `limitations`.
- Probe status is `supported`, `partial`, `unsupported`, `timeout`, or `error`.

First-rollout probe boundaries:
- Document movements by recorder are out of scope for this rollout and moved to
  `add-04-document-movement-live-evidence-probe`.
- Form command availability is read-only only where EDT/runtime APIs expose command state without
  opening an unsafe interactive form flow; otherwise the probe returns `unsupported`.
- Document write/post dry-run is unsupported by default. A request for write/post dry-run returns
  `status=unsupported_safe_dry_run`, `readOnly=true`, and performs no write/post unless a later
  implementation proves rollback and side-effect isolation.

## Risks / Trade-offs

- Query extraction can miss dynamically built query text; the tool must report extraction limits.
- Form metadata parsing must handle generated or localized form structures without brittle string
  matching.
- Live probes need careful scoping to avoid becoming an unrestricted data extraction surface.
- Deferring document movement reads keeps this rollout honest, but leaves live register-record
  evidence to the follow-up change.
- Dry-run write/post behavior may be impossible for some runtime paths; unsupported is better than
  unsafe.
- Capability discovery can drift if it is hand-maintained; it should be derived from registrations
  and runtime feature flags where possible.

## Implementation Sketch

1. Add `revalidate_objects` safety parameters and risk/preflight DTOs.
2. Add source query extraction and validation diagnostics tied to module/method locations.
3. Add form event contract inspection over form metadata plus form module structure.
4. Add fail-closed live evidence guardrails first.
5. Add `describe_capabilities` over tool/resource registrations and runtime feature flags.
6. Update resources, README, and generated agent docs.
7. Verify locally and then live against EDT/infobase targets for runtime-dependent probes.
