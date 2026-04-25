# Change: Add live acceptance evidence helpers

## Why

Several recent checks showed the same gap: clean EDT markers or a successful protocol call do not
prove live behavior. Agents still need narrow evidence helpers for source-tied query diagnostics,
form event wiring, safer validation, runtime infobase probes, and capability discovery. Without
those helpers, the acceptance loop falls back to manual extraction and ad hoc live checks.

## What Changes

- Add safer `revalidate_objects` modes for dry-run, timeout, fail-fast, and explicit risk reporting.
- Add source-tied query diagnostics that extract query text from BSL methods and return validation
  diagnostics with source locations.
- Add a form event contract check that verifies both handler existence and binding in form metadata.
- Add bounded live infobase evidence probes for read-only checks first, with write/post dry-run only
  when rollback semantics are proven.
- Add `describe_capabilities` so agents can discover versions, supported contours, known
  limitations, async support, debug support, YAxUnit support, extension support, and live-evidence
  support from one structured surface.

## Impact

- Affected specs: `long-running-operations`, `bsl-analysis-and-navigation`,
  `workspace-and-metadata-management`, `agent-productivity-surface`
- Affected code: validation tooling, BSL query extraction, form metadata inspection, live infobase
  probes, capability registry, docs/resources, focused tests
- Validation: strict OpenSpec checks, local unit coverage for extraction and contract checks, and
  live EDT/infobase verification for probes that depend on runtime evidence
