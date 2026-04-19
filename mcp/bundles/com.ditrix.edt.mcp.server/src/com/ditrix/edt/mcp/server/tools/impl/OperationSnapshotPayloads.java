/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import com.ditrix.edt.mcp.server.progress.OperationProgressState;
import com.ditrix.edt.mcp.server.progress.ProgressEvent;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Shared serialization helpers for operation snapshot tools.
 */
final class OperationSnapshotPayloads
{
    private OperationSnapshotPayloads()
    {
        // Utility class
    }

    static ToolResult addSnapshot(ToolResult result, OperationProgressState operation)
    {
        result.put("operationId", operation.getOperationId()) //$NON-NLS-1$
                .put("toolName", operation.getToolName()) //$NON-NLS-1$
                .put("status", operation.getStatus()) //$NON-NLS-1$
                .put("detached", operation.isDetached()) //$NON-NLS-1$
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
        if (!operation.getDetails().isEmpty())
        {
            result.put("details", operation.getDetails()); //$NON-NLS-1$
        }
        return result;
    }

    private static JsonArray toJsonArray(OperationProgressState operation)
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
