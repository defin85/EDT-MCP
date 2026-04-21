# Change: Добавить tool для exact build version EDT-MCP

## Why

Сейчас runtime surface позволяет быстро узнать версию EDT через `get_edt_version`, но не даёт
надёжно отличить, какой именно build самого `edt-mcp` установлен в живом runtime. Для диагностики
reinstall/update drift этого недостаточно, потому что текущий short plugin version обрезает
qualifier и не различает соседние сборки одной и той же линии.

## What Changes

- Добавить отдельный discovery tool, который возвращает exact runtime build information для
  `edt-mcp`.
- Зафиксировать, что tool читает именно установленный OSGi bundle metadata runtime'а, а не только
  усечённую build-time версию.
- Вывести вместе с exact bundle version короткую plugin version, symbolic name bundle'а, qualifier,
  protocol version и EDT version для одной диагностической точки.
- Обновить README, agent-facing catalog и focused verification для нового tool.

## Impact

- Affected specs: `mcp-server-core`
- Affected code: tool registry, new tool implementation, focused tests, README, generated agent refs
- Validation: strict OpenSpec validation, focused Tycho/JUnit coverage, fast compile
