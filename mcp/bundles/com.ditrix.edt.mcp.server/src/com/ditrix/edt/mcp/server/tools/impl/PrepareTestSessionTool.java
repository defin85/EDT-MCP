/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.time.Instant;
import java.util.Map;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionProviderBridge;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRegistry;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionSnapshot;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionStaleReason;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionTarget;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionToolContract;
import com.ditrix.edt.mcp.server.testruns.YaxUnitRuntimeAdapter;
import com.ditrix.edt.mcp.server.tools.IMcpTool;

/**
 * Explicit warm-up or attach surface for persistent unit-test sessions.
 */
public class PrepareTestSessionTool implements IMcpTool
{
    public static final String NAME = UnitTestSessionToolContract.TOOL_PREPARE_TEST_SESSION;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Prepare or attach a persistent unit-test session for a supported target."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT configuration project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringEnumProperty("provider", "Supported unit-test provider (default: yaxunit).", //$NON-NLS-1$ //$NON-NLS-2$
                        java.util.List.of(YaxUnitRuntimeAdapter.PROVIDER))
                .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public TaskSupport getTaskSupport()
    {
        return TaskSupport.OPTIONAL;
    }

    @Override
    public TaskSchedulingKey getTaskSchedulingKey(Map<String, String> params)
    {
        return TaskSchedulingKey.projectScoped(JsonUtils.extractStringArgument(params, "projectName")); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        String provider = normalizeProvider(JsonUtils.extractStringArgument(params, "provider")); //$NON-NLS-1$
        if (projectName == null || projectName.isBlank())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (applicationId == null || applicationId.isBlank())
        {
            return ToolResult.error("applicationId is required. Use get_applications first.").toJson(); //$NON-NLS-1$
        }
        if (!YaxUnitRuntimeAdapter.PROVIDER.equals(provider))
        {
            return ToolResult.error("Unsupported provider: " + provider + ". The first rollout supports yaxunit only.") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("provider", provider) //$NON-NLS-1$
                    .put("supportedProviders", java.util.List.of(YaxUnitRuntimeAdapter.PROVIDER)) //$NON-NLS-1$
                    .toJson();
        }

        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").toJson(); //$NON-NLS-1$
        }

        String ownerSessionId = currentOwnerSessionId();
        UnitTestSessionTarget target = new UnitTestSessionTarget(provider, projectName, applicationId, null);
        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        UnitTestSessionSnapshot existing = registry.findLatestForTarget(ownerSessionId, target);
        if (existing != null && existing.isReusable(Instant.now()))
        {
            return success(existing, true);
        }

        UnitTestSessionProviderBridge bridge = server.getUnitTestSessionProviderBridgeRegistry().get(provider);
        if (bridge == null)
        {
            return ToolResult.error("No provider bridge is registered for persistent unit-test provider: " + provider) //$NON-NLS-1$
                    .put("provider", provider) //$NON-NLS-1$
                    .toJson();
        }

        UnitTestSessionSnapshot starting = registry.createStartingSession(ownerSessionId, target, Map.of("tool", NAME)); //$NON-NLS-1$
        UnitTestSessionProviderBridge.PrepareResult prepareResult;
        try
        {
            prepareResult = bridge.prepare(new UnitTestSessionProviderBridge.PrepareRequest(NAME, ownerSessionId,
                    target, Map.of(UnitTestSessionToolContract.FIELD_SESSION_ID, starting.getSessionId())));
        }
        catch (Exception e)
        {
            UnitTestSessionSnapshot stale = registry.markStale(starting.getSessionId(),
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return failure("Failed to prepare test session: " + e.getMessage(), stale); //$NON-NLS-1$
        }

        if (prepareResult == null)
        {
            UnitTestSessionSnapshot stale = registry.markStale(starting.getSessionId(),
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return failure("Provider bridge returned no prepare result", stale); //$NON-NLS-1$
        }
        if (!prepareResult.isSuccess())
        {
            UnitTestSessionSnapshot stale = registry.markStale(starting.getSessionId(),
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            ToolResult result = prepareResult.getFailureResult() != null ? prepareResult.getFailureResult()
                    : ToolResult.error("Provider bridge failed to prepare test session"); //$NON-NLS-1$
            stale.toPublicMap().forEach((key, value) -> result.put(key, value));
            return result.toJson();
        }

        UnitTestSessionSnapshot prepared = prepareResult.getSnapshot();
        if (prepared == null)
        {
            UnitTestSessionSnapshot stale = registry.markStale(starting.getSessionId(),
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return failure("Provider bridge prepared no session snapshot", stale); //$NON-NLS-1$
        }
        registry.put(prepared);
        return success(prepared, false);
    }

    private static String currentOwnerSessionId()
    {
        ToolExecutionContext context = ToolExecutionContextHolder.get();
        return context != null ? context.getSessionId() : null;
    }

    private static String success(UnitTestSessionSnapshot snapshot, boolean attached)
    {
        ToolResult result = ToolResult.success().put("attached", attached); //$NON-NLS-1$
        snapshot.toPublicMap().forEach((key, value) -> result.put(key, value));
        return result.toJson();
    }

    private static String failure(String message, UnitTestSessionSnapshot snapshot)
    {
        ToolResult result = ToolResult.error(message);
        if (snapshot != null)
        {
            snapshot.toPublicMap().forEach((key, value) -> result.put(key, value));
        }
        return result.toJson();
    }

    private static String normalizeProvider(String provider)
    {
        if (provider == null || provider.isBlank())
        {
            return YaxUnitRuntimeAdapter.PROVIDER;
        }
        return provider.trim().toLowerCase();
    }
}
