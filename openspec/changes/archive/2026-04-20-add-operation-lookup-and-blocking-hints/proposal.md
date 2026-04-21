# Change: Добавить lookup операций и blocking hints для long-running runtime

## Why

Live-проверка свежего async-default rollout показала, что progress surface уже стал рабочим, но
ориентироваться всё ещё неудобно в двух важных случаях.

Во-первых, после terminal task outcome агент уже знает `operationId`, но публично может опрашивать
только `get_active_operation`, который остаётся focused projection. Если в фокус перейдёт другая
operation или параллельно появится ещё один runtime flow, exact lookup по уже известному
`operationId` наружу не выведен, хотя внутри runtime такой lookup уже существует.

Во-вторых, blocked/busy ошибки вроде `Project is building: DerivedDataStatus@... Please wait and retry.`
остаются слишком слабыми для агентной навигации. Они не объясняют, какой именно tracked operation
блокирует вызов, можно ли её продолжать poll'ить, и когда стоит retry, а когда нужно ждать detach /
derived-data continuation.

## What Changes

- Добавить public lookup surface для конкретной tracked operation по стабильному `operationId`.
- Перенаправить machine-readable hints, которые уже несут stable `operationId`, на exact polling
  через `get_operation_snapshot`, а не обратно на focused fallback.
- Добавить machine-readable blocking hint для project/application busy состояний, когда server может
  связать отказ с известной tracked operation.
- Нормализовать human-readable blocked diagnostics, чтобы они не опирались на сырые `toString()`
  EDT-объектов как на главный user-facing текст.
- Сохранить `get_active_operation` как focused shortcut и не вводить полный multi-operation history
  dashboard в этом rollout.

## Impact

- Affected specs: `long-running-operations`
- Affected code: runtime progress surface, tool registry/implementations, project/application state
  guards, `README.md`, `docs/agent/long-running-ops.md`, unit/live verification
- Validation: strict OpenSpec validation, focused unit coverage на operation lookup и blocking hints,
  live EDT verification against detached continuation plus busy-state rejections
