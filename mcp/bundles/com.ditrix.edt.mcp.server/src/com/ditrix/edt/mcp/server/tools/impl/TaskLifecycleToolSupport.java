/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.TaskInfo;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.TaskLifecycleEnvelope;
import com.ditrix.edt.mcp.server.tasks.TaskRecord;
import com.ditrix.edt.mcp.server.tasks.TaskRegistry;
import com.ditrix.edt.mcp.server.tasks.TaskResultEnvelope;
import com.google.gson.JsonObject;

/**
 * Shared support for tool-level task lifecycle wrappers.
 */
final class TaskLifecycleToolSupport
{
    static final int DEFAULT_WAIT_TIMEOUT_SECONDS = 30;
    static final int MAX_WAIT_TIMEOUT_SECONDS = 300;

    private TaskLifecycleToolSupport()
    {
        // Utility class.
    }

    static McpServer currentServer()
    {
        return Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
    }

    static String currentSessionId()
    {
        ToolExecutionContext context = ToolExecutionContextHolder.get();
        return context != null ? context.getSessionId() : null;
    }

    static TaskRegistry.ListPage listTasks(McpServer server, String sessionId, String cursor, int limit)
    {
        return server.getTaskRegistry().listTasks(sessionId, cursor, Integer.valueOf(limit));
    }

    static TaskRecord getTask(McpServer server, String taskId, String sessionId)
    {
        if (!hasText(taskId))
        {
            return null;
        }
        return server.getTaskRegistry().getTask(taskId, sessionId);
    }

    static ToolResult addTaskSnapshot(ToolResult result, TaskRecord task, String outcome, String followUpTool)
    {
        return result.put("task", new TaskInfo(task)) //$NON-NLS-1$
                .put("lifecycle", new TaskLifecycleEnvelope(task, outcome, followUpTool)) //$NON-NLS-1$
                .put("taskId", task.getTaskId()) //$NON-NLS-1$
                .put("state", task.getStatus().getWireValue()) //$NON-NLS-1$
                .put("terminal", task.getStatus().isTerminal()) //$NON-NLS-1$
                .put("resultAvailable", task.getResultEnvelope() != null); //$NON-NLS-1$
    }

    static ToolResult addRetainedResult(ToolResult result, TaskRecord task)
    {
        TaskResultEnvelope envelope = task.getResultEnvelope();
        if (envelope == null)
        {
            return result.put("result", Map.of("available", Boolean.FALSE)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (envelope.getError() != null)
        {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", Integer.valueOf(envelope.getError().getCode())); //$NON-NLS-1$
            error.put("message", envelope.getError().getMessage()); //$NON-NLS-1$
            return result.put("result", Map.of( //$NON-NLS-1$
                    "available", Boolean.TRUE, //$NON-NLS-1$
                    "kind", "jsonRpcError", //$NON-NLS-1$ //$NON-NLS-2$
                    "error", error)); //$NON-NLS-1$
        }

        return result.put("result", Map.of( //$NON-NLS-1$
                "available", Boolean.TRUE, //$NON-NLS-1$
                "kind", "toolPayload")) //$NON-NLS-1$ //$NON-NLS-2$
                .put("toolPayload", envelope.getResult()) //$NON-NLS-1$
                .putMeta(McpConstants.META_RELATED_TASK, relatedTask(task));
    }

    static int normalizeLimit(String rawLimit)
    {
        return normalizeInteger(rawLimit, 20, 1, 100);
    }

    static int normalizeWaitTimeoutSeconds(String rawTimeout)
    {
        return normalizeInteger(rawTimeout, DEFAULT_WAIT_TIMEOUT_SECONDS, 0, MAX_WAIT_TIMEOUT_SECONDS);
    }

    static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private static int normalizeInteger(String value, int defaultValue, int min, int max)
    {
        if (!hasText(value))
        {
            return defaultValue;
        }
        try
        {
            double parsed = Double.parseDouble(value.trim());
            if (parsed != Math.floor(parsed) || parsed < min)
            {
                return defaultValue;
            }
            return Math.min(max, (int) parsed);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private static JsonObject relatedTask(TaskRecord task)
    {
        JsonObject relatedTask = new JsonObject();
        relatedTask.addProperty("taskId", task.getTaskId()); //$NON-NLS-1$
        return relatedTask;
    }
}
