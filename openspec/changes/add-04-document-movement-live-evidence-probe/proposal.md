# Change: Add document movement live evidence probe

## Why

`add-03-live-acceptance-evidence-helpers` intentionally does not fake document movement evidence.
The current EDT runtime bridge does not expose a proven headless-safe read-only query path for
register records by recorder, so document movement evidence needs its own transport design,
implementation, and live proof.

## What Changes

- Add a dedicated read-only probe for document movements by recorder.
- Define and implement a proven headless-safe runtime transport/query path before reading live
  register records.
- Return recorder identity, register names, row counts, bounded samples, target identity, timeout
  status, and explicit limitations.
- Fail closed when runtime access, bounds, or read-only guarantees cannot be proven.

## Impact

- Affected specs: `workspace-and-metadata-management`
- Affected code: live runtime transport/query support, document movement probe tooling, tests,
  README/resources/generated docs
- Validation: focused unit/contract tests, strict OpenSpec validation, and installed-runtime live
  evidence against a real EDT/infobase target
