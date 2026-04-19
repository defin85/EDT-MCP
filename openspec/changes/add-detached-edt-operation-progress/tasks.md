## 1. Contract

- [x] 1.1 Определить public contract для detached EDT operation snapshot: lifecycle, ownership,
      distinction between active MCP execution and detached continuation, explicit detached marker,
      and honest progress rules.
- [x] 1.2 Зафиксировать, какие operations входят в первый rollout: project rebuild / derived-data
      continuation для `clean_project` и full-project `revalidate_objects`, плюс infobase update
      continuation для `update_database`.
- [x] 1.3 Зафиксировать machine-readable continuation hint в terminal payload/task `_meta` и правило
      сохранения stable `operationId` при handoff в detached continuation.

## 2. Runtime Bridge

- [x] 2.1 Добавить detached operation tracker, живущий независимо от lifetime исходного task/call,
      но использующий существующий `OperationProgressState`-style snapshot.
- [x] 2.2 Подключить public EDT listeners/state sources:
      `IDerivedDataStatusListener` / `DerivedDataStatus` для rebuild и
      `IInfobaseSynchronizationListener` для update_database.
- [x] 2.3 Явно завершать detached snapshot, когда underlying EDT work реально заканчивается, а не
      когда исходный MCP execution context уже завершился.

## 3. Surface Integration

- [x] 3.1 Обновить `get_active_operation`, чтобы он возвращал detached snapshot, когда исходный
      tool/task уже detached, но EDT work ещё продолжается, и чтобы detached continuation была
      явно отличима в payload.
- [x] 3.2 Обеспечить stable `operationId` и непрерывную event history при handoff из tracked
      operation в detached snapshot.
- [x] 3.3 Добавить machine-readable continuation hint в terminal result/task payload для операций,
      где EDT work может продолжиться после terminal MCP outcome.
- [x] 3.4 Обновить EDT status bar / tooltip, чтобы они показывали detached continuation без
      искусственного exact percent.

## 4. Verification

- [ ] 4.1 Добавить focused unit coverage на mapping EDT listener/state changes в detached snapshot.
- [x] 4.2 Проверить live rebuild continuation: отмена или detach после старта `clean_project` или
      full-project `revalidate_objects`, затем подтверждение, что `get_active_operation` и status
      bar продолжают показывать EDT state до реального `ready`.
- [ ] 4.3 Проверить live infobase update continuation: detach во время `update_database`, затем
      подтверждение state bridge по synchronization/equality transitions.
- [x] 4.4 Обновить `README.md`, `docs/agent/long-running-ops.md` и verification notes с честными
      ограничениями detached progress semantics, explicit continuation hint и stable operation id
      contract.
