## Copilot Surface Notes

Root repository policy lives in `AGENTS.md`. Use this file only for Copilot-specific additions.

### Use Repo Policy First

- Follow `AGENTS.md`, `docs/agent/*`, and `openspec/*` as the repository source of truth.
- Use `rg` for code search.
- All code and UI strings must remain in English.

### When Expert Tools Are Helpful

If Copilot-specific expert/consultation tools are available in your surface, prefer them for:

- ambiguous 1C:EDT / platform-specific behaviour
- deletions, schema-level changes, or breaking protocol changes
- complex runtime/UI interactions inside EDT
- cases where local verification is blocked by missing EDT runtime context

### Useful References

- EDT plugin project docs: https://edt.1c.ru/dev/ru/docs/plugins/project/
- EDT 2025.2 API docs: https://edt.1c.ru/dev/edt/2025.2/apidocs/
