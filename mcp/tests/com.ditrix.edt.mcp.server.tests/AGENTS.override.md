# Test Fragment Override

Эти инструкции действуют для Tycho/JUnit test fragment.

## Purpose

- `mcp/tests/com.ditrix.edt.mcp.server.tests/` покрывает protocol DTOs, helpers, registry logic и часть tool-level contracts без живого EDT

## Conventions

- Клади тесты в package path, который зеркалит production package
- Предпочитай focused unit/contract tests вместо broad fixture-heavy suites
- Если runtime behaviour невозможно адекватно выразить без EDT, не притворяйся unit coverage: укажи gap и сошлись на `tests/e2e/` или manual runtime verify

## Verify

```bash
mvn -f mcp/pom.xml clean verify --batch-mode --no-transfer-progress -T 1C
```
