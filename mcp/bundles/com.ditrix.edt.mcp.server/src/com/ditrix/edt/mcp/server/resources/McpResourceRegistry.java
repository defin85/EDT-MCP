/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.resources;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for static MCP resources exposed by resources/list and resources/read.
 */
public class McpResourceRegistry
{
    private static final String MARKDOWN = "text/markdown"; //$NON-NLS-1$

    private static final McpResourceRegistry INSTANCE = new McpResourceRegistry();

    private final Map<String, McpResource> resources = new LinkedHashMap<>();

    private McpResourceRegistry()
    {
        register(new McpResource(
                "edt-mcp://capabilities/yaxunit-runtime-testing", //$NON-NLS-1$
                "yaxunit-runtime-testing", //$NON-NLS-1$
                "YAxUnit runtime testing", //$NON-NLS-1$
                "YAxUnit cold and warm runtime testing workflow exposed by EDT-MCP.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# YAxUnit runtime testing",
                        "",
                        "Use this capability when an agent needs to run YAxUnit tests through a selected EDT project and application target.",
                        "",
                        "## Tool chain",
                        "",
                        "1. `get_applications` discovers the `applicationId` for an EDT configuration project.",
                        "2. `prepare_test_session` prepares or attaches a persistent YAxUnit warm session and returns `sessionId`.",
                        "3. `run_unit_tests` runs tests cold or with `sessionMode` set to `prefer_warm`, `require_warm`, or `recycle_then_run`.",
                        "4. Task-backed runs return task metadata first; retrieve the final payload with `tasks/result` in the same MCP session.",
                        "5. `get_test_run_report` reads retained summaries, manifests, or JUnit payloads by `runId`.",
                        "6. `get_test_session_status` inspects warm-session lifecycle state by `sessionId`.",
                        "7. `recycle_test_session` explicitly invalidates or recycles a warm session.",
                        "",
                        "## Live state",
                        "",
                        "This resource is static guidance only. Current sessions, tasks, and retained reports are authoritative only through their MCP tools.")));

        register(new McpResource(
                "edt-mcp://capabilities/runtime-debug-control", //$NON-NLS-1$
                "runtime-debug-control", //$NON-NLS-1$
                "Runtime debug control", //$NON-NLS-1$
                "Runtime debug launch, session inspection, breakpoint, stack, variable, and control workflow.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# Runtime debug control",
                        "",
                        "Use this capability when an agent needs to drive a supported EDT runtime debug session from MCP.",
                        "",
                        "## Tool chain",
                        "",
                        "1. `get_applications` discovers the target application.",
                        "2. `set_debug_breakpoint` creates or reuses an MCP-owned BSL line breakpoint.",
                        "3. `debug_launch` starts the EDT runtime in debug mode for the target application.",
                        "4. `list_debug_sessions` discovers active supported sessions and returns `sessionId` and `threadId` values.",
                        "5. `get_debug_stack` reads suspended stack frames by `threadId` and returns `frameId` values.",
                        "6. `get_debug_variables` reads bounded variables for a selected `frameId`.",
                        "7. `control_debug_session` resumes, suspends, steps, or terminates the addressed session or thread.",
                        "8. `remove_debug_breakpoint` removes MCP-owned breakpoints by `breakpointId`.",
                        "",
                        "## Live state",
                        "",
                        "This resource does not contain active session, frame, variable, or breakpoint state. Poll the debug tools for current runtime state.")));

        register(new McpResource(
                "edt-mcp://workflows/yaxunit-warm-session", //$NON-NLS-1$
                "yaxunit-warm-session-workflow", //$NON-NLS-1$
                "YAxUnit warm-session workflow", //$NON-NLS-1$
                "Step-by-step warm-session reuse workflow for YAxUnit test runs.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# YAxUnit warm-session workflow",
                        "",
                        "## Recommended sequence",
                        "",
                        "1. Call `get_applications` for the configuration project.",
                        "2. Call `prepare_test_session` with `projectName`, `applicationId`, and provider `yaxunit`.",
                        "3. Store the returned `sessionId` and target identity.",
                        "4. Call `run_unit_tests` with `sessionMode: prefer_warm` for best-effort reuse or `require_warm` for fail-closed reuse.",
                        "5. Retrieve the task result through `tasks/result` using the same `MCP-Session-Id`.",
                        "6. Read retained report content with `get_test_run_report` and the returned `runId`.",
                        "7. Use `get_test_session_status` before later reuse attempts.",
                        "8. Use `recycle_test_session` when a session is stale, dead, or no longer wanted.",
                        "",
                        "## Fail-closed rules",
                        "",
                        "`require_warm` must not silently fall back to a cold run. Stale, dead, or target-mismatched sessions require an explicit client decision.")));

        register(new McpResource(
                "edt-mcp://workflows/runtime-debug-breakpoint", //$NON-NLS-1$
                "runtime-debug-breakpoint-workflow", //$NON-NLS-1$
                "Runtime debug breakpoint workflow", //$NON-NLS-1$
                "Step-by-step MCP-owned BSL breakpoint workflow for runtime debug sessions.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# Runtime debug breakpoint workflow",
                        "",
                        "## Recommended sequence",
                        "",
                        "1. Call `set_debug_breakpoint` with `projectName`, `modulePath` relative to `src`, and 1-based `lineNumber`.",
                        "2. Keep the returned `breakpointId` and ownership metadata.",
                        "3. Call `debug_launch` for the selected project/application target.",
                        "4. Poll `list_debug_sessions` until a supported thread is suspended.",
                        "5. Call `get_debug_stack` with the suspended `threadId`.",
                        "6. Call `get_debug_variables` with a selected `frameId` when variable inspection is needed.",
                        "7. Call `evaluate_debug_expression` only for bounded checks on a current suspended `frameId`; responses do not guarantee side-effect-free evaluation.",
                        "8. Call `control_debug_session` for `step_over`, `step_into`, `step_return`, `resume`, `suspend`, or `terminate`.",
                        "9. Call `remove_debug_breakpoint` or `cleanup_mcp_debug_breakpoints` for MCP-owned breakpoints during cleanup.",
                        "",
                        "## One-shot helper",
                        "",
                        "`run_to_debug_breakpoint` composes the sequence for common operator checks: it sets or reuses a temporary breakpoint, launches or resumes execution, waits up to `timeoutSeconds`, and returns direct stack/variable evidence or a timeout outcome.",
                        "",
                        "## Safety rules",
                        "",
                        "Default cleanup protects pre-existing user breakpoints. Removing a non-owned user breakpoint requires the explicit override accepted by `remove_debug_breakpoint`.",
                        "Expression evaluation can be observable in the runtime; do not treat it as a read-only proof surface.")));

        register(new McpResource(
                "edt-mcp://capabilities/extension-lifecycle", //$NON-NLS-1$
                "extension-lifecycle", //$NON-NLS-1$
                "Extension lifecycle", //$NON-NLS-1$
                "Extension discovery, installed-extension inspection, guarded applicability, apply, and diagnostic probe workflow.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# Extension lifecycle",
                        "",
                        "Use this capability when an agent needs to inspect or synchronize a 1C extension project against an infobase target.",
                        "",
                        "## Tool chain",
                        "",
                        "1. `get_extension_properties` reads extension-project root metadata.",
                        "2. `get_extension_runtime_targets` resolves the parent configuration project and returns `applicationId` values.",
                        "3. `list_infobase_extensions` reads extensions currently installed in the selected target.",
                        "4. `check_extension_applicability` returns the current MCP-safe guardrail status; it is intentionally fail-closed and does not drive the unsafe public applicability path.",
                        "5. `apply_extension_to_infobase` is the public mutation tool. It uses the internal EDT synchronization bridge and is async-first through MCP tasks.",
                        "6. `probe_extension_sync_bridge` is a developer diagnostic for the same backend used by apply.",
                        "7. `probe_extension_xml_contract` is a developer diagnostic for EDT XML export/import layout questions.",
                        "",
                        "## Live state",
                        "",
                        "Resources are static guidance. Current target state, installed extensions, sync state, task progress, and final apply results are authoritative only through the tools.")));

        register(new McpResource(
                "edt-mcp://workflows/extension-apply", //$NON-NLS-1$
                "extension-apply-workflow", //$NON-NLS-1$
                "Extension apply workflow", //$NON-NLS-1$
                "Step-by-step workflow for applying an extension project to an infobase target.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# Extension apply workflow",
                        "",
                        "## Recommended sequence",
                        "",
                        "1. Call `get_extension_runtime_targets` with the extension `projectName`.",
                        "2. Select an `applicationId` from the returned target list.",
                        "3. Optionally call `list_infobase_extensions` to inspect currently installed extensions.",
                        "4. Treat `check_extension_applicability` as a guardrail/readiness signal, not as a required apply backend.",
                        "5. Call `apply_extension_to_infobase` with `projectName`, `applicationId`, and explicit `fullReload` / `autoRestructure` choices when defaults are not acceptable.",
                        "6. For bare calls, retrieve the final payload with `tasks/result` in the same `MCP-Session-Id`.",
                        "7. Use `get_operation_snapshot` if the apply operation detaches and returns an `operationId` polling hint.",
                        "8. Use developer probes only when diagnosing or proving the bridge/XML contract on a controlled target.",
                        "",
                        "## Safety rules",
                        "",
                        "`apply_extension_to_infobase` mutates the selected infobase target. The developer probes are not normal automation entry points. Do not infer live apply completion from resources; poll task and operation tools.")));

        register(new McpResource(
                "edt-mcp://capabilities/live-acceptance-evidence", //$NON-NLS-1$
                "live-acceptance-evidence", //$NON-NLS-1$
                "Live acceptance evidence", //$NON-NLS-1$
                "Bounded diagnostics and fail-closed probes for proving acceptance evidence from installed runtime capabilities.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# Live acceptance evidence",
                        "",
                        "Use this capability when an agent needs direct proof for a narrow acceptance question without widening into a general test framework.",
                        "",
                        "## Tool chain",
                        "",
                        "1. Call `describe_capabilities` first to inspect the installed runtime surface and known limitations.",
                        "2. Use `revalidate_objects` dry-run/preflight options before expensive validation when available.",
                        "3. Use `diagnose_bsl_queries` to extract and validate source-tied query text from a module or method scope.",
                        "4. Use `check_form_event_contract` to compare form metadata event bindings with form module handlers.",
                        "5. Use `probe_form_command_availability` only as a fail-closed live-evidence guardrail when command state is not safely exposed.",
                        "6. Use `probe_document_write_post_dry_run` only for its explicit unsupported-safe-dry-run evidence until rollback semantics are proven.",
                        "",
                        "## Scope limits",
                        "",
                        "- Document movement reads by recorder are intentionally deferred to `add-04-document-movement-live-evidence-probe`.",
                        "- Form command availability does not claim live runtime support unless the response status says it is supported.",
                        "- Document write/post dry-run performs no mutation in this rollout and reports unsupported until rollback and side-effect isolation are proven.",
                        "- Static resources are guidance only; current availability is authoritative through `describe_capabilities` and each tool response.")));

        register(new McpResource(
                "edt-mcp://limitations/runtime-testing-and-debug", //$NON-NLS-1$
                "runtime-testing-and-debug-limitations", //$NON-NLS-1$
                "Runtime testing and debug limitations", //$NON-NLS-1$
                "Known scope limits for the YAxUnit and runtime debug discovery contours.", //$NON-NLS-1$
                MARKDOWN,
                markdown(
                        "# Runtime testing and debug limitations",
                        "",
                        "- Resources are static guidance. They do not replace live tools for sessions, tasks, runs, frames, variables, or breakpoints.",
                        "- `resources/templates/list`, resource subscriptions, and list-changed notifications are intentionally not implemented in this rollout.",
                        "- `run_unit_tests` is YAxUnit-backed and configuration-project focused.",
                        "- Warm-session reuse is controlled by explicit `sessionMode` values and must fail closed for stale or mismatched sessions.",
                        "- Runtime debug control is limited to supported EDT runtime launches and standard Eclipse debug model capabilities exposed by the bridge.",
                        "- Expression evaluation depends on the current frame debug model exposing an Eclipse watch-expression delegate and is not guaranteed side-effect-free.",
                        "- Breakpoint identifiers are stable for the current EDT workspace session only; refresh with `list_debug_breakpoints` after restart.")));
    }

    public static McpResourceRegistry getInstance()
    {
        return INSTANCE;
    }

    public Collection<McpResource> getAllResources()
    {
        return Collections.unmodifiableCollection(resources.values());
    }

    public McpResource getResource(String uri)
    {
        if (uri == null)
        {
            return null;
        }
        return resources.get(uri);
    }

    private void register(McpResource resource)
    {
        resources.put(resource.getUri(), resource);
    }

    private static String markdown(String... lines)
    {
        return String.join("\n", lines) + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
