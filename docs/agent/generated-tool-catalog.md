<!-- GENERATED FILE: do not edit manually. Run `python3 scripts/generate_agent_refs.py`. -->
# Generated Tool Catalog

Этот файл генерируется из `tools/impl/*Tool.java` и служит fast reference для Codex.

- Tool implementations found: `44`
- Tool names documented in `README.md`: `44`
- Drift status: `missing_in_readme=0`, `missing_in_code=0`

## Tool Map

| Tool | Implementation | Primary tests | Zone | Notes |
|------|----------------|---------------|------|-------|
| `add_metadata_attribute` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/AddMetadataAttributeTool.java` | `-` | `mutation/refactoring` | - |
| `apply_extension_to_infobase` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ApplyExtensionToInfobaseTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/ApplyExtensionToInfobaseToolTest.java` | `mixed` | - |
| `check_extension_applicability` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/CheckExtensionApplicabilityTool.java` | `-` | `mixed` | - |
| `clean_project` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/CleanProjectTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `debug_launch` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/DebugLaunchTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `delete_metadata_object` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/DeleteMetadataObjectTool.java` | `-` | `mutation/refactoring` | - |
| `find_references` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/FindReferencesTool.java` | `-` | `analysis/navigation` | - |
| `get_active_operation` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetActiveOperationTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `get_applications` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetApplicationsTool.java` | `-` | `read/discovery` | - |
| `get_bookmarks` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetBookmarksTool.java` | `-` | `read/discovery` | - |
| `get_check_description` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetCheckDescriptionTool.java` | `-` | `read/discovery` | - |
| `get_configuration_properties` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetConfigurationPropertiesTool.java` | `-` | `read/discovery` | - |
| `get_content_assist` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GetContentAssistTool.java` | `-` | `read/discovery` | - |
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
| `go_to_definition` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/GoToDefinitionTool.java` | `-` | `analysis/navigation` | - |
| `list_infobase_extensions` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListInfobaseExtensionsTool.java` | `-` | `read/discovery` | - |
| `list_modules` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListModulesTool.java` | `-` | `read/discovery` | - |
| `list_projects` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ListProjectsTool.java` | `-` | `read/discovery` | - |
| `probe_extension_sync_bridge` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ProbeExtensionSyncBridgeTool.java` | `-` | `mixed` | - |
| `probe_extension_xml_contract` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ProbeExtensionXmlContractTool.java` | `-` | `mixed` | - |
| `read_method_source` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ReadMethodSourceTool.java` | `-` | `mixed` | - |
| `read_module_source` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ReadModuleSourceTool.java` | `-` | `mixed` | - |
| `rename_metadata_object` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/RenameMetadataObjectTool.java` | `-` | `mutation/refactoring` | - |
| `revalidate_objects` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/RevalidateObjectsTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `search_in_code` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/SearchInCodeTool.java` | `-` | `mixed` | - |
| `update_database` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/UpdateDatabaseTool.java` | `-` | `long-running-runtime` | `docs/agent/long-running-ops.md` |
| `validate_query` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/ValidateQueryTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/ValidateQueryToolTest.java` | `analysis/navigation` | - |
| `write_module_source` | `mcp/bundles/com.ditrix.edt.mcp.server/src/com/ditrix/edt/mcp/server/tools/impl/WriteModuleSourceTool.java` | `mcp/tests/com.ditrix.edt.mcp.server.tests/src/com/ditrix/edt/mcp/server/tools/impl/WriteModuleSourceToolTest.java` | `mutation/refactoring` | - |

## Drift Check

- Missing in `README.md`: none
- Missing in code scan: none

## Regeneration

```bash
python3 scripts/generate_agent_refs.py
```
