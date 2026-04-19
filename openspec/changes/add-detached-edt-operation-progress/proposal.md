# Change: Add detached EDT operation progress bridge

## Why

Во время live-проверок long-running flow на `172.24.192.1:8766` с проектом `DORolf` выяснилось, что
`clean_project` и потенциально `update_database` могут продолжать работу внутри EDT уже после того,
как MCP-side task/call завершился или был прерван. В этом состоянии агент теряет видимость:
`get_active_operation` становится idle, а project/application-level операции всё ещё отвечают
`Project is building ... Please wait and retry.` или остаются в состоянии фоновой синхронизации.

Текущий rollout честно покрывает progress только пока жив исходный MCP execution context. Для EDT
rebuild и infobase synchronization этого недостаточно: именно после detach агенту нужен актуальный
статус, чтобы не гадать, зависла операция или корректно продолжается в фоне.

## What Changes

- Добавить detached progress bridge для EDT operations, которые продолжаются после завершения или
  прерывания исходного MCP task/call.
- Подтягивать rebuild / derived-data status из `IDerivedDataManager`, а infobase update state из
  `IInfobaseSynchronizationManager`, не полагаясь только на lifetime исходного Java tool call.
- Покрыть first-wave rebuild-oriented flows общим project-scoped bridge, чтобы одна и та же detached
  visibility работала как минимум для `clean_project` и full-project `revalidate_objects`.
- Добавить явный continuation hint в terminal payload/result `_meta`, чтобы agent понимал, что
  после terminal MCP outcome нужно продолжать polling detached EDT work.
- Сохранять stable `operationId` при handoff из tracked MCP execution в detached EDT continuation,
  если detached snapshot является продолжением той же runtime operation.
- Расширить compatibility surface (`get_active_operation` и EDT status bar) так, чтобы agent видел
  активную detached operation и мог отличать её от idle состояния и от обычной active MCP
  execution.
- Документировать честную семантику progress: stage/state/event history да, искусственный точный
  percent без доверенного total нет.

## Impact

- Affected specs: `long-running-operations`
- Affected code: progress/runtime bridge, `clean_project`, `update_database`, EDT status bar,
  `README.md`, runtime verification assets
- Validation: strict OpenSpec validation, focused Tycho coverage for detached bridge lifecycle and
  live EDT verification for rebuild/update continuation visibility
