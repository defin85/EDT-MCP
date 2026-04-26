/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.ditrix.edt.mcp.server.resources.McpResource;
import com.ditrix.edt.mcp.server.resources.McpResourceRegistry;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.McpToolRegistry;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Runtime capability discovery over the installed tool and resource registries.
 */
public class DescribeCapabilitiesTool implements IMcpTool
{
    public static final String NAME = "describe_capabilities"; //$NON-NLS-1$

    private static final String UNKNOWN = "unknown"; //$NON-NLS-1$

    private static final List<String> ASYNC_TASK_TOOLS = Arrays.asList(
            ListTasksTool.NAME,
            GetTaskResultTool.NAME,
            WaitTaskTool.NAME);

    private static final List<String> RUNTIME_DEBUG_TOOLS = Arrays.asList(
            DebugLaunchTool.NAME,
            ListDebugSessionsTool.NAME,
            ListDebugBreakpointsTool.NAME,
            SetDebugBreakpointTool.NAME,
            RemoveDebugBreakpointTool.NAME,
            CleanupMcpDebugBreakpointsTool.NAME,
            GetDebugStackTool.NAME,
            GetDebugVariablesTool.NAME,
            EvaluateDebugExpressionTool.NAME,
            ControlDebugSessionTool.NAME,
            RunToDebugBreakpointTool.NAME);

    private static final List<String> YAXUNIT_TOOLS = Arrays.asList(
            RunUnitTestsTool.NAME,
            PrepareTestSessionTool.NAME,
            GetTestSessionStatusTool.NAME,
            RecycleTestSessionTool.NAME,
            GetTestRunReportTool.NAME);

    private static final List<String> EXTENSION_LIFECYCLE_TOOLS = Arrays.asList(
            GetExtensionPropertiesTool.NAME,
            GetExtensionRuntimeTargetsTool.NAME,
            ListInfobaseExtensionsTool.NAME,
            CheckExtensionApplicabilityTool.NAME,
            ApplyExtensionToInfobaseTool.NAME,
            ProbeExtensionSyncBridgeTool.NAME,
            ProbeExtensionXmlContractTool.NAME);

    private static final List<String> LIVE_DIAGNOSTIC_TOOLS = Arrays.asList(
            BslQueryDiagnosticsTool.NAME,
            FormEventContractTool.NAME);

    private static final List<String> LIVE_READ_ONLY_PROBES = Arrays.asList(
            ProbeFormCommandAvailabilityTool.NAME);

    private static final List<String> LIVE_MUTATION_DRY_RUN_TOOLS = Arrays.asList(
            ProbeDocumentWritePostDryRunTool.NAME);

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Describe installed EDT-MCP runtime capabilities, registered tools/resources, " //$NON-NLS-1$
                + "and fail-closed limitations."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object().build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("Describe installed runtime capabilities"); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        List<IMcpTool> tools = sortedTools(McpToolRegistry.getInstance().getAllTools());
        List<McpResource> resources = sortedResources(McpResourceRegistry.getInstance().getAllResources());
        Set<String> registeredToolNames = new LinkedHashSet<>();
        for (IMcpTool tool : tools)
        {
            registeredToolNames.add(tool.getName());
        }

        JsonObject result = new JsonObject();
        result.addProperty("success", true); //$NON-NLS-1$
        result.add("server", buildServerInfo()); //$NON-NLS-1$
        result.add("tools", buildToolsInfo(tools)); //$NON-NLS-1$
        result.add("resources", buildResourcesInfo(resources)); //$NON-NLS-1$
        result.add("capabilities", buildCapabilities(registeredToolNames)); //$NON-NLS-1$
        result.add("limitations", buildLimitations(registeredToolNames)); //$NON-NLS-1$
        return result.toString();
    }

    private static JsonObject buildServerInfo()
    {
        Bundle bundle = FrameworkUtil.getBundle(DescribeCapabilitiesTool.class);
        String symbolicName = bundle != null ? bundle.getSymbolicName() : null;
        Version version = bundle != null ? bundle.getVersion() : null;
        String edtVersion = GetEdtVersionTool.getEdtVersion();

        JsonObject server = new JsonObject();
        server.addProperty("serverName", McpConstants.SERVER_NAME); //$NON-NLS-1$
        server.addProperty("protocolVersion", McpConstants.PROTOCOL_VERSION); //$NON-NLS-1$
        server.addProperty("bundleSymbolicName", hasText(symbolicName) ? symbolicName : UNKNOWN); //$NON-NLS-1$
        server.addProperty("bundleVersion", version != null ? version.toString() : UNKNOWN); //$NON-NLS-1$
        server.addProperty("buildQualifier", getQualifier(version)); //$NON-NLS-1$
        server.addProperty("pluginVersion", McpConstants.PLUGIN_VERSION); //$NON-NLS-1$
        server.addProperty("edtVersion", hasText(edtVersion) ? edtVersion : "Unknown"); //$NON-NLS-1$ //$NON-NLS-2$
        return server;
    }

    private static JsonObject buildToolsInfo(List<IMcpTool> tools)
    {
        JsonObject info = new JsonObject();
        JsonArray names = new JsonArray();
        JsonArray items = new JsonArray();
        for (IMcpTool tool : tools)
        {
            names.add(tool.getName());

            JsonObject item = new JsonObject();
            item.addProperty("name", tool.getName()); //$NON-NLS-1$
            item.addProperty("responseType", tool.getResponseType().name()); //$NON-NLS-1$
            item.addProperty("taskSupport", tool.getTaskSupport().getWireValue()); //$NON-NLS-1$
            item.add("annotations", buildAnnotations(tool.getAnnotations())); //$NON-NLS-1$
            items.add(item);
        }

        info.addProperty("count", tools.size()); //$NON-NLS-1$
        info.add("names", names); //$NON-NLS-1$
        info.add("items", items); //$NON-NLS-1$
        return info;
    }

    private static JsonObject buildResourcesInfo(List<McpResource> resources)
    {
        JsonObject info = new JsonObject();
        JsonArray uris = new JsonArray();
        JsonArray items = new JsonArray();
        for (McpResource resource : resources)
        {
            uris.add(resource.getUri());

            JsonObject item = new JsonObject();
            item.addProperty("uri", resource.getUri()); //$NON-NLS-1$
            item.addProperty("name", resource.getName()); //$NON-NLS-1$
            item.addProperty("title", resource.getTitle()); //$NON-NLS-1$
            item.addProperty("mimeType", resource.getMimeType()); //$NON-NLS-1$
            item.addProperty("description", resource.getDescription()); //$NON-NLS-1$
            items.add(item);
        }

        info.addProperty("count", resources.size()); //$NON-NLS-1$
        info.add("uris", uris); //$NON-NLS-1$
        info.add("items", items); //$NON-NLS-1$
        return info;
    }

    private static JsonObject buildCapabilities(Set<String> registeredToolNames)
    {
        JsonObject capabilities = new JsonObject();
        capabilities.add("asyncTasks", buildCapabilityArea(registeredToolNames, ASYNC_TASK_TOOLS)); //$NON-NLS-1$
        capabilities.add("runtimeDebug", buildCapabilityArea(registeredToolNames, RUNTIME_DEBUG_TOOLS)); //$NON-NLS-1$
        capabilities.add("yaxUnit", buildCapabilityArea(registeredToolNames, YAXUNIT_TOOLS)); //$NON-NLS-1$
        capabilities.add("extensionLifecycle", buildCapabilityArea(registeredToolNames, EXTENSION_LIFECYCLE_TOOLS)); //$NON-NLS-1$
        capabilities.add("liveEvidence", buildLiveEvidence(registeredToolNames)); //$NON-NLS-1$
        return capabilities;
    }

    private static JsonObject buildCapabilityArea(Set<String> registeredToolNames, List<String> requiredTools)
    {
        JsonObject area = new JsonObject();
        List<String> present = presentTools(registeredToolNames, requiredTools);
        List<String> missing = missingTools(registeredToolNames, requiredTools);
        area.addProperty("status", statusFor(present, missing)); //$NON-NLS-1$
        area.add("tools", toJsonArray(present)); //$NON-NLS-1$
        if (!missing.isEmpty())
        {
            area.add("missingRequired", toJsonArray(missing)); //$NON-NLS-1$
        }
        return area;
    }

    private static JsonObject buildLiveEvidence(Set<String> registeredToolNames)
    {
        List<String> diagnostics = presentTools(registeredToolNames, LIVE_DIAGNOSTIC_TOOLS);
        List<String> readOnlyProbes = presentTools(registeredToolNames, LIVE_READ_ONLY_PROBES);
        List<String> mutationDryRunTools = presentTools(registeredToolNames, LIVE_MUTATION_DRY_RUN_TOOLS);
        List<String> allTools = new ArrayList<>();
        allTools.addAll(diagnostics);
        allTools.addAll(readOnlyProbes);
        allTools.addAll(mutationDryRunTools);

        JsonObject area = new JsonObject();
        area.addProperty("status", allTools.isEmpty() ? "unsupported" : "partial"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        area.add("tools", toJsonArray(allTools)); //$NON-NLS-1$
        area.add("diagnostics", toJsonArray(diagnostics)); //$NON-NLS-1$
        area.add("readOnlyProbes", toJsonArray(readOnlyProbes)); //$NON-NLS-1$

        JsonObject mutationDryRun = new JsonObject();
        mutationDryRun.addProperty("status", "unsupported"); //$NON-NLS-1$ //$NON-NLS-2$
        mutationDryRun.add("tools", toJsonArray(mutationDryRunTools)); //$NON-NLS-1$
        mutationDryRun.addProperty("reason", mutationDryRunTools.isEmpty() //$NON-NLS-1$
                ? "No write/post dry-run guardrail is registered." //$NON-NLS-1$
                : "Registered guardrail returns unsupported_safe_dry_run until rollback semantics are proven."); //$NON-NLS-1$
        area.add("mutationDryRun", mutationDryRun); //$NON-NLS-1$

        JsonObject documentMovements = new JsonObject();
        documentMovements.addProperty("status", "deferred"); //$NON-NLS-1$ //$NON-NLS-2$
        documentMovements.addProperty("changeId", "add-04-document-movement-live-evidence-probe"); //$NON-NLS-1$ //$NON-NLS-2$
        documentMovements.addProperty("reason", //$NON-NLS-1$
                "Register-record reads by recorder require a separate proven read-only runtime path."); //$NON-NLS-1$
        area.add("documentMovements", documentMovements); //$NON-NLS-1$
        return area;
    }

    private static JsonArray buildLimitations(Set<String> registeredToolNames)
    {
        JsonArray limitations = new JsonArray();
        limitations.add(limitation("installed_runtime_only", "runtime", "info", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "This report describes the currently installed and registered runtime, not checked-in source code.")); //$NON-NLS-1$
        limitations.add(limitation("document_movements_deferred_to_add_04", "liveEvidence", "warning", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "Document movement reads by recorder are intentionally deferred to add-04-document-movement-live-evidence-probe.")); //$NON-NLS-1$
        if (registeredToolNames.contains(ProbeFormCommandAvailabilityTool.NAME))
        {
            limitations.add(limitation("runtime_form_command_api_unavailable", "liveEvidence", "warning", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "Form command availability remains fail-closed when no proven headless-safe runtime command state API is available.")); //$NON-NLS-1$
        }
        if (registeredToolNames.contains(ProbeDocumentWritePostDryRunTool.NAME))
        {
            limitations.add(limitation("document_write_post_rollback_not_proven", "liveEvidence", "blocker", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "Document write/post dry-run is reported as unsupported until rollback and side-effect isolation are proven.")); //$NON-NLS-1$
        }
        return limitations;
    }

    private static JsonObject buildAnnotations(ToolAnnotations annotations)
    {
        JsonObject result = new JsonObject();
        if (annotations == null)
        {
            return result;
        }
        if (annotations.getTitle() != null)
        {
            result.addProperty("title", annotations.getTitle()); //$NON-NLS-1$
        }
        if (annotations.getReadOnlyHint() != null)
        {
            result.addProperty("readOnlyHint", annotations.getReadOnlyHint()); //$NON-NLS-1$
        }
        if (annotations.getDestructiveHint() != null)
        {
            result.addProperty("destructiveHint", annotations.getDestructiveHint()); //$NON-NLS-1$
        }
        if (annotations.getIdempotentHint() != null)
        {
            result.addProperty("idempotentHint", annotations.getIdempotentHint()); //$NON-NLS-1$
        }
        if (annotations.getOpenWorldHint() != null)
        {
            result.addProperty("openWorldHint", annotations.getOpenWorldHint()); //$NON-NLS-1$
        }
        return result;
    }

    private static JsonObject limitation(String id, String area, String severity, String message)
    {
        JsonObject limitation = new JsonObject();
        limitation.addProperty("id", id); //$NON-NLS-1$
        limitation.addProperty("area", area); //$NON-NLS-1$
        limitation.addProperty("severity", severity); //$NON-NLS-1$
        limitation.addProperty("message", message); //$NON-NLS-1$
        return limitation;
    }

    private static List<IMcpTool> sortedTools(Collection<IMcpTool> tools)
    {
        List<IMcpTool> sorted = new ArrayList<>(tools);
        sorted.sort(Comparator.comparing(IMcpTool::getName));
        return sorted;
    }

    private static List<McpResource> sortedResources(Collection<McpResource> resources)
    {
        List<McpResource> sorted = new ArrayList<>(resources);
        sorted.sort(Comparator.comparing(McpResource::getUri));
        return sorted;
    }

    private static List<String> presentTools(Set<String> registeredToolNames, List<String> expectedTools)
    {
        List<String> present = new ArrayList<>();
        for (String toolName : expectedTools)
        {
            if (registeredToolNames.contains(toolName))
            {
                present.add(toolName);
            }
        }
        return present;
    }

    private static List<String> missingTools(Set<String> registeredToolNames, List<String> expectedTools)
    {
        List<String> missing = new ArrayList<>();
        for (String toolName : expectedTools)
        {
            if (!registeredToolNames.contains(toolName))
            {
                missing.add(toolName);
            }
        }
        return missing;
    }

    private static String statusFor(List<String> present, List<String> missing)
    {
        if (present.isEmpty())
        {
            return "unsupported"; //$NON-NLS-1$
        }
        if (missing.isEmpty())
        {
            return "supported"; //$NON-NLS-1$
        }
        return "partial"; //$NON-NLS-1$
    }

    private static JsonArray toJsonArray(List<String> values)
    {
        JsonArray array = new JsonArray();
        for (String value : values)
        {
            array.add(value);
        }
        return array;
    }

    private static String getQualifier(Version bundleVersion)
    {
        if (bundleVersion == null)
        {
            return UNKNOWN;
        }
        String qualifier = bundleVersion.getQualifier();
        return hasText(qualifier) ? qualifier : UNKNOWN;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
