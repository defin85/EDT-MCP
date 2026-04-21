## 1. Contract

- [x] 1.1 Зафиксировать public contract для exact EDT-MCP build inspection tool в `mcp-server-core`.
- [x] 1.2 Зафиксировать, какие version fields обязательны для runtime diagnostics и что источник
      истины — установленный OSGi bundle metadata.

## 2. Implementation

- [x] 2.1 Добавить новый discovery tool для exact build version EDT-MCP.
- [x] 2.2 Зарегистрировать tool в MCP runtime catalog без изменения поведения существующего
      `get_edt_version`.
- [x] 2.3 Вернуть machine-readable fields для exact bundle version, short plugin version, qualifier,
      bundle symbolic name, protocol version и EDT version.

## 3. Documentation

- [x] 3.1 Обновить `README.md` и generated tool catalog для нового tool.

## 4. Verification

- [x] 4.1 Добавить focused unit coverage на output contract нового tool и его presence в `tools/list`.
- [x] 4.2 Прогнать minimal verification set: fast compile, targeted Tycho/JUnit, strict OpenSpec validation.
