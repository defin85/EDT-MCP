# Capability: Agent Productivity Surface

## Purpose

Define the repository-owned artifacts that let Codex and similar agents onboard, navigate, verify, and review this repository consistently.

## Requirements

### Requirement: Root Agent Onboarding Surface

The repository SHALL provide a root agent instruction layer and curated onboarding documents for repository discovery, verification, and process guidance.

#### Scenario: New agent starts from repository root

- **WHEN** an agent opens the repository root
- **THEN** it can find `AGENTS.md`
- **AND** `AGENTS.md` points to curated onboarding and verification docs under `docs/agent/`

### Requirement: Local Overrides For High-Friction Areas

The repository SHALL provide subtree-local instruction overrides for high-friction work areas where root instructions are not specific enough.

#### Scenario: Agent works inside runtime or test subtree

- **WHEN** an agent starts from the core server subtree or test subtree
- **THEN** it can discover additional local instructions close to that work area
- **AND** those instructions refine local boundaries and verification expectations

### Requirement: Generated References And Drift Check

The repository SHALL provide a generated reference for tool discovery and a repeatable verification command that detects drift in the agent-facing surface.

#### Scenario: Agent-facing docs are refreshed or validated

- **WHEN** the generated tool reference is refreshed or the productivity surface is verified
- **THEN** a repo-owned script can regenerate the reference
- **AND** a repo-owned verification command can fail on stale generated output or missing required artifacts

### Requirement: Repo-Scoped Codex Defaults

The repository SHALL provide repo-scoped Codex configuration for durable project-specific defaults when the project is trusted.

#### Scenario: Trusted project-scoped Codex session starts

- **WHEN** Codex loads repository-scoped configuration
- **THEN** it can pick up project-specific defaults from `.codex/config.toml`
