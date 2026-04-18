# Change: Add explicit support for extension projects

## Why

The repository already recognizes extension projects in some paths, but that support is partial and
mostly accidental. Agents and users cannot reliably tell which tools are safe on extension
projects, which metadata flows are supported, and which runtime flows must fail. The project needs
an explicit, testable extension-support contract before it can claim that extensions are supported.

## What Changes

- Add an explicit extension-project capability surface built around shared project-kind and
  capability resolution.
- Expose project kind and capability hints to clients before tool execution.
- Make read-only metadata/module flows for extension projects explicit and testable.
- Add guarded write/refactor behavior and explicit failures for configuration-only runtime tools.
- Keep extension lifecycle work such as import/export or infobase attachment out of this rollout.

## Impact

- Affected specs: `extension-project-support`
- Affected code: project context layer, metadata/module/refactor tools, runtime capability checks,
  `list_projects`, `README.md`
- Validation: source-level checks plus a real workspace matrix with both configuration and
  extension projects
