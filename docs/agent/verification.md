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
