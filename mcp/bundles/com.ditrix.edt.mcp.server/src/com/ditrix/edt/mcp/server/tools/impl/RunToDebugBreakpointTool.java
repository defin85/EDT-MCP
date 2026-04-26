/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.HashMap;
import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugBreakpointBridge;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugModelBridge;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * One-shot helper that runs a supported EDT debug session to a BSL line breakpoint.
 */
public class RunToDebugBreakpointTool implements IMcpTool
{
    public static final String NAME = "run_to_debug_breakpoint"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug control. Set or reuse a temporary MCP-owned BSL breakpoint, launch or resume, then wait boundedly for a suspended matching frame."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Run to debug breakpoint") //$NON-NLS-1$
                .readOnlyHint(false)
                .destructiveHint(true)
                .idempotentHint(false)
                .openWorldHint(true)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("modulePath", "BSL module path relative to src", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("lineNumber", "1-based BSL source line number", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID for launch mode; optional when threadId is provided") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("threadId", "Existing threadId to resume; if omitted applicationId is launched") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("updateBeforeLaunch", "If launching, update database first (default: true)") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Bounded wait for suspend at the target line (default 30, max 300)") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("maxVariables", "Maximum variables to return from the matched top frame (default 100, max 200)") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("cleanupOnTimeout", "Remove a newly-created temporary breakpoint on timeout (default: true)") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("cleanupOnSuspend", "Remove a newly-created temporary breakpoint after a match (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
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
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String modulePath = JsonUtils.extractStringArgument(params, "modulePath"); //$NON-NLS-1$
        int lineNumber = JsonUtils.extractIntArgument(params, "lineNumber", -1); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        String threadId = JsonUtils.extractStringArgument(params, "threadId"); //$NON-NLS-1$
        boolean updateBeforeLaunch = JsonUtils.extractBooleanArgument(params, "updateBeforeLaunch", true); //$NON-NLS-1$
        int timeoutSeconds = JsonUtils.extractIntArgument(params, "timeoutSeconds", 0); //$NON-NLS-1$
        int maxVariables = JsonUtils.extractIntArgument(params, "maxVariables", 0); //$NON-NLS-1$
        boolean cleanupOnTimeout = JsonUtils.extractBooleanArgument(params, "cleanupOnTimeout", true); //$NON-NLS-1$
        boolean cleanupOnSuspend = JsonUtils.extractBooleanArgument(params, "cleanupOnSuspend", false); //$NON-NLS-1$

        if ((threadId == null || threadId.isEmpty()) && (applicationId == null || applicationId.isEmpty()))
        {
            return ToolResult.error("threadId or applicationId is required") //$NON-NLS-1$
                    .put("reason", "debug_target_required") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        JsonObject breakpointResult = parse(RuntimeDebugBreakpointBridge.setBreakpoint(projectName, modulePath,
                lineNumber, false));
        if (!isSuccess(breakpointResult))
        {
            return breakpointResult.toString();
        }

        JsonObject breakpoint = breakpointResult.getAsJsonObject("breakpoint"); //$NON-NLS-1$
        String breakpointId = breakpoint != null && breakpoint.has("breakpointId") //$NON-NLS-1$
                ? breakpoint.get("breakpointId").getAsString() //$NON-NLS-1$
                : ""; //$NON-NLS-1$
        boolean created = breakpointResult.has("created") && breakpointResult.get("created").getAsBoolean(); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject startResult;
        String startMode;
        if (threadId != null && !threadId.isEmpty())
        {
            startMode = "resume"; //$NON-NLS-1$
            startResult = parse(RuntimeDebugModelBridge.control(null, threadId, "resume")); //$NON-NLS-1$
        }
        else
        {
            startMode = "launch"; //$NON-NLS-1$
            Map<String, String> launchParams = new HashMap<>();
            launchParams.put("projectName", projectName); //$NON-NLS-1$
            launchParams.put("applicationId", applicationId); //$NON-NLS-1$
            launchParams.put("updateBeforeLaunch", Boolean.toString(updateBeforeLaunch)); //$NON-NLS-1$
            startResult = parse(new DebugLaunchTool().execute(launchParams));
        }

        if (!isSuccess(startResult))
        {
            JsonObject cleanup = cleanupTemporaryBreakpoint(created, breakpointId, true);
            return ToolResult.error("Failed to start debug execution") //$NON-NLS-1$
                    .put("reason", "debug_start_failed") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("startMode", startMode) //$NON-NLS-1$
                    .put("breakpoint", breakpointResult) //$NON-NLS-1$
                    .put("startResult", startResult) //$NON-NLS-1$
                    .put("cleanup", cleanup) //$NON-NLS-1$
                    .toJson();
        }

        JsonObject waitResult = parse(RuntimeDebugModelBridge.waitForSuspendedLocation(projectName, applicationId,
                modulePath, lineNumber, timeoutSeconds, maxVariables));
        if (!isSuccess(waitResult))
        {
            JsonObject cleanup = cleanupTemporaryBreakpoint(created, breakpointId, true);
            return ToolResult.error("Failed while waiting for debug breakpoint") //$NON-NLS-1$
                    .put("reason", "debug_wait_failed") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("startMode", startMode) //$NON-NLS-1$
                    .put("breakpoint", breakpointResult) //$NON-NLS-1$
                    .put("startResult", startResult) //$NON-NLS-1$
                    .put("waitResult", waitResult) //$NON-NLS-1$
                    .put("cleanup", cleanup) //$NON-NLS-1$
                    .toJson();
        }
        boolean timedOut = waitResult.has("timedOut") && waitResult.get("timedOut").getAsBoolean(); //$NON-NLS-1$ //$NON-NLS-2$
        boolean shouldCleanup = timedOut ? cleanupOnTimeout : cleanupOnSuspend;
        JsonObject cleanup = cleanupTemporaryBreakpoint(created, breakpointId, shouldCleanup);

        return ToolResult.success()
                .put("outcome", waitResult.has("outcome") ? waitResult.get("outcome").getAsString() : "error") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .put("timedOut", timedOut) //$NON-NLS-1$
                .put("startMode", startMode) //$NON-NLS-1$
                .put("breakpoint", breakpointResult) //$NON-NLS-1$
                .put("startResult", startResult) //$NON-NLS-1$
                .put("waitResult", waitResult) //$NON-NLS-1$
                .put("cleanup", cleanup) //$NON-NLS-1$
                .toJson();
    }

    private static JsonObject cleanupTemporaryBreakpoint(boolean created, String breakpointId, boolean shouldCleanup)
    {
        if (!created)
        {
            JsonObject skipped = new JsonObject();
            skipped.addProperty("status", "skipped"); //$NON-NLS-1$ //$NON-NLS-2$
            skipped.addProperty("reason", "pre_existing_breakpoint"); //$NON-NLS-1$ //$NON-NLS-2$
            return skipped;
        }
        if (!shouldCleanup)
        {
            JsonObject skipped = new JsonObject();
            skipped.addProperty("status", "skipped"); //$NON-NLS-1$ //$NON-NLS-2$
            skipped.addProperty("reason", "cleanup_not_requested"); //$NON-NLS-1$ //$NON-NLS-2$
            skipped.addProperty("breakpointId", breakpointId); //$NON-NLS-1$
            return skipped;
        }
        JsonObject removed = parse(RuntimeDebugBreakpointBridge.removeBreakpoint(breakpointId, false));
        removed.addProperty("status", isSuccess(removed) ? "removed" : "failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return removed;
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static boolean isSuccess(JsonObject object)
    {
        return object != null && object.has("success") && object.get("success").getAsBoolean(); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
