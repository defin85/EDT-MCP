# Capability: Workspace And Metadata Management

## Purpose

Define the baseline workspace, metadata, diagnostics, and refactoring surface available through the EDT MCP server.

## Requirements

### Requirement: Workspace Discovery And Diagnostics

The system SHALL expose workspace discovery and diagnostic tools for EDT projects.

#### Scenario: Client inspects workspace state

- **WHEN** a client requests project listing, problem summaries, project errors, bookmarks, or task markers
- **THEN** the server returns EDT workspace information for the requested project scope

### Requirement: Metadata Inspection

The system SHALL expose metadata inspection tools for 1C configuration projects.

#### Scenario: Client inspects metadata

- **WHEN** a client requests configuration properties, metadata objects, or metadata details
- **THEN** the server returns metadata information derived from the EDT project

### Requirement: Metadata Refactoring And Organization

The system SHALL expose metadata refactoring and metadata-organization tools supported by the plugin.

#### Scenario: Client changes metadata structure

- **WHEN** a client invokes metadata refactoring or metadata tag/object grouping tools supported by the plugin
- **THEN** the server performs the requested operation or returns a preview/confirmation workflow when required
