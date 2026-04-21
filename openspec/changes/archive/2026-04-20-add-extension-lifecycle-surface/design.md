## Context

`add-extension-project-support` ввёл project kind discovery и first-wave read-only support для
extension projects, но специально оставил runtime/application flows configuration-only до появления
отдельного lifecycle surface. После проверки official EDT API это решение остаётся корректным как
boundary, но больше не выглядит как platform limit:

- `IExtensionProject` и `IDependentProject` дают extension-specific project model и parent linkage
- `IThickClientLauncher` имеет public operations для extension-aware infobase actions
- `IModuleExtensionService` и `IFormExtensionService` подтверждают, что extension semantics в EDT
  не являются чисто internal concern

Проблема в `EDT-MCP` архитектурная: текущие runtime tools привязаны к configuration-oriented
contract (`get_applications` -> `update_database` / `debug_launch`) и потому не подходят как
естественный extension lifecycle surface.

## Goals / Non-Goals

- Goals:
  - дать extension projects отдельный runtime/discovery surface вместо перегрузки generic
    configuration tools
  - использовать verified EDT integration paths: public APIs для discovery/inspection и verified
    internal sync bridge для apply
  - сохранить fail-closed backward compatibility для старых configuration-only tools
  - встроить extension apply в существующий progress/tasks runtime
- Non-Goals:
  - не расширять в этом change semantic/navigation tools для extension projects
  - не открывать extension mutation/refactor flows
  - не добавлять destructive infobase extension operations вроде delete в первый rollout
  - не переводить `get_configuration_properties` на polymorphic contract в этом change

## Decisions

- Decision: ввести dedicated extension lifecycle tools вместо расширения `get_applications`,
  `update_database` и `debug_launch`.
  - Alternatives considered:
    - разрешить существующие runtime tools на extension projects
    - silently route extension projects через parent configuration semantics
  - Rationale:
    - старые tool names уже означают configuration-oriented flow
    - extension lifecycle имеет другие входы и failure modes
    - explicit surface проще документировать и fail-closed проверять

- Decision: parent configuration project остаётся canonical bridge к runtime targets.
  - Alternatives considered:
    - считать extension project самостоятельным owner of applications
    - требовать от клиента вручную указывать parent project в каждом вызове
  - Rationale:
    - official EDT API моделирует extension project как dependent project
    - target discovery должен быть deterministic и не перекладывать parent resolution на клиента

- Decision: разделить verified execution paths по capability.
  - Public/runtime path kept for:
    - `IExtensionProject` / `IDependentProject` for project and parent resolution
    - `IApplicationManager` on parent configuration project for available applications
    - `IThickClientLauncher` for listing extensions and XML-contract diagnostics
  - Internal/runtime path promoted to primary apply backend:
    - `IInfobaseSynchronizationManager.updateInfobase(...)` / `reloadInfobase(...)` on the
      extension project
  - Rationale:
    - public applicability/XML path did not become a safe apply backend for MCP:
      `canApplyConfigurationExtension` was not headless-safe and the XML contract probe disproved
      direct `workspace/src -> import` compatibility
    - the internal synchronization bridge has a positive live mutation proof on the demo workspace
      without transport hangs or UI prompts

- Decision: первый lifecycle rollout использует EDT synchronization semantics (`updateInfobase` /
  `reloadInfobase`) вместо прямого XML import/apply handoff.
  - Alternatives considered:
    - continue toward public XML import/apply after source staging
    - reuse `IInfobaseSynchronizationManager` as-is for extension projects
  - Rationale:
    - internal sync bridge already maps extension project source to infobase mutation semantics
      that EDT itself accepts
    - это убирает необходимость изобретать staging adapter в рамках первого public apply contract
    - `fullReload` остаётся доступным как explicit mode, но default path остаётся incremental
      synchronization

- Decision: `apply_extension_to_infobase` должен быть async-first task-backed tool.
  - Alternatives considered:
    - sync-first call like `debug_launch`
    - custom polling contract outside existing task runtime
  - Rationale:
    - extension apply потенциально долгий и конфликтный infobase mutation flow
    - существующий task/progress contract уже подходит и уменьшает protocol sprawl

- Decision: сохранить legacy configuration-only tools на extension projects и добавить migration
  hint вместо silent widening.
  - Rationale:
    - это избегает breaking reinterpretation старых tool contracts
    - клиенту проще понимать разницу между configuration runtime и extension lifecycle

## Audit Verdict

Рекомендуемый следующий change: **узкий dedicated extension lifecycle rollout**.

Причины:
- public EDT API уже даёт достаточную опору для отдельного extension management surface
- смешивать lifecycle с semantic/navigation expansion в одном rollout невыгодно: разные API,
  разные риски, разные validation gates
- переиспользование existing configuration runtime tools создаст ambiguous contract и усложнит
  backward compatibility

## Audit Matrix

| Area | Verdict | Notes |
|------|---------|-------|
| Requirement fit | Strong | directly addresses the missing “manage extensions” surface |
| API stability | Medium | public APIs exist, but exact apply path assumptions need a live spike |
| Backward compatibility | Strong | legacy tool semantics remain unchanged |
| Operability | Strong | can reuse tasks/progress/blocking diagnostics |
| Testability | Medium | needs canonical extension fixture or documented live matrix |
| Upgrade risk | Medium | acceptable if public API stays primary and internal API stays fallback-only |
| Scope control | Strong | excludes semantic tools, refactors and destructive delete/export flows |

## Execution Plan

1. Prove candidate apply paths on a live workspace:
   public applicability/XML path verdict -> internal sync bridge verdict.
2. Add shared extension runtime context and lifecycle failure helper.
3. Ship discovery/inspection tools first.
4. Add task-backed apply tool on top of the verified internal sync bridge with parent-project
   scheduling and application-scoped blocking diagnostics.
5. Update README and live verification docs/fixtures.
6. Only after this change lands, start a separate follow-up for extension semantic/navigation
   expansion.

## Risks / Trade-offs

- Public apply path may require stricter XML source layout than the extension project `src/`
  currently guarantees.
  - Mitigation:
    - keep XML-contract probing as diagnostics only
    - do not route the public apply tool through direct XML handoff in this rollout
- Parentless extension projects may exist and cannot be routed to runtime targets automatically.
  - Mitigation:
    - explicit `extension_parent_missing` failure category
- Concurrent mutation with `update_database` on the parent configuration infobase can create
  hidden races.
  - Mitigation:
    - use application-scoped or infobase-scoped scheduling/blocking diagnostics
- Clients may continue to call old tools on extension projects.
  - Mitigation:
    - preserve `configuration_only` but add migration hint to dedicated tools

## Exact Wording Fixes

- `add-extension-project-support` already says runtime/application flows stay configuration-only
  until a dedicated extension lifecycle surface is approved. This new change should preserve that
  wording and add the dedicated surface rather than weakening the old requirement in place.

## Assumptions and Open Questions

- Current proof status:
  - Documented public EDT API support is strong for target selection and extension-scoped execution:
    `IExtensionProject` gives parent linkage and root configuration, while
    `RuntimeExecutionArguments.setExtensionName()` plus `IThickClientLauncher.importConfigurationFromXml`,
    `importObjectsFromXml` and `updateInfobase` are documented as targeting a specific extension.
  - Live proof is currently narrower than the public API surface: parent resolution and
    `list_infobase_extensions` are verified, but `canApplyConfigurationExtension` is not
    headless-safe for MCP and had to be fail-closed.
  - Live XML-contract probing on the demo extensions `ВесТоваров` and `Колонтитулы` showed
    `compatibleLayout=false`: the workspace uses EDT `src/` with `.mdo` files and form/resource
    subtrees, while `exportConfigurationToXml(..., HIERARCHICAL, PLAIN_FILES)` produces `.xml`
    roots plus `/Ext/...` subtrees and extra files such as `ConfigDumpInfo.xml`.
  - Therefore the original assumption that workspace `src/` can be passed directly to
    `importConfigurationFromXml`/`importObjectsFromXml` is disproven. Any future
    `apply_extension_to_infobase` needs a dedicated export/staging adapter or another verified
    source-format bridge.
  - Live proof for the internal path is now positive: `IInfobaseSynchronizationManager`
    accepted extension-project mutation on the demo workspace, both for no-op `UPDATED` targets and
    for a controlled drift that moved the target to `INCREMENTAL_UPDATE_REQUIRED / NOT_EQUAL`,
    after which sync returned it to `UPDATED / EQUAL`.
- Assumption:
  internal EDT synchronization semantics for extension projects remain stable enough across supported
  EDT releases to serve as the first public apply backend.
- Open question:
  do we need a dedicated repo fixture for extension lifecycle E2E, or is documented live-matrix
  evidence sufficient for the first rollout?
