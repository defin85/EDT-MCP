## MODIFIED Requirements

### Requirement: Generated References And Drift Check

The repository SHALL provide generated references for MCP tool and resource discovery and a
repeatable verification command that detects drift in the agent-facing surface.

#### Scenario: Agent-facing docs are refreshed or validated

- **WHEN** the generated discovery references are refreshed or the productivity surface is verified
- **THEN** a repo-owned script can regenerate the tool reference, registered resource reference, and
  supported discovery metadata
- **AND** a repo-owned verification command can fail on stale generated output or missing required
  discovery artifacts

#### Scenario: New MCP discovery metadata is added

- **WHEN** a tool annotation, workflow-aware description, or registered resource is added or changed
- **THEN** the generated agent-facing reference reflects that runtime discovery surface
- **AND** stale checked-in generated output is detected before the change is reported complete
