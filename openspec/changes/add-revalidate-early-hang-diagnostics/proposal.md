# Change: Add early revalidate hang diagnostics

## Why

Во время live-диагностики в контуре `TP1141` (`znvuh32modeling`, объект
`Document.вд_ОтчетностьIPC`) выяснилось, что object-scoped `revalidate_objects`
может уводить весь EDT в hang очень рано. Для кейса `requestId=84`,
`operationId=306a53a4-4e23-4d72-82e8-8996965ddd50` лог обрывается сразу после
`[diag] ... :: start`: нет ни `refreshLocal completed`, ни `object lookup completed`,
ни `scheduleValidation returned`, ни `watchdog fired`.

Это уже сужает проблему до раннего workspace/resource path и делает менее
вероятной позднюю поломку на build jobs или derived data. Но текущий лог всё
ещё не доказывает root cause: по нему нельзя честно утверждать, что это именно
deadlock, lock wait, infinite wait или прямое следствие внешних file changes в
обход EDT.

Нужен отдельный change, который усилит раннюю диагностируемость
`revalidate_objects`, не обещая пока фикса неизвестного EDT bug-а.

## What Changes

- Добавить ранние диагностические checkpoints для object-scoped
  `revalidate_objects` до и вокруг refresh-stage.
- Расширить watchdog coverage на ранний refresh path, чтобы hang до
  `refreshLocal completed` оставлял thread-dump evidence, а не только одну
  строку `:: start`.
- Зафиксировать единый correlation label для этой диагностики: `requestId`,
  `operationId`, `projectName`, object summary и thread.
- Обновить verification/runbook для расследования таких hang-ов по
  `.metadata/.log`, без преувеличения уровня доказанности root cause.
- Явно оставить вне scope детерминированный runtime fix для самого EDT hang-а,
  пока у нас нет достаточного evidence.

## Impact

- Affected specs: `long-running-operations`
- Affected code: `RevalidateObjectsTool`, `DiagnosticThreadDumpWatchdog`,
  диагностические helper-утилиты, agent-facing verification docs
- Validation: strict OpenSpec validation, targeted compile/test coverage where
  practical, live reproduce on `TP1141` или честно зафиксированный runtime
  evidence gap, если проблема временно не воспроизводится
