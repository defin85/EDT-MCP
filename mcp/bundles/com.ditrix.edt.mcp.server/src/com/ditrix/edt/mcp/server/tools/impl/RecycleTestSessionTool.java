/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionProviderBridge;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRecycleOutcome;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRegistry;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionSnapshot;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionStaleReason;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionState;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionToolContract;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;

/**
 * Controlled invalidation or provider-backed recycle for persistent unit-test sessions.
 */
public class RecycleTestSessionTool implements IMcpTool
{
    public static final String NAME = UnitTestSessionToolContract.TOOL_RECYCLE_TEST_SESSION;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: YAxUnit runtime testing. Recycle or invalidate a persistent warm session by sessionId before later reuse."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Recycle YAxUnit warm session") //$NON-NLS-1$
                .readOnlyHint(false)
                .destructiveHint(true)
                .openWorldHint(true)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty(UnitTestSessionToolContract.FIELD_SESSION_ID,
                        "Stable persistent unit-test session identifier.", true) //$NON-NLS-1$
                .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String sessionId = JsonUtils.extractStringArgument(params, UnitTestSessionToolContract.FIELD_SESSION_ID);
        if (sessionId == null || sessionId.isBlank())
        {
            return ToolResult.error("sessionId is required").toJson(); //$NON-NLS-1$
        }

        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").toJson(); //$NON-NLS-1$
        }

        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        UnitTestSessionSnapshot current = registry.get(sessionId);
        if (current == null)
        {
            return ToolResult.success()
                    .put("found", false) //$NON-NLS-1$
                    .put("requestedSessionId", sessionId) //$NON-NLS-1$
                    .put("message", "Test session not found or expired: " + sessionId) //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        UnitTestSessionProviderBridge bridge = server.getUnitTestSessionProviderBridgeRegistry()
                .get(current.getTarget().getProvider());
        if (bridge == null)
        {
            UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, current,
                    UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
            return success(UnitTestSessionRecycleOutcome.MARKED_STALE, stale, null);
        }

        UnitTestSessionProviderBridge.RecycleResult recycleResult;
        try
        {
            recycleResult = bridge.recycle(new UnitTestSessionProviderBridge.RecycleRequest(NAME, sessionId, current));
        }
        catch (Exception e)
        {
            UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, current,
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return failure("Failed to recycle test session: " + e.getMessage(), stale); //$NON-NLS-1$
        }

        if (recycleResult == null)
        {
            UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, current,
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return failure("Provider bridge returned no recycle result", stale); //$NON-NLS-1$
        }
        if (!recycleResult.isSuccess())
        {
            UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, current,
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            ToolResult result = recycleResult.getFailureResult() != null ? recycleResult.getFailureResult()
                    : ToolResult.error("Provider bridge failed to recycle test session"); //$NON-NLS-1$
            stale.toPublicMap().forEach((key, value) -> result.put(key, value));
            return result.toJson();
        }

        UnitTestSessionRecycleOutcome outcome = recycleResult.getOutcome();
        UnitTestSessionSnapshot replacement = recycleResult.getReplacementSnapshot();
        UnitTestSessionSnapshot finalOld = applyOutcome(registry, current, outcome, replacement);
        if (finalOld == null)
        {
            UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, current,
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return failure("Provider bridge returned an unsupported recycle outcome", stale); //$NON-NLS-1$
        }
        return success(outcome, finalOld, replacement);
    }

    private static UnitTestSessionSnapshot applyOutcome(UnitTestSessionRegistry registry, UnitTestSessionSnapshot current,
            UnitTestSessionRecycleOutcome outcome, UnitTestSessionSnapshot replacement)
    {
        if (UnitTestSessionRecycleOutcome.TERMINATED.equals(outcome))
        {
            return registry.markDead(current.getSessionId(), UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
        }
        if (UnitTestSessionRecycleOutcome.REPLACED.equals(outcome))
        {
            if (replacement == null)
            {
                return null;
            }
            UnitTestSessionSnapshot old = markStaleWithoutDowngrade(registry, current,
                    UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
            registry.put(replacement);
            return old;
        }
        if (UnitTestSessionRecycleOutcome.MARKED_STALE.equals(outcome))
        {
            return markStaleWithoutDowngrade(registry, current, UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
        }
        return null;
    }

    private static UnitTestSessionSnapshot markStaleWithoutDowngrade(UnitTestSessionRegistry registry,
            UnitTestSessionSnapshot current, UnitTestSessionStaleReason reason)
    {
        if (UnitTestSessionState.DEAD.equals(current.getState()))
        {
            return current;
        }
        return registry.markStale(current.getSessionId(), reason);
    }

    private static String success(UnitTestSessionRecycleOutcome outcome, UnitTestSessionSnapshot oldSnapshot,
            UnitTestSessionSnapshot replacement)
    {
        ToolResult result = ToolResult.success()
                .put("found", true) //$NON-NLS-1$
                .put(UnitTestSessionToolContract.FIELD_RECYCLE_OUTCOME, outcome.wireValue());
        oldSnapshot.toPublicMap().forEach((key, value) -> result.put(key, value));
        if (replacement != null)
        {
            result.put("replacementSessionId", replacement.getSessionId()); //$NON-NLS-1$
            result.put("replacementSession", replacement.toPublicMap()); //$NON-NLS-1$
        }
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
}
