## ADDED Requirements

### Requirement: YAxUnit-backed Unit Test Execution

The system SHALL provide a supported MCP surface for running unit tests through `YAxUnit`
against a configuration project and the selected runtime application target.

#### Scenario: Клиент запускает поддержанный unit-test run

- **WHEN** клиент вызывает `run_unit_tests` с корректными `projectName` и `applicationId`
- **AND** указанный target находится в поддержанном configuration-project scope
- **THEN** сервер запускает task-backed execution вместо ожидания финального sync payload
- **AND** финальный task result содержит как минимум `runId`, `provider`, summary counts и
  machine-readable status
- **AND** progress/status path использует тот же long-running contract, что и другие async-first
  runtime tools

### Requirement: Explicit Provider And Scope Boundaries

The system SHALL fail closed and limit the first rollout to the `YAxUnit` provider and
configuration-project targets only.

#### Scenario: Клиент запрашивает неподдержанный provider или scope

- **WHEN** клиент запрашивает неизвестный provider, BDD/scenario flow, extension-project target
  или другой неподдержанный режим первого rollout-а
- **THEN** сервер возвращает normal tool result с `success=false` и actionable explanation
- **AND** сервер НЕ ДОЛЖЕН молча переключаться на другой тестовый framework
- **AND** сервер НЕ ДОЛЖЕН запускать runtime operation в частично поддержанном режиме

### Requirement: Fail-Closed Runtime Preflight

The system SHALL validate runtime prerequisites before launching a unit-test run.

#### Scenario: Runtime prerequisites не готовы

- **WHEN** у выбранного target отсутствует пригодный runtime bridge, access settings, YAxUnit
  prerequisites или безопасное состояние для запуска
- **THEN** сервер возвращает явный preflight failure с actionable explanation
- **AND** сервер НЕ ДОЛЖЕН стартовать тестовый runtime "на удачу"

### Requirement: Retained Test Run Report Retrieval

The system SHALL retain final summary/report metadata for a completed unit-test run and let the
client retrieve it by stable `runId`.

#### Scenario: Клиент читает сохранённый отчёт по известному runId

- **WHEN** клиент вызывает `get_test_run_report` с известным `runId` из завершённого test run
- **THEN** сервер возвращает summary и поддержанный report payload или report-manifest metadata
- **AND** response остаётся read-only outcome и НЕ запускает новый test run

#### Scenario: Клиент спрашивает неизвестный или уже истёкший runId

- **WHEN** клиент вызывает `get_test_run_report` с неизвестным или уже очищенным `runId`
- **THEN** сервер возвращает явный not-found outcome с `found=false`
- **AND** ответ остаётся normal tool result, а не transport-level JSON-RPC error
