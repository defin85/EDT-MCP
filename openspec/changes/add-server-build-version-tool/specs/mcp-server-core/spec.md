## ADDED Requirements
### Requirement: Exact Server Build Version Inspection

The system SHALL expose a discovery tool that returns the exact installed EDT-MCP runtime build
version, not just the EDT product version or a truncated plugin version.

#### Scenario: Client inspects exact EDT-MCP build

- **WHEN** a client calls the exact build inspection tool
- **THEN** the response includes the exact OSGi bundle version string of the installed
  `edt-mcp` runtime
- **AND** the response includes the bundle symbolic name and qualifier when available
- **AND** the response includes the short plugin version already used by the server surface
- **AND** the response includes the current EDT version for correlation

#### Scenario: Runtime bundle metadata is unavailable

- **WHEN** the runtime cannot resolve the current `edt-mcp` bundle metadata
- **THEN** the tool still returns a structured response
- **AND** the exact-version fields explicitly fall back to `unknown` rather than pretending a
  precise build was found
