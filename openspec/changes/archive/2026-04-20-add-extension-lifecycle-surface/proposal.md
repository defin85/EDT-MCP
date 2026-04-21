# Change: Добавить dedicated lifecycle surface для extension projects

## Why

Текущий rollout `add-extension-project-support` сознательно ограничил extension projects read-only
inspection paths и сохранил runtime/application flows configuration-only. После проверки public EDT
API стало ясно, что это в основном архитектурное ограничение `EDT-MCP`, а не жесткий предел
платформы: у EDT есть явные extension-aware project/runtime API для parent linkage, extension
configuration properties и infobase extension operations.

Плагину нужен отдельный, явно названный lifecycle surface для extension projects вместо
перегрузки существующих configuration-only tools. Это позволит управлять расширениями честно и
предсказуемо, не размывая старые контракты `get_applications`, `update_database` и `debug_launch`.

## What Changes

- Добавить dedicated extension lifecycle capability поверх verified EDT integration paths:
  `IExtensionProject`, `IDependentProject`, `IApplicationManager` (через parent configuration
  project) и internal EDT synchronization bridge для apply flow.
- Добавить extension-only discovery/runtime tools:
  `get_extension_properties`, `get_extension_runtime_targets`,
  `list_infobase_extensions`, `check_extension_applicability`,
  `apply_extension_to_infobase`.
- Сохранить существующие configuration-only tools для extension projects в fail-closed режиме и
  давать migration hint на новый dedicated surface.
- Встроить `apply_extension_to_infobase` в существующий progress/tasks contract как long-running
  task-backed runtime tool.
- Явно отложить вне этого change:
  extension semantic/navigation expansion,
  extension mutation/refactor flows,
  extension delete/export/import-from-infobase workflows.

## Impact

- Affected specs:
  - `extension-lifecycle-management` (new capability)
- Affected code:
  - extension/runtime context resolution
  - runtime/application integration layer
  - long-running progress/tasks integration
  - README and runtime verification surface
- Dependencies:
  - relies on the project-kind groundwork from `add-extension-project-support`
- Validation:
  - strict OpenSpec validation
  - compile + Tycho verify
  - live workspace matrix with both configuration and extension projects
