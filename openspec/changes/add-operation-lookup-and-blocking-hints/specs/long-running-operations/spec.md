## ADDED Requirements

### Requirement: Exact Operation Snapshot Lookup

The system SHALL allow clients to fetch the snapshot of a specific tracked long-running operation by stable `operationId`, independently from the current focused projection.

#### Scenario: Клиент опрашивает известную detached operation

- **WHEN** terminal payload, task result или blocking hint уже содержит `operationId`
- **AND** соответствующая runtime operation всё ещё отслеживается сервером
- **THEN** клиент может вызвать `get_operation_snapshot` с этим `operationId`
- **AND** сервер возвращает snapshot именно этой operation, даже если `get_active_operation`
  сейчас сфокусирован на другой operation
- **AND** snapshot сохраняет ту же logical identity через тот же `operationId`
- **AND** machine-readable hint, который уже содержит этот `operationId`, указывает
  `pollTool: "get_operation_snapshot"` как preferred exact polling surface

#### Scenario: Клиент спрашивает неизвестную или уже очищенную operation

- **WHEN** клиент вызывает `get_operation_snapshot` с неизвестным или уже очищенным `operationId`
- **THEN** сервер возвращает явный not-found outcome с `found: false`
- **AND** response остаётся normal tool result, а не transport-level JSON-RPC error
- **AND** сервер НЕ ДОЛЖЕН подменять ответ текущей focused operation

### Requirement: Actionable Busy-State Blocking Diagnostics

The system SHALL return actionable machine-readable diagnostics when a tool invocation is rejected because a project or infobase is still busy with ongoing EDT work.

#### Scenario: Busy-state blocker однозначно связан с tracked operation

- **WHEN** tool invocation отклоняется из-за project-building или infobase-sync busy state
- **AND** сервер может однозначно связать blocker с tracked runtime operation
- **THEN** tool payload содержит нормализованный human-readable blocker message
- **AND** payload `_meta["io.ditrix.edt.mcp/blocking-operation"]` содержит как минимум
  `reasonCode`, `scope`, `projectName`, `operationId` и `pollTool`
- **AND** при `scope: "application"` hint дополнительно содержит `applicationId` и SHOULD
  содержать `applicationName`, если оно уже известно серверу
- **AND** `pollTool` равен `get_operation_snapshot` и указывает на exact polling surface для этого
  blocker
- **AND** primary human-facing message НЕ ДОЛЖНО опираться на raw Java `toString()` EDT object как
  единственное объяснение причины

#### Scenario: Busy-state blocker не удаётся однозначно коррелировать

- **WHEN** tool invocation отклоняется из-за busy state
- **AND** сервер не может надёжно определить одну blocking operation
- **THEN** tool payload всё равно содержит нормализованный blocker reason code и human-readable
  explanation
- **AND** payload `_meta["io.ditrix.edt.mcp/blocking-operation"]` всё равно содержит `reasonCode`,
  `scope`, `projectName` и известные application identifiers, если blocker относится к infobase
- **AND** сервер НЕ ДОЛЖЕН придумывать `operationId`
- **AND** сервер НЕ ДОЛЖЕН указывать `pollTool`, если exact operation lookup недоступен
- **AND** response остаётся пригодным для retry/wait decision даже без exact operation lookup
