## 1. Baseline And Live Probe

- [ ] 1.1 Confirm the current extension-related source assumptions and record the baseline behavior
      matrix from a workspace that contains both configuration and extension projects.

## 2. Shared Project Context

- [ ] 2.1 Add shared project-kind, capability, and `ResolvedProjectContext` resolution for EDT
      projects.
- [ ] 2.2 Add central capability validation so tools do not duplicate project-kind checks.

## 3. Client Discovery

- [ ] 3.1 Extend `list_projects` to expose `projectKind`, stable capability categories, and
      extension metadata where available without regressing existing discovery clients.

## 4. Extension Metadata And Module Support

- [ ] 4.1 Migrate only the verified first-wave read-only metadata and module tools to the shared
      project context and document the supported tool matrix explicitly.
- [ ] 4.2 Clarify precise failure behavior when EDT cannot provide a compatible extension model.

## 5. Guarded Mutation And Runtime Boundaries

- [ ] 5.1 Add guarded write/refactor handling for extension projects with actionable failures for
      unverified operations.
- [ ] 5.2 Add explicit configuration-only rejection for runtime/application tools on extension
      projects and keep `get_configuration_properties` configuration-only in this rollout.

## 6. Documentation And Verification

- [ ] 6.1 Update `README.md` with an honest extension support matrix, stable capability hint
      vocabulary, and non-goals for lifecycle operations.
- [ ] 6.2 Verify the final matrix on a real workspace for both configuration and extension
      projects.
