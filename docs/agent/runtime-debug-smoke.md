# Runtime Debug Variables Smoke

Этот сценарий фиксирует воспроизводимый путь для live-проверки runtime variables через EDT
debugger. Он требует установленный bundle в живом EDT workspace и реальный код, который достигает
выбранной BSL-строки из debug-launched client/server thread.

## Inputs

- `MCP_URL`, например `http://127.0.0.1:8767/mcp`
- `projectName`
- `applicationId`
- `modulePath` относительно `src`
- `lineNumber`, где выполнение гарантированно остановится

## Sequence

1. Set or reuse an MCP-owned breakpoint:

   ```bash
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"set_debug_breakpoint","arguments":{"projectName":"<projectName>","modulePath":"<modulePath>","lineNumber":<lineNumber>}}}' \
     "$MCP_URL"
   ```

2. Launch the target in debug mode:

   ```bash
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"debug_launch","arguments":{"projectName":"<projectName>","applicationId":"<applicationId>","updateBeforeLaunch":false}}}' \
     "$MCP_URL"
   ```

3. Wait for a supported session:

   ```bash
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"wait_debug_session","arguments":{"projectName":"<projectName>","applicationId":"<applicationId>","timeoutSeconds":30}}}' \
     "$MCP_URL"
   ```

4. Drive the application workflow that reaches the selected BSL line. Do not use 1c-mcp
   `debug_execute_bsl` as proof: it does not execute inside the EDT-launched debug client thread and
   does not trigger EDT breakpoints.

5. Read stack and variables from the suspended frame:

   ```bash
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_debug_stack","arguments":{"threadId":"<threadId>"}}}' \
     "$MCP_URL"
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_debug_variables","arguments":{"frameId":"<frameId>","maxVariables":100}}}' \
     "$MCP_URL"
   ```

6. Prove control and clean up:

   ```bash
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"control_debug_session","arguments":{"threadId":"<threadId>","action":"step_over"}}}' \
     "$MCP_URL"
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"cleanup_mcp_debug_breakpoints","arguments":{"projectName":"<projectName>"}}}' \
     "$MCP_URL"
   curl -sS -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"terminate_debug_launch","arguments":{"projectName":"<projectName>","applicationId":"<applicationId>"}}}' \
     "$MCP_URL"
   ```

## Blocked Phase Evidence

If the smoke cannot reach variables, record the exact failed phase instead of marking the smoke
complete:

- `debug_launch_ui_blocked`: UI launch request did not complete inside `launchTimeoutSeconds`
- `debug_launch_already_running`: reuse, wait, or terminate the matching launch before relaunching
- `debug_session_wait_timeout`: attach/thread was not visible; inspect `latestLaunchSnapshot`
- `supported_thread_visible=false`: process/debug target exists, but supported EDT thread is not
  exposed yet
- `get_debug_stack`/`get_debug_variables` stale ID errors: refresh `list_debug_sessions` and retry
