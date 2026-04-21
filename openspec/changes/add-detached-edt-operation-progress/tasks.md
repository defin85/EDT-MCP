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

- [x] 4.1 Добавить focused unit coverage на mapping EDT listener/state changes в detached snapshot.
- [x] 4.2 Проверить live rebuild continuation: отмена или detach после старта `clean_project` или
      full-project `revalidate_objects`, затем подтверждение, что `get_active_operation` и status
      bar продолжают показывать EDT state до реального `ready`.
- [ ] 4.3 Проверить live infobase update continuation: detach во время `update_database`, затем
      подтверждение state bridge по synchronization/equality transitions.
      Текущий статус на 2026-04-21: bridge уже live-подтверждён, но строгий happy-path acceptance
      ещё не закрыт.
      На `http://172.24.192.1:8766/mcp` после rebuild/reinstall новый runtime уже подтвердил
      detached continuation для `update_database`:
      `taskId=615d4bef-521a-4735-8f4a-27fc82843da3` после `tasks/cancel` вернул
      `_meta["io.ditrix.edt.mcp/detached-continuation"]`, а `get_active_operation` и
      `get_operation_snapshot` показали `detached=true` и `stage="infobase_sync_pending"`.
      На том же контуре отдельный обычный happy-path без detach дошёл до финального `EQUAL`:
      `taskId=eda59134-88f8-4f7f-a2d7-dd64c5b6c733`,
      `stateAfter=UPDATED`, `syncStateAfter=SYNCHRONIZED`, `equalityStateAfter=EQUAL`.
      Но единый live run, где `update_database` сначала detached, а затем тот же detached flow
      доходит до финального `EQUAL`, пока не зафиксирован.
      На большом `TP1141` контуре `http://172.24.192.1:8765/mcp`
      (`bundleVersion=1.0.0.202604211214`) detached handoff тоже подтвердился и для `fullUpdate`,
      и для incremental update:
      `taskId=b8c97196-3a6d-49ad-a36e-649a6a130709`,
      `taskId=c33898ea-580d-4afc-a3e1-5e439f320569`.
      Оба прогона были отменены слишком рано: detached snapshot появился, но infobase осталась в
      `UNSYNCHRONIZED/NOT_EQUAL/INCREMENTAL_UPDATE_REQUIRED`.
      Дополнительно baseline incremental run без раннего cancel
      (`taskId=eeb41ab4-8005-4a03-b48f-b132eae8c969`) застрял в infobase load на `progress=193/1000`
      с `lastUpdatedAt=2026-04-21T13:12:56.256609900Z`; после отмены detached snapshot зафиксировал
      `handoffStage="failure"` и
      `handoffMessage="Database synchronization failed: Ошибка исполнения 1С:Предприятия"`.
      Пункт остаётся открытым до следующего live окна: нужно сначала восстановить contour до
      `UPDATED/EQUAL`, затем измерить обычный happy-path на большом проекте и повторить late-cancel
      заметно позже внутри infobase-load фазы. Пользователь отдельно отметил, что даже минимальное
      incremental update на этом контуре занимает 5-7 минут.
- [x] 4.4 Обновить `README.md`, `docs/agent/long-running-ops.md` и verification notes с честными
      ограничениями detached progress semantics, explicit continuation hint и stable operation id
      contract.
