## Context

Базовый `run_unit_tests` должен сначала закрыть корректный cold-launch flow. Но для агентного
итеративного цикла этого недостаточно: после первой успешной загрузки runtime следующий rerun
всё ещё платит startup cost `1С:Предприятия`.

Идея "поднять режим предприятия один раз и потом гонять тесты" жизнеспособна, но только как
managed optimization:

- нужна живая session identity, а не "какой-то процесс предприятия";
- нужен control channel к уже поднятой test session;
- нужен fail-closed критерий, когда session ещё можно reuse, а когда она уже `stale`;
- нужна explicit recycle policy, а не молчаливый reuse устаревшего runtime.

Внешний prior art тоже это подтверждает: warm execution обсуждается как отдельное ускорение
(`WebSocket`/hot session), а не как отмена необходимости rebuild/update semantics при изменении
кода и runtime state.

## Goals / Non-Goals

- Goals:
  - переиспользовать одну и ту же enterprise test session для повторных unit-test runs
  - сделать reuse наблюдаемым и управляемым для MCP клиента
  - fail-close при `stale`, dead или busy session
  - отделить lifecycle warm session от lifecycle конкретного test run
  - минимизировать cold restarts там, где runtime state ещё честно пригоден
- Non-Goals:
  - гарантировать "тесты без обновления" для любых изменений workspace/runtime
  - превращать warm session в общий process pool для разных проектов/targets
  - поддерживать BDD/smoke/scenario frameworks в этом rollout-е
  - делать public contract жёстко завязанным на конкретный transport вроде WebSocket
  - скрывать от клиента факт, что session стала `stale` и требует recycle

## Decisions

- Decision: ввести отдельный session lifecycle поверх test-run lifecycle.
  - Alternatives considered:
    - держать warm session полностью неявной внутри `run_unit_tests`
    - переиспользовать только OS process without explicit session model
  - Rationale: клиенту нужен наблюдаемый и управляемый lifecycle. Иначе невозможно отличить
    ускорение от случайного использования устаревшего runtime.

- Decision: добавить explicit tools `prepare_test_session`, `get_test_session_status`,
  `recycle_test_session`.
  - Alternatives considered:
    - оставить только implicit autostart/reuse inside `run_unit_tests`
  - Rationale: пользовательский сценарий "один раз поднять, потом гонять" требует явного warm-up.
    Аналогично, stale/busy/dead state должен быть видим до следующего test run.

- Decision: reuse разрешён только в пределах одного provider/target identity.
  - Alternatives considered:
    - cross-project session sharing
    - cross-application reuse внутри одного workspace
  - Rationale: unit-test correctness слишком зависит от runtime target. Cross-target reuse
    создаёт слишком большой риск ложных быстрых прогонов.

- Decision: stale detection должна быть fail-closed и консервативной.
  - Alternatives considered:
    - оптимистично reuse session, пока нет явного crash
    - молча auto-heal без сообщения клиенту
  - Rationale: correctness важнее latency. Лучше чаще инвалидировать session, чем прогонять тесты
    по устаревшему состоянию.

- Decision: public contract должен быть transport-agnostic, даже если provider внутри использует
  persistent bridge вроде WebSocket.
  - Alternatives considered:
    - зафиксировать WebSocket как обязательный MCP-visible contract
  - Rationale: это implementation detail compatibility line и provider runtime. MCP surface должен
    описывать session semantics, а не навязывать конкретный wire mechanism.

## Session Model

- Session identity:
  - `sessionId`
  - `ownerSessionId` when the MCP request is session-scoped
  - `provider`
  - `projectName`
  - `applicationId`
  - optional provider-specific runtime correlation fields
- Reuse scope:
  - session reuse is bounded by `provider`, `projectName`, and `applicationId`
  - `ownerSessionId` is part of ownership and visibility, not cross-target reuse eligibility
- Session states:
  - `starting`
  - `ready`
  - `busy`
  - `stale`
  - `dead`
- Stable reasons:
  - `workspace_changed`
  - `runtime_target_changed`
  - `infobase_sync_performed`
  - `heartbeat_lost`
  - `explicit_recycle`
  - `provider_error`

## Reuse Policy

`run_unit_tests` в этом follow-up change должен уметь минимум такие режимы:

- `cold`
  - не использовать существующую session
- `prefer_warm`
  - reuse healthy session, иначе cold launch
- `require_warm`
  - выполнять только на healthy warm session, иначе явный fail
- `recycle_then_run`
  - сначала explicit recycle/replace, потом новый run

Public request field: `sessionMode`.

Public run outcomes:

- `cold_started`
- `reused`
- `recycled`
- `stale_rejected`

Fail-closed rule: `require_warm` MUST NOT fall back to cold launch when a matching session is
missing, `busy`, `stale`, or `dead`. It returns an actionable stale/dead/busy outcome with the
matching session identity when known.

## Provider Bridge Notes

- YAxUnit warm execution uses provider-internal RPC over WebSocket, matching the upstream YAxUnit
  external-control contract: the Enterprise client connects with `rpc.transport="ws"`, sends
  `hello`, receives `runTest`, and answers with `report`.
- `prepare_test_session` starts a persistent `RunUnitTests=<temp-config>` launch with
  `closeAfterTests=false` and provider correlation (`transport`, `rpcPort`, `pid`,
  `protocolVersion`) without exposing the RPC key in the MCP response.
- Warm rerun sends the current source text of one common module to the prepared Enterprise session.
  This mirrors the upstream hot-rerun boundary: one module/test can be rerun without restarting,
  while broad scopes stay on cold launch unless a later provider implementation proves safe reuse.
- Public MCP contract remains transport-agnostic: clients choose `sessionMode` and inspect
  lifecycle state/outcomes; WebSocket framing is provider-private.

## Tool Contracts

`prepare_test_session` inputs:

- `projectName`
- `applicationId`
- `provider` (`yaxunit` in this rollout)

`prepare_test_session` output:

- `sessionId`
- `state`
- target identity fields: `provider`, `projectName`, `applicationId`, optional `applicationName`
- optional `ownerSessionId`
- optional provider correlation fields

`get_test_session_status` inputs:

- `sessionId`

`get_test_session_status` output:

- current `state`
- `reuseScope`
- optional `staleReason`
- optional `lastHeartbeatAt`
- target identity fields

`recycle_test_session` inputs:

- `sessionId`

`recycle_test_session` output:

- `recycleOutcome`: `marked_stale`, `terminated`, or `replaced`
- old `sessionId`
- optional replacement `sessionId`
- current snapshot or stale/dead reason

## Invalidation Rules

Warm session считается `stale`, если после её подготовки произошло хотя бы одно из событий:

- успешная mutable operation на том же target, меняющая runtime state или infobase state;
- изменение workspace/source inputs, влияющих на test run, если system умеет это надёжно
  определить;
- потеря liveness/heartbeat или provider-side disconnect;
- explicit `recycle_test_session`;
- runtime/provider error, после которого дальнейший reuse уже не доказан безопасным.

## Risks / Trade-offs

- Слишком агрессивная invalidation ухудшит hit rate warm rerun, но не correctness.
- Слишком слабая invalidation приведёт к ложным быстрым тестам на устаревшем runtime.
- Provider-specific persistent bridge может иметь platform/version drift по сравнению с базовым
  cold-launch path.
- Live verification сложнее, чем у cold-launch flow: нужен не один run, а цепочка состояний
  с intentional mutation и recycle.

## Migration Plan

1. Зафиксировать public session lifecycle и reuse policy.
2. Добавить session registry, liveness tracking и invalidation hooks.
3. Реализовать explicit session-control tools.
4. Интегрировать reuse policy в `run_unit_tests`.
5. Провести live verification цепочки warm reuse -> stale -> recycle.

## Open Questions

- Какие project/application mutations можно reliably detect на current EDT compatibility line без
  ложных `stale` verdicts?
- Нужно ли в первом rollout-е persistent session всегда привязывать к единственному YAxUnit
  provider implementation, или закладывать provider-neutral registry сразу?
