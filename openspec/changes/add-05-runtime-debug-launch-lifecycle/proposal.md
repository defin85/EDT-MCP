# Change: Add runtime debug launch lifecycle diagnostics

## Why

Текущий debug surface умеет запускать runtime, видеть supported sessions, читать stack/variables и
делать bounded `run_to_debug_breakpoint`, но live-проверка на TP1141 показала новую слепую зону:
`debug_launch` может честно стартовать клиентский процесс, но не доказать attach EDT debugger target,
а повторный запуск того же `/DEBUG` runtime может увести оператора в модалку или второй клиент вместо
понятного MCP-статуса.

## What Changes

- Добавить `list_debug_launches` как launch-level inventory: launch config, project/application,
  EDT launch state, runtime process/PID when safely available, sanitized command line,
  `/DEBUGGERURL`, debug targets, optional `projectName`/`applicationId` filters и причины
  unsupported/filtering.
- Сделать `debug_launch` фазовым: отличать `launch_config_started`, `runtime_process_started`,
  `debug_target_attached` и `supported_thread_visible`, без сообщения "started successfully" как
  доказательства fully attached debugger session, и без бесконечного ожидания UI-thread launch
  invocation.
- Добавить fail-closed duplicate-launch guard для `debug_launch` и `run_to_debug_breakpoint`:
  при уже запущенном debug runtime для той же пары `projectName`/`applicationId` возвращать
  `debug_launch_already_running` с operator choices.
- Добавить bounded attach wait через
  `wait_debug_session(projectName, applicationId, timeoutSeconds)` и использовать тот же
  attach-state model в `debug_launch`/`run_to_debug_breakpoint`.
- Добавить `terminate_debug_launch(projectName, applicationId, launchId?)` для точечной очистки
  stale launch/debug client через Eclipse launch/process termination, без остановки EDT, raw PID
  kill и без затрагивания чужих 1C-клиентов.
- Расширить `list_debug_sessions`: если supported `count=0`, но launch/debug process существует,
  возвращать `unsupportedLaunches`/`filteredLaunches` с причинами.
- Добавить штатный smoke для runtime variables и явно задокументировать, что `debug_execute_bsl` из
  1c-mcp не триггерит EDT breakpoints.

## Impact

- Affected specs: `runtime-debug-control`
- Affected code: runtime debug bridge, launch/process diagnostics, debug tool registry, README,
  generated agent docs, E2E/manual smoke artifacts
- Dependencies: existing active debug changes `add-runtime-debug-control`,
  `add-runtime-debug-breakpoints` and `add-02-runtime-debug-operator-helpers`; this change starts
  earlier in the lifecycle and must not replace those session/breakpoint contracts
- Validation: strict OpenSpec checks, focused Tycho/JUnit coverage for launch classification and
  duplicate guardrails, plus live EDT smoke proving attach/session/variables/step/cleanup or an
  explicit runtime evidence gap
