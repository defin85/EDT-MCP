/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.OperationProgressState;
import com.ditrix.edt.mcp.server.progress.ProgressEvent;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Polling fallback tool for the currently active long-running operation.
 */
public class GetActiveOperationTool implements IMcpTool
{
    public static final String NAME = "get_active_operation"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Get progress snapshot for the currently active long-running operation, if any."; //$NON-NLS-1$
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
    public String execute(Map<String, String> params)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").toJson(); //$NON-NLS-1$
        }

        OperationProgressState operation = server.getActiveOperationSnapshot();
        if (operation == null)
        {
            return ToolResult.success()
                    .put("active", false) //$NON-NLS-1$
                    .put("message", "No active operation") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        ToolResult result = ToolResult.success()
                .put("active", true) //$NON-NLS-1$
                .put("operationId", operation.getOperationId()) //$NON-NLS-1$
                .put("toolName", operation.getToolName()) //$NON-NLS-1$
                .put("status", operation.getStatus()) //$NON-NLS-1$
                .put("stage", operation.getStage()) //$NON-NLS-1$
                .put("message", operation.getMessage()) //$NON-NLS-1$
                .put("indeterminate", operation.isIndeterminate()) //$NON-NLS-1$
                .put("elapsedSeconds", operation.getElapsedSeconds()) //$NON-NLS-1$
                .put("recentEvents", toJsonArray(operation)); //$NON-NLS-1$

        if (operation.getProgress() != null)
        {
            result.put("progress", operation.getProgress()); //$NON-NLS-1$
        }
        if (operation.getTotal() != null)
        {
            result.put("total", operation.getTotal()); //$NON-NLS-1$
        }
        if (operation.getStartedAt() != null)
        {
            result.put("startedAt", operation.getStartedAt().toString()); //$NON-NLS-1$
        }

        return result.toJson();
    }

    private JsonArray toJsonArray(OperationProgressState operation)
    {
        JsonArray events = new JsonArray();
        for (ProgressEvent event : operation.getRecentEvents())
        {
            JsonObject item = new JsonObject();
            if (event.getTimestamp() != null)
            {
                item.addProperty("timestamp", event.getTimestamp().toString()); //$NON-NLS-1$
            }
            if (event.getStage() != null)
            {
                item.addProperty("stage", event.getStage()); //$NON-NLS-1$
            }
            if (event.getMessage() != null)
            {
                item.addProperty("message", event.getMessage()); //$NON-NLS-1$
            }
            if (event.getProgress() != null)
            {
                item.addProperty("progress", event.getProgress()); //$NON-NLS-1$
            }
            if (event.getTotal() != null)
            {
                item.addProperty("total", event.getTotal()); //$NON-NLS-1$
            }
            events.add(item);
        }
        return events;
    }
}
