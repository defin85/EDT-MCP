## Context

Текущий runtime уже умеет:

- держать focused snapshot через `get_active_operation`;
- переносить detached continuation в compatibility surface;
- сохранять stable `operationId`;
- прикреплять detached continuation hint в terminal payload.

Но live evidence показал два оставшихся UX gap:

1. После получения `operationId` agent не может запросить exact snapshot этой же operation через
   public MCP surface. Внутри `McpServer` уже есть `getOperationSnapshot(String operationId)`, но
   наружу exposed только focused `get_active_operation`.
2. Busy-state guards вроде `ProjectStateChecker` возвращают текст формата
   `Project is building: DerivedDataStatus@...`, что плохо подходит для агентной навигации и не
   связывает blocked tool call с уже известной runtime operation.

Это уже не проблема самого detached bridge. Bridge даёт visibility фокуса, но не решает exact
lookup и actionable blocker diagnostics.

## Goals / Non-Goals

- Goals:
  - дать клиенту exact polling по известному `operationId`;
  - сделать blocked project/application rejections machine-readable и пригодными для retry logic;
  - сохранить additive contract без ломки `get_active_operation` и task APIs;
  - убрать raw EDT object identity strings из primary human-facing blocked messages.
- Non-Goals:
  - не добавлять полноценный `list_operations` / history dashboard в этот rollout;
  - не менять MCP Tasks lifecycle;
  - не обещать точное время, через которое busy-state закончится;
  - не invent `operationId`, если корреляция blocker -> tracked operation неоднозначна.

## Decisions

- Decision: добавить отдельный `get_operation_snapshot` tool для polling по `operationId`.
  - Alternatives considered:
    - расширить `get_active_operation` optional-параметром `operationId`
    - оставить только focused projection
  - Rationale: отдельный tool keeps focused shortcut backward-compatible, а exact lookup становится
    явным и не ломает текущий fallback semantics.

- Decision: использовать уже существующий runtime lookup `McpServer.getOperationSnapshot(...)` как
  основу public surface.
  - Alternatives considered:
    - дублировать snapshot storage в новом registry
    - вычислять snapshot заново из task registry
  - Rationale: runtime already tracks snapshots by `operationId`; proposal only exposes this truth
    outside in a controlled MCP contract.

- Decision: добавлять blocking hint в `_meta["io.ditrix.edt.mcp/blocking-operation"]`.
  - Alternatives considered:
    - класть всё только в `content.text`
    - использовать JSON-RPC error data вместо additive payload metadata
  - Rationale: additive `_meta` лучше согласуется с уже существующим detached continuation hint и
    не заставляет менять transport-level error semantics.

- Decision: нормализовать blocker reason codes и human-readable messages отдельно от EDT raw
  `toString()`.
  - Alternatives considered:
    - продолжать отдавать только существующий raw text
    - жёстко сериализовать весь `DerivedDataStatus` как текстовую строку
  - Rationale: агенту нужны стабильные reason codes для retry/orientation logic, а не Java
    implementation detail.

- Decision: прикреплять `operationId` в blocking hint только при однозначной корреляции.
  - Alternatives considered:
    - всегда выбирать focused operation даже при ambiguity
    - всегда скрывать `operationId` и отдавать только reason code
  - Rationale: ложная корреляция опаснее отсутствующей корреляции; лучше честно дать reason code без
    `operationId`, чем привязать tool failure к неверной operation.

## Risks / Trade-offs

- Busy-state correlation может оказаться неоднозначной, если несколько tracked operations касаются
  одного и того же проекта или infobase.
- Exact lookup по `operationId` увеличит зависимость клиентов от времени жизни runtime snapshot; это
  нормально, если not-found semantics остаётся явной.
- Нормализованные messages нужно держать честными: они должны объяснять blocker better, но не
  обещать точный retry timing.

## Migration Plan

1. Зафиксировать contract для `get_operation_snapshot` и blocking hint metadata.
2. Expose public tool поверх existing runtime lookup by `operationId`.
3. Добавить correlation helper для project/app busy guards и нормализованные reason codes.
4. Обновить README и agent docs.
5. Проверить live flow: detached continuation lookup и blocked retry guidance.

## Open Questions

- Должен ли `get_operation_snapshot` возвращать тот же payload shape, что и `get_active_operation`,
  или стоит сразу добавить extra field вроде `focused` / `found`?
- Нужен ли в blocking hint отдельный `scope` (`project`, `application`) или достаточно
  `reasonCode` + известных identifiers?
