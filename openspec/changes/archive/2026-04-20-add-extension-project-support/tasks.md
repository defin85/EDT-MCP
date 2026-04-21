## 1. Baseline And Live Probe

- [x] 1.1 Confirm the current extension-related source assumptions and record the baseline behavior
      matrix from a workspace that contains both configuration and extension projects.

## 2. Shared Project Context

- [x] 2.1 Add shared project-kind, capability, and `ResolvedProjectContext` resolution for EDT
      projects.
- [x] 2.2 Add central capability validation so tools do not duplicate project-kind checks.

## 3. Client Discovery

- [x] 3.1 Extend `list_projects` to expose `projectKind`, stable capability categories, and
      extension metadata where available without regressing existing discovery clients.
- [x] 3.2 Add the bounded MCP transport refactor required for markdown-first discovery responses to
      carry structured project-record data alongside embedded markdown without breaking existing
      clients.
- [x] 3.3 Make the discovery contract deterministic for agents on top of that additive transport
      shape; do not rely on table-only markdown parsing.

## 4. Extension Metadata And Module Support

- [x] 4.1 Migrate only the verified first-wave read-only metadata and module tools to the shared
      project context and document the supported tool matrix explicitly.
- [x] 4.2 Clarify precise failure behavior when EDT cannot provide a compatible extension model.
- [x] 4.3 Use stable failure categories to distinguish unsupported extension operations from
      extension-model limitations.

## 5. Guarded Mutation And Runtime Boundaries

- [x] 5.1 Add guarded write/refactor handling for extension projects with actionable failures for
      unverified operations.
- [x] 5.2 Add explicit configuration-only rejection for runtime/application tools on extension
      projects and keep `get_configuration_properties` configuration-only in this rollout.
- [x] 5.3 Use a stable configuration-only failure category for extension rejections on runtime
      tools and `get_configuration_properties`.

## 6. Documentation And Verification

- [x] 6.1 Update `README.md` with an honest extension support matrix, stable capability hint
      vocabulary, and non-goals for lifecycle operations.
- [x] 6.2 Verify the final matrix on a real workspace for both configuration and extension
      projects.
