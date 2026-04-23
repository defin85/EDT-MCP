# Verification

Канонический verify contract для `EDT-MCP` строится от узкого к широкому.

## Narrow Verify

### Plugin compile

Используй, когда менялись только Java sources в bundle и нужен быстрый compile gate:

```bash
mvn -f mcp/pom.xml -pl bundles/com.ditrix.edt.mcp.server -am -DskipTests compile
```

### Unit/Tycho tests

Используй, когда менялись protocol DTOs, helpers, server internals или tool contracts:

```bash
mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C
```

Ожидаемый artefact:

- Surefire reports в `mcp/tests/com.ditrix.edt.mcp.server.tests/target/surefire-reports/`
- Local EDT update-site ZIPs в `mcp/repositories/com.ditrix.edt.mcp.server.repository/target/`, включая immutable build-specific `...-SNAPSHOT-YYYYMMDDHHMMSS.zip`
- Stable local latest-only update site в `mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site/` и `local-update-site.zip`
- Optional history composite update site в `mcp/repositories/com.ditrix.edt.mcp.server.repository/local-update-site-history/` и `local-update-site-history.zip`

## Runtime Verify

### MCP health check

Если есть живой EDT instance с установленным plugin:

```bash
curl -sf http://localhost:8765/health
```

### Python E2E

Для end-to-end проверки against a running MCP server:

```bash
python tests/e2e/run_e2e_tests.py --host localhost --port 8765 --project TestConfiguration
```

Варианты:

```bash
python tests/e2e/run_e2e_tests.py --wait 300
python tests/e2e/run_e2e_tests.py --junit-xml tests/e2e/e2e-results.xml
```

### Extension lifecycle live probe

Для current extension lifecycle rollout есть два live уровня:

- repo-owned manual probe для delivered discovery/inspection/fail-closed slice
- отдельный mutation proof для `apply_extension_to_infobase`, который пока не оформлен как CI-ready
  scripted E2E, потому что требует контролируемого drift на demo extension project

```bash
python tests/e2e/run_extension_lifecycle_probe.py \
  --host "$(ip route | awk '/default/ {print $3; exit}')" \
  --port 8766 \
  --configuration-project 'Демонстрационная_конфигурация_Управляемое_приложение' \
  --extension-project 'Демонстрационная_конфигурация_Управляемое_приложение.ВесТоваров' \
  --expected-installed 'ВесТоваров,Колонтитулы'
```

Канонический live fixture для этого probe:

- EDT workspace: `E:\Projects\DemoEDT`
- Configuration project: `Демонстрационная_конфигурация_Управляемое_приложение`
- Extension projects:
  - `Демонстрационная_конфигурация_Управляемое_приложение.ВесТоваров`
  - `Демонстрационная_конфигурация_Управляемое_приложение.Колонтитулы`
- Ожидаемое состояние target application: `SYNCHRONIZED`, `EQUAL`, `UPDATED`
- Ожидаемый результат базового probe:
  - `get_extension_runtime_targets` succeeds
  - `list_infobase_extensions` succeeds
  - `check_extension_applicability` returns `extension_runtime_headless_unsafe`
  - повторный `list_infobase_extensions` остаётся успешным

Дополнительно live verified вручную на том же fixture:

- internal EDT sync bridge, который теперь используется как backend для
  `apply_extension_to_infobase`, а не unsafe public applicability/XML path
- controlled source drift в `...ВесТоваров/src/Documents/РасходТовара/ManagerModule.bsl` приводит
  target в `INCREMENTAL_UPDATE_REQUIRED / NOT_EQUAL`
- затем sync bridge возвращает target в `UPDATED / EQUAL` без подвисания transport-а
- публичный `apply_extension_to_infobase` live verified на том же fixture после переустановки
  plugin:
  - bare `tools/call` auto-promote'ится в task-backed execution
  - `tasks/result` возвращает final tool payload с `_meta.related-task`
  - controlled drift через временный комментарий в `ManagerModule.bsl` приводит tool result к
    `updateStateBefore=INCREMENTAL_UPDATE_REQUIRED` и `equalityStateBefore=NOT_EQUAL`
  - cleanup через удаление того же комментария и повторный `apply_extension_to_infobase`
    возвращает fixture в `UPDATED / EQUAL`

Если базовый probe падает, не считай это автоматическим доказательством против
`apply_extension_to_infobase`: сначала зафиксируй, сломан ли уже delivery slice или снова всплыл
EDT runtime/UI ceiling.

### Early object revalidate hang reproduce

Используй этот runbook, когда object-scoped `revalidate_objects` подозревается в раннем hang до
`scheduleValidation returned`.

Базовый safe preflight:

```bash
HOST_IP=$(ip route | awk '/default/ {print $3; exit}')
curl -sS -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":301,"method":"tools/call","params":{"name":"get_edt_version","arguments":{}}}' \
  "http://$HOST_IP:8765/mcp"
curl -sS -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":302,"method":"tools/call","params":{"name":"get_project_errors","arguments":{"projectName":"<project>","objects":["<object-fqn>"],"limit":50}}}' \
  "http://$HOST_IP:8765/mcp"
```

Exact reproduce command:

```bash
curl -m 120 -sS -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":303,"method":"tools/call","params":{"name":"revalidate_objects","arguments":{"projectName":"<project>","objects":["<object-fqn>"]}}}' \
  "http://$HOST_IP:8765/mcp"
```

Known intermittent live example observed on April 20, 2026:

- workspace/project family: `TP1141`
- `projectName="znvuh32modeling"`
- `objects=["Document.вд_ОтчетностьIPC"]`

After the call, inspect the EDT workspace log:

```bash
rg -n '\[diag\]|watchdog fired|thread dump for|tool=revalidate_objects|requestId=303|operationId=|refreshLocal|scheduleValidation|waiting for build jobs|waiting for derived data' <workspace>/.metadata/.log
```

What to preserve in evidence:

- the last `[diag]` lines for the exact `requestId`
- whether `refreshLocal completed` appeared
- whether `scheduleValidation returned` appeared
- whether the trace reached `waiting for build jobs` or `waiting for derived data`
- whether `watchdog fired` emitted a matching `thread dump for ... stage=refresh`

Interpretation:

- healthy `get_project_errors` plus hanging `revalidate_objects` narrows the incident to the mutating EDT path, not general transport reachability
- missing `refreshLocal completed` still does not prove an EDT root cause on its own; it only narrows the failure to the early refresh path
- if the reproduce succeeds, record the exact `requestId`, `operationId`, and observed timings instead of claiming the incident is fixed; the current case is known to be intermittent
- if live EDT is unavailable, record this as a runtime evidence gap instead of closing the task on unit coverage alone

## CI Reference

- `.github/workflows/build.yml` — `mvn clean verify --batch-mode --no-transfer-progress -T 1C`
- `.github/workflows/e2e-tests.yml` — wait for `/health`, then run `python tests/e2e/run_e2e_tests.py`
- `.github/workflows/release.yml` — release build gate over the same Maven/Tycho pipeline
- `.github/workflows/agent-surface.yml` — verifies AGENTS/docs/OpenSpec/Codex productivity surface

## Verification Policy

- Меняешь docs only: проверь ссылки/пути/команды вручную и через targeted reads.
- Меняешь Java/protocol code: минимум plugin compile; prefer full Tycho verify.
- Меняешь HTTP/runtime behaviour: при возможности добавляй или запускай E2E against live server.
- Если runtime verify невозможен без EDT, явно фиксируй это как evidence gap.

## Productivity Surface Verify

Для проверки agent-facing surface:

```bash
bash scripts/verify_agent_surface.sh
```

Для быстрого onboarding/self-check:

```bash
bash scripts/codex-onboard.sh
```
