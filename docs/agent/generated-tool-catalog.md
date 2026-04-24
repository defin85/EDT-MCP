<!-- GENERATED FILE: do not edit manually. Run `python3 scripts/generate_agent_refs.py`. -->
# Generated Tool Catalog

Этот файл генерируется из `tools/impl/*Tool.java` и `McpResourceRegistry.java` и служит fast reference для Codex.

- Tool implementations found: `56`
- Tool names documented in `README.md`: `56`
- Tools with discovery annotations: `20`
- Static MCP resources found: `7`
- Drift status: `missing_in_readme=0`, `missing_in_code=0`

## Tool Map

| Tool | Implementation | Primary tests | Zone | Notes |
|------|----------------|---------------|------|-------|
| `add_metadata_attribute` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/AddMetadataAttributeTool.java` | `-` | `mutation/refactoring` | - |
| `apply_extension_to_infobase` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ApplyExtensionToInfobaseTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/ApplyExtensionToInfobaseToolTest.java` | `mixed` | - |
| `check_extension_applicability` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/CheckExtensionApplicabilityTool.java` | `-` | `mixed` | - |
| `clean_project` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/CleanProjectTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `control_debug_session` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ControlDebugSessionTool.java` | `-` | `mixed` | - |
| `debug_launch` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/DebugLaunchTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `delete_metadata_object` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/DeleteMetadataObjectTool.java` | `-` | `mutation/refactoring` | - |
| `find_references` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/FindReferencesTool.java` | `-` | `analysis/navigation` | - |
| `get_active_operation` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetActiveOperationTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `get_applications` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetApplicationsTool.java` | `-` | `read/discovery` | - |
| `get_bookmarks` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetBookmarksTool.java` | `-` | `read/discovery` | - |
| `get_check_description` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetCheckDescriptionTool.java` | `-` | `read/discovery` | - |
| `get_configuration_properties` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetConfigurationPropertiesTool.java` | `-` | `read/discovery` | - |
| `get_content_assist` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetContentAssistTool.java` | `-` | `read/discovery` | - |
| `get_debug_stack` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetDebugStackTool.java` | `-` | `read/discovery` | - |
| `get_debug_variables` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetDebugVariablesTool.java` | `-` | `read/discovery` | - |
| `get_edt_version` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetEdtVersionTool.java` | `-` | `read/discovery` | - |
| `get_extension_properties` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetExtensionPropertiesTool.java` | `-` | `read/discovery` | - |
| `get_extension_runtime_targets` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetExtensionRuntimeTargetsTool.java` | `-` | `read/discovery` | - |
| `get_form_screenshot` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetFormScreenshotTool.java` | `-` | `read/discovery` | - |
| `get_metadata_details` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetMetadataDetailsTool.java` | `-` | `read/discovery` | - |
| `get_metadata_objects` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetMetadataObjectsTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/GetMetadataObjectsToolTest.java` | `read/discovery` | - |
| `get_method_call_hierarchy` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetMethodCallHierarchyTool.java` | `-` | `read/discovery` | - |
| `get_module_structure` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetModuleStructureTool.java` | `-` | `read/discovery` | - |
| `get_objects_by_tags` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetObjectsByTagsTool.java` | `-` | `read/discovery` | - |
| `get_operation_snapshot` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetOperationSnapshotTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/GetOperationSnapshotToolTest.java` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `get_platform_documentation` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetPlatformDocumentationTool.java` | `-` | `read/discovery` | - |
| `get_problem_summary` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetProblemSummaryTool.java` | `-` | `read/discovery` | - |
| `get_project_errors` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetProjectErrorsTool.java` | `-` | `read/discovery` | - |
| `get_server_build_info` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetServerBuildInfoTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/GetServerBuildInfoToolTest.java` | `read/discovery` | - |
| `get_symbol_info` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetSymbolInfoTool.java` | `-` | `read/discovery` | - |
| `get_tags` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetTagsTool.java` | `-` | `read/discovery` | - |
| `get_tasks` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetTasksTool.java` | `-` | `read/discovery` | - |
| `get_test_run_report` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetTestRunReportTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/GetTestRunReportToolTest.java` | `read/discovery` | - |
| `get_test_session_status` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetTestSessionStatusTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/GetTestSessionStatusToolTest.java` | `read/discovery` | - |
| `go_to_definition` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GoToDefinitionTool.java` | `-` | `analysis/navigation` | - |
| `list_debug_breakpoints` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListDebugBreakpointsTool.java` | `-` | `read/discovery` | - |
| `list_debug_sessions` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListDebugSessionsTool.java` | `-` | `read/discovery` | - |
| `list_infobase_extensions` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListInfobaseExtensionsTool.java` | `-` | `read/discovery` | - |
| `list_modules` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListModulesTool.java` | `-` | `read/discovery` | - |
| `list_projects` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListProjectsTool.java` | `-` | `read/discovery` | - |
| `prepare_test_session` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/PrepareTestSessionTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/PrepareTestSessionToolTest.java` | `mixed` | - |
| `probe_extension_sync_bridge` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ProbeExtensionSyncBridgeTool.java` | `-` | `mixed` | - |
| `probe_extension_xml_contract` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ProbeExtensionXmlContractTool.java` | `-` | `mixed` | - |
| `read_method_source` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ReadMethodSourceTool.java` | `-` | `mixed` | - |
| `read_module_source` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ReadModuleSourceTool.java` | `-` | `mixed` | - |
| `recycle_test_session` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/RecycleTestSessionTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/RecycleTestSessionToolTest.java` | `mixed` | - |
| `remove_debug_breakpoint` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/RemoveDebugBreakpointTool.java` | `-` | `mixed` | - |
| `rename_metadata_object` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/RenameMetadataObjectTool.java` | `-` | `mutation/refactoring` | - |
| `revalidate_objects` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/RevalidateObjectsTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `run_unit_tests` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/RunUnitTestsTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/RunUnitTestsToolTest.java` | `mixed` | - |
| `search_in_code` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/SearchInCodeTool.java` | `-` | `mixed` | - |
| `set_debug_breakpoint` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/SetDebugBreakpointTool.java` | `-` | `mixed` | - |
| `update_database` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/UpdateDatabaseTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `validate_query` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ValidateQueryTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/ValidateQueryToolTest.java` | `analysis/navigation` | - |
| `write_module_source` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/WriteModuleSourceTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/WriteModuleSourceToolTest.java` | `mutation/refactoring` | - |

## Tool Discovery Metadata

`tools/list` may include MCP `annotations` for tools with conservative safety metadata.

| Tool | Annotation title |
|------|------------------|
| `apply_extension_to_infobase` | `Apply extension to infobase` |
| `check_extension_applicability` | `Check extension applicability guardrail` |
| `control_debug_session` | `Control runtime debug session` |
| `debug_launch` | `Launch EDT debug session` |
| `get_debug_stack` | `Read runtime debug stack` |
| `get_debug_variables` | `Read runtime debug variables` |
| `get_extension_properties` | `Read extension project properties` |
| `get_extension_runtime_targets` | `Resolve extension runtime targets` |
| `get_test_run_report` | `Read YAxUnit run report` |
| `get_test_session_status` | `Inspect YAxUnit session status` |
| `list_debug_breakpoints` | `List BSL debug breakpoints` |
| `list_debug_sessions` | `List runtime debug sessions` |
| `list_infobase_extensions` | `List installed infobase extensions` |
| `prepare_test_session` | `Prepare YAxUnit warm session` |
| `probe_extension_sync_bridge` | `Probe extension sync bridge` |
| `probe_extension_xml_contract` | `Probe extension XML contract` |
| `recycle_test_session` | `Recycle YAxUnit warm session` |
| `remove_debug_breakpoint` | `Remove BSL debug breakpoint` |
| `run_unit_tests` | `Run YAxUnit unit tests` |
| `set_debug_breakpoint` | `Set BSL debug breakpoint` |

## MCP Resource Map

Static resources are exposed through `resources/list` and `resources/read`; live runtime state stays tool-owned.

| URI | Name | MIME type | Description |
|-----|------|-----------|-------------|
| `edt-mcp://capabilities/yaxunit-runtime-testing` | `yaxunit-runtime-testing` | `text/markdown` | YAxUnit cold and warm runtime testing workflow exposed by EDT-MCP. |
| `edt-mcp://capabilities/runtime-debug-control` | `runtime-debug-control` | `text/markdown` | Runtime debug launch, session inspection, breakpoint, stack, variable, and control workflow. |
| `edt-mcp://workflows/yaxunit-warm-session` | `yaxunit-warm-session-workflow` | `text/markdown` | Step-by-step warm-session reuse workflow for YAxUnit test runs. |
| `edt-mcp://workflows/runtime-debug-breakpoint` | `runtime-debug-breakpoint-workflow` | `text/markdown` | Step-by-step MCP-owned BSL breakpoint workflow for runtime debug sessions. |
| `edt-mcp://capabilities/extension-lifecycle` | `extension-lifecycle` | `text/markdown` | Extension discovery, installed-extension inspection, guarded applicability, apply, and diagnostic probe workflow. |
| `edt-mcp://workflows/extension-apply` | `extension-apply-workflow` | `text/markdown` | Step-by-step workflow for applying an extension project to an infobase target. |
| `edt-mcp://limitations/runtime-testing-and-debug` | `runtime-testing-and-debug-limitations` | `text/markdown` | Known scope limits for the YAxUnit and runtime debug discovery contours. |

## Drift Check

- Missing in `README.md`: none
- Missing in code scan: none

## Regeneration

```bash
python3 scripts/generate_agent_refs.py
```
