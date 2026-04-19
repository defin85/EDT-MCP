## 1. Contract And Discovery

- [x] 1.1 Update long-running-operation contract to make the verified long-running mutable tools
      async-first by default.
- [x] 1.2 Update tool discovery metadata and descriptions to mark the affected tools as task-only
      at runtime without falsely advertising MCP `execution.taskSupport: "required"`.

## 2. Protocol Behavior

- [x] 2.1 Promote bare `tools/call` requests for `update_database`, `clean_project`, and
      full-project `revalidate_objects` into task-backed execution by default.
- [x] 2.2 Remove the legacy synchronous execution branch for the affected tools.

## 3. Documentation

- [x] 3.1 Update `README.md` to explain task-only invocation and task result retrieval for the
      affected tools, including same-session task retrieval.
- [x] 3.2 Update agent-facing docs so wrappers do not keep treating bare calls as synchronous by
      default and do not infer `execution.taskSupport: "required"` from EDT-MCP auto-promotion.

## 4. Verification

- [x] 4.1 Add focused tests for async promotion and sync-branch removal behavior.
- [x] 4.2 Verify on a live EDT workspace that bare calls now produce task-backed execution for the
      affected tools and that same-session task polling/result retrieval still works.
