## Context

Current long-running support tracks progress only while the originating MCP task/call is alive. That
works for normal sync execution and task-backed execution, but it breaks down when EDT keeps doing
work after the MCP layer is already terminal.

Observed live evidence on 2026-04-19:

- task-backed `clean_project` on `DORolf` could be cancelled at MCP level after EDT rebuild had
  already started;
- after cancellation, `tasks/get` and `tasks/result` were terminal as expected, but project tools
  still reported `Project is building ... Please wait and retry.`;
- `get_active_operation` no longer showed that continuing EDT work, so the agent lost visibility
  into real runtime status.

The public EDT API surface is stronger than the current implementation uses:

- `IDerivedDataManager` exposes `addStatusListener(...)`, `getDerivedDataStatus()`, `isIdle()`,
  `isAllComputed()`, and `waitAllComputations(...)`;
- `DerivedDataStatus` exposes `getActiveStage()`, `getPipelineStatus()`,
  `getActivePipelineSegments()`, `getReadySegments()`, and `isModelSyncActive()`;
- `IInfobaseSynchronizationManager` exposes `addInfobaseSynchronizationListener(...)` plus
  `getSynchronizationState(...)` and `getEqualityState(...)`.

There is also an EDT internal project-pipeline event surface (`ProjectOrchestrator`,
`IProjectOrchestratorListener`), but it lives in an internal package and should be treated as a
fallback or optional enrichment source, not the primary contract foundation.

## Goals / Non-Goals

- Goals:
  - preserve visibility of supported EDT work after the MCP task/call detached from it
  - let agents poll current detached state through existing compatibility surfaces
  - keep task terminal semantics truthful while still exposing underlying EDT continuation
  - avoid fake exact progress values when EDT does not expose a trustworthy total
- Non-Goals:
  - reopen a `cancelled` or `completed` task as running again
  - guarantee exact percentage for rebuild/update flows that only expose state transitions
  - add a brand-new MCP polling API when `get_active_operation` can be extended safely
  - depend primarily on EDT internal pipeline APIs in the first rollout

## Decisions

- Decision: represent detached EDT continuation as a runtime snapshot independent from the original
  task/call lifetime, but compatible with existing `OperationProgressState` consumers.
  - Alternatives considered:
    - keep using only the original active-operation reporter
    - store continuation inside task terminal state
  - Rationale: the underlying EDT work can outlive the tool/task lifetime, so the bridge must not be
    destroyed together with the original execution context.

- Decision: use public EDT listener/state APIs as the primary signal source.
  - Alternatives considered:
    - depend on internal `ProjectOrchestrator` events for all continuation tracking
    - poll only coarse readiness checks like `ProjectStateChecker`
  - Rationale: public listeners give better compatibility guarantees and richer state than pure
    readiness polling, while internal pipeline APIs increase maintenance risk.

- Decision: keep `get_active_operation` and EDT status bar as the first-class detached surfaces.
  - Alternatives considered:
    - continue emitting the original `_meta.progressToken` after the task/call became terminal
    - add a new `get_detached_operation` MCP tool
  - Rationale: `get_active_operation` already exists as the compatibility bridge; extending it is
    lower-risk than inventing another polling surface or keeping terminal progress tokens alive.

- Decision: require an explicit detached marker in the compatibility snapshot instead of expecting
  clients to infer detached continuation indirectly.
  - Alternatives considered:
    - rely on the absence of an active tool call plus a non-idle snapshot
    - document detached behavior without changing the snapshot shape
  - Rationale: the change requirement already says detached continuation must be distinguishable from
    an actively running MCP call, so the contract should expose that distinction directly.

- Decision: add an explicit continuation hint in terminal tool/task payload metadata whenever the
  server knows that underlying EDT work may continue in detached mode.
  - Alternatives considered:
    - document that agents should always poll after cancellation
    - encode the hint only in human-readable `content.text`
  - Rationale: agents need a machine-readable signal that terminal MCP state does not imply EDT idle
    state, otherwise detached continuation remains discoverable only by convention.

- Decision: preserve the original `operationId` across the handoff from tracked MCP execution to the
  detached snapshot when the underlying EDT work is the same logical operation.
  - Alternatives considered:
    - allocate a fresh detached operation id
    - expose detached work only as uncorrelated focused status
  - Rationale: preserving the same operation identity avoids UI flicker, keeps event history intact,
    and lets polling clients continue following the same object.

- Decision: report stage/state/event history honestly and omit exact percent when the EDT source does
  not expose a trustworthy total.
  - Alternatives considered:
    - synthesize percentages from elapsed time or guessed phase weights
    - force every detached source into `progress/total`
  - Rationale: rebuild and infobase synchronization APIs expose meaningful state changes, but not a
    reliable linear total; invented percentages would regress trust in the progress surface.

## Risks / Trade-offs

- `IDerivedDataStatusListener` reports DD pipeline status, not a user-facing percent, so clients must
  tolerate indeterminate detached progress.
- `IInfobaseSynchronizationListener` gives state transitions, but not every internal sub-phase of the
  synchronization pipeline.
- If public EDT signals prove insufficient for one edge case, using internal project-orchestrator
  APIs would increase EDT compatibility risk.
- Detached snapshots widen the time window in which `get_active_operation` is non-idle, so the UI
  and docs must clearly distinguish detached continuation from an actively executing MCP request.

## Migration Plan

1. Define detached snapshot semantics under `long-running-operations`.
2. Add a runtime tracker for detached EDT work plus source adapters for rebuild and infobase update.
3. Extend `get_active_operation` and status bar rendering to surface detached snapshots, including
   an explicit detached indicator and stable operation identity.
4. Add an explicit continuation hint in terminal results/tasks when detached EDT work is expected.
5. Verify the flow live on a real EDT workspace with long rebuild and database update operations.

## Open Questions

- Should `get_active_operation` remain a global focused projection for detached work, or should the
  first rollout add optional session scoping for background continuation?
- What should be the exact metadata shape for the continuation hint so it stays additive and does
  not collide with existing MCP metadata conventions?
