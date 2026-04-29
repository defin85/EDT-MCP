## Context

`add-runtime-debug-control`, `add-runtime-debug-breakpoints` и
`add-02-runtime-debug-operator-helpers` уже дали рабочий слой для supported EDT runtime sessions:
`list_debug_sessions`, stack/variables, execution control, breakpoints, expression evaluation и
bounded `run_to_debug_breakpoint`. Этот слой начинается после того, как Eclipse `DebugPlugin` видит
поддерживаемый debug target/thread.

Новая проблема находится раньше: между запуском launch config и появлением supported
`RuntimeDebugTargetThread`. Сейчас `debug_launch` возвращает success после вызова EDT launch API, а
оператору приходится вручную понимать, появился ли процесс `1cv8c.exe`, есть ли `/DEBUGGERURL`,
подключился ли debugger target, не висит ли старая сессия и почему `list_debug_sessions` пуст.

## Goals / Non-Goals

- Goals:
  - дать launch-level inventory поверх Eclipse `ILaunchManager`/`DebugPlugin`
  - различать launch request, runtime process, debugger attach и supported thread visibility
  - предотвратить двойной запуск `/DEBUG` runtime для одной пары `projectName`/`applicationId`
  - дать bounded wait до supported session без обязательной установки breakpoint
  - дать точечную cleanup-команду для stale launch/debug client
  - сделать причины filtering видимыми в `list_debug_sessions`
  - закрепить воспроизводимый variables smoke и границу с 1c-mcp `debug_execute_bsl`
- Non-Goals:
  - управлять произвольными Eclipse launches вне 1C:EDT RuntimeClient
  - убивать EDT process или все процессы `1cv8c.exe`
  - убивать runtime process через raw OS PID/`ProcessHandle`, минуя Eclipse launch/process model
  - обещать attach/variables без live EDT runtime evidence
  - превращать `debug_launch` в task-backed long-running operation в этом change
  - использовать 1C internal debug classes как compile-time dependency вместо standard Eclipse
    interfaces

## Decisions

- Decision: добавить отдельный `list_debug_launches`, а `list_debug_sessions` оставить
  supported-session focused.
  - Rationale: supported sessions и launch/process diagnostics имеют разные модели. Смешивание
    сделало бы session API шумным, но при `count=0` `list_debug_sessions` всё равно должен
    возвращать краткие `unsupportedLaunches`/`filteredLaunches`, чтобы оператор не попадал в
    пустоту.
  - `list_debug_launches` is also the source of `launchId` values for later targeted cleanup.
    `launchId` values are valid only for the current EDT workspace/runtime snapshot; clients must
    refresh them after EDT restart or workspace reload.

- Decision: считать `debug_launch` успешным только на уровне честно достигнутых фаз.
  - Response сохраняет `success=true` для backward-compatible accepted launch, но возвращает
    `phases` и не формулирует launch request как fully attached debug session.
  - `supported_thread_visible` может быть `pending`, `false`, `timeout` или `true`; только `true`
    является доказательством готовности `get_debug_stack`/`get_debug_variables`.
  - The launch invocation itself must be bounded. The implementation must avoid an unbounded
    request-thread `Display.syncExec`; if the EDT UI callback cannot enter or finish inside the
    bounded call window, return `debug_launch_ui_blocked` with the last known launch state and a
    clear statement of whether the callback was cancelled before launch or already in-flight.

- Decision: duplicate guard обязан быть preflight-частью `debug_launch` и
  `run_to_debug_breakpoint`.
  - Если уже есть matching RuntimeClient launch/process для той же пары
    `projectName`/`applicationId`, launch config type and debug mode, новый launch не стартует
    второй клиент.
  - Fail-closed reason: `debug_launch_already_running`.
  - Operator choices: reuse `threadId` from `list_debug_sessions`, call
    `wait_debug_session(projectName, applicationId, timeoutSeconds)`, call
    `terminate_debug_launch(projectName, applicationId, launchId?)` for the exact launch, or
    attach/cleanup manually in EDT.

- Decision: `terminate_debug_launch` работает только по доказанному target match.
  - Input должен включать `projectName` and `applicationId`; если найдено несколько matching
    launches, tool returns `multiple_matching_launches` and requires a `launchId` from
    `list_debug_launches`.
  - Tool may terminate the Eclipse `ILaunch` and associated `IProcess` objects only through
    Eclipse `ITerminate` capabilities and only when they map to the selected RuntimeClient launch.
    It SHALL NOT terminate EDT, unrelated 1C clients, or raw OS PIDs outside the Eclipse
    launch/process model.

- Decision: process command line is useful but must be sanitized.
  - `list_debug_launches` should expose executable, PID, argument list or command line when
    available, `/DEBUGGERURL` value/presence, and redacted sensitive tokens for credentials-like
    arguments.
  - Redaction must run before response serialization and before any MCP-owned logging of command
    line details. Raw command lines must not be retained in task/progress/log payloads.
  - If OS/JVM APIs cannot read process details, the response must say `process_details_unavailable`
    instead of guessing.

- Decision: EDT modal handling should be represented as launch state, not as a hanging tool call.
  - Preflight duplicate detection is the primary mitigation.
  - If EDT still reports an already-started session or UI launch does not complete within the
    bounded call window, return `old_debug_session_detected` or `debug_launch_ui_blocked` with
    actionable operator choices.

## Public Response Schema

`list_debug_launches` returns a launch snapshot, not a supported-session snapshot:

```json
{
  "success": true,
  "snapshotId": "launch-snapshot-42",
  "projectName": "znvuh32modeling",
  "applicationId": "615ab016-9ccd-423e-a7ac-40937d6d3ba5",
  "count": 1,
  "launches": [],
  "unsupportedLaunches": [],
  "filteredLaunches": []
}
```

Each item in `launches` is a sanitized RuntimeClient debug launch snapshot:

```json
{
  "launchId": "launch-snapshot-42:0",
  "launchMode": "debug",
  "supported": true,
  "launchConfiguration": {
    "name": "znv_uh32.tp1141",
    "typeId": "com._1c.g5.v8.dt.launching.core.RuntimeClient",
    "projectName": "znvuh32modeling",
    "applicationId": "615ab016-9ccd-423e-a7ac-40937d6d3ba5"
  },
  "lifecycle": {
    "launch_config_started": "true",
    "runtime_process_started": "true",
    "debug_target_attached": "pending",
    "supported_thread_visible": "false"
  },
  "processes": [],
  "debugTargets": [],
  "threads": [],
  "unsupportedReasons": []
}
```

Lifecycle phase values are string enums: `true`, `false`, `pending`, `timeout`, `unknown`. `pending`
means a previous phase exists but a later phase is not yet visible. `unknown` means Eclipse/EDT did
not expose enough information to classify the phase. `supported_thread_visible=true` is the only
phase proof that `get_debug_stack`/`get_debug_variables` can proceed.

Process snapshots expose only safe Eclipse process metadata:

- `label`, `className`, `terminated`
- optional `pid` with `pidAvailable`
- optional `executable`, `arguments`, `sanitizedCommandLine`
- `debuggerUrl` object with `present`, optional sanitized `value`, and `source`
- `redacted=true` and `redactionReasons` when any command-line token was masked
- `process_details_unavailable` in `reasons` when Eclipse/JVM APIs do not expose PID or command line

`unsupportedLaunches` uses the same launch/process summary but includes `unsupportedReasons` such as
`not_debug_mode`, `unsupported_launch_configuration_type`, `project_application_unavailable`,
`debug_target_unavailable`, or `supported_thread_unavailable`. `filteredLaunches` is a compact
summary with `filterReasons`, for example `projectName_mismatch` or `applicationId_mismatch`.

`debug_launch` remains a synchronous MCP tool response, but its success is phase-specific:

```json
{
  "success": true,
  "accepted": true,
  "phase": "launch_config_started",
  "phases": {},
  "launch": {},
  "operatorChoices": []
}
```

If duplicate preflight blocks a launch, the response is fail-closed:

```json
{
  "success": false,
  "accepted": false,
  "reason": "debug_launch_already_running",
  "matchingLaunches": [],
  "operatorChoices": [
    "list_debug_sessions",
    "wait_debug_session",
    "terminate_debug_launch",
    "manual_cleanup_in_edt"
  ]
}
```

`wait_debug_session(projectName, applicationId, timeoutSeconds)` returns either a supported session
snapshot compatible with `list_debug_sessions` or `success=false` with the latest launch snapshot and
reason `debug_session_wait_timeout`. `terminate_debug_launch(projectName, applicationId, launchId?)`
returns `terminated=true` only for launches terminated through Eclipse `ITerminate`; ambiguity returns
`multiple_matching_launches` and the matching `launchId` values.

## EDT Launch/Process API Discovery

The implementation is limited to standard Eclipse debug APIs available in EDT plugin runtime:

- `DebugPlugin.getDefault().getLaunchManager().getLaunches()` for inventory
- `ILaunch`, `ILaunchConfiguration`, `ILaunchConfigurationType` for launch identity and mode
- `ILaunch.getProcesses()`, `IProcess`, `IProcess.getAttribute(String)` for best-effort process
  labels, command-line attributes and PID-like attributes
- `ILaunch.getDebugTargets()`, `IDebugTarget`, `IThread`, `IStackFrame` for attach/thread visibility
- `ITerminate` on `ILaunch`, `IProcess` and debug targets for cleanup

The supported 1C:EDT launch identity is the RuntimeClient launch configuration type
`com._1c.g5.v8.dt.launching.core.RuntimeClient` plus attributes
`com._1c.g5.v8.dt.debug.core.ATTR_PROJECT_NAME` and
`com._1c.g5.v8.dt.debug.core.ATTR_APPLICATION_ID`. These attributes are not globally unique without
each other; matching must keep both.

PID and command-line exposure is explicitly best-effort. On Windows-hosted EDT with WSL Codex, the
runtime process is owned by Windows and may be visible to the Eclipse process model while not being
inspectable from WSL `/proc`. The implementation must not call raw OS process APIs for termination and
must not infer `/DEBUGGERURL` when the Eclipse process attributes do not expose it.

## Duplicate Matching And LaunchId Rules

A launch is a duplicate candidate only when all of these facts match:

- launch mode includes `debug`
- launch configuration type is RuntimeClient
- launch configuration attributes match both `projectName` and `applicationId`
- the Eclipse launch is not terminated, or it owns at least one non-terminated process/debug target

`applicationId` alone is not a valid duplicate key because multiple EDT projects can reference
different applications with colliding or stale identifiers. `projectName` alone is also insufficient
because a project can have multiple runtime applications.

`launchId` is an opaque handle derived from the current launch snapshot and index. It is valid only
for the current EDT workspace/runtime process and only until the next snapshot invalidates the bridge
cache. Clients must refresh via `list_debug_launches` after EDT restart, workspace reload, MCP server
restart, or `stale_launch_id`.

## Risks / Trade-offs

- Eclipse launch/process APIs may not expose PID or command line uniformly across EDT versions and
  platforms.
- `/DEBUGGERURL` may be visible only through sanitized command line or runtime process metadata.
- Some stale launches may have no process but still remain in `DebugPlugin`; cleanup must classify
  those separately.
- Application identifiers may collide across configuration projects; lifecycle matching must keep
  `projectName` in the identity instead of treating `applicationId` as globally unique.
- Once a UI launch callback has entered EDT code, a timeout cannot prove that no runtime process will
  appear later. Responses must label that state as in-flight/unknown and point to
  `list_debug_launches` instead of claiming no side effect.
- Duplicate detection can block a legitimate second client for the same infobase. This is accepted
  for MCP automation because the safer default is to avoid unbounded duplicate debug clients.
- Live verification depends on an installed bundle and a real EDT workspace; source-only tests can
  cover classification, but not the debugger handshake itself.

## Verification Strategy

1. Unit/contract tests for launch classification, filtering reasons, duplicate guard decisions,
   command-line redaction, bounded UI invocation outcomes and targeted termination ambiguity
   handling using fakes around launch/process snapshots.
2. Focused Maven/Tycho gate for touched runtime debug modules.
3. Live EDT smoke:
   - ensure no stale launch for the project/application
   - set a breakpoint at a guaranteed executable BSL point
   - call `debug_launch` or `run_to_debug_breakpoint`
   - call `wait_debug_session`
   - obtain `threadId`, `frameId`, variables, `step_over`, cleanup and terminate
4. If live EDT/TP1141 cannot expose a supported thread, record the exact launch/process/handshake
   diagnostics as the evidence gap instead of calling variables smoke complete.
