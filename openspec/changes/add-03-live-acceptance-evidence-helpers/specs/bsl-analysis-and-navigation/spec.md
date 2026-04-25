## ADDED Requirements

### Requirement: Source-Tied Query Diagnostics

The system SHALL provide a query diagnostics tool that accepts a BSL module or method scope, extracts
query texts such as `Запрос.Текст` assignments where supported, validates each extracted query in
project context, and returns diagnostics tied back to source locations.

#### Scenario: Client validates queries inside a method

- **WHEN** a client invokes the query diagnostics tool for a module and method containing supported
  query text assignments
- **THEN** the server extracts each query text it can prove from source
- **AND** it validates each query using the project query validator
- **AND** every diagnostic includes module path, method name, source line or range, extracted query
  identifier, and validation result

#### Scenario: Query text is built dynamically

- **WHEN** the tool encounters query text assembled dynamically beyond supported extraction rules
- **THEN** the response reports an extraction limitation with source location evidence
- **AND** the tool does not fabricate a complete query string
