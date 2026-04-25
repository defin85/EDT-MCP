/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tasks.TaskRecord;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;

/**
 * Bounded wait helper for session-owned MCP tasks.
 */
public class WaitTaskTool implements IMcpTool
{
    public static final String NAME = "wait_task"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: task lifecycle access. Wait for a session-owned task to reach terminal state, bounded by timeoutSeconds."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("taskId", "Task identifier returned by a task-backed tool call.", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Bounded wait timeout in seconds (default 30, max 300).") //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("Wait for task terminal state"); //$NON-NLS-1$
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String taskId = params != null ? params.get("taskId") : null; //$NON-NLS-1$
        if (!TaskLifecycleToolSupport.hasText(taskId))
        {
            return ToolResult.error("taskId is required").toJson(); //$NON-NLS-1$
        }

        McpServer server = TaskLifecycleToolSupport.currentServer();
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").toJson(); //$NON-NLS-1$
        }

        TaskRecord task = TaskLifecycleToolSupport.getTask(server, taskId,
                TaskLifecycleToolSupport.currentSessionId());
        if (task == null)
        {
            return ToolResult.error("Task not found").put("requestedTaskId", taskId).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
        }

        int timeoutSeconds = TaskLifecycleToolSupport
                .normalizeWaitTimeoutSeconds(params != null ? params.get("timeoutSeconds") : null); //$NON-NLS-1$
        boolean terminal;
        try
        {
            terminal = task.awaitTerminal(timeoutSeconds * 1000L);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return ToolResult.error("Interrupted while waiting for task").put("taskId", taskId).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (!terminal)
        {
            return TaskLifecycleToolSupport.addTaskSnapshot(ToolResult.success()
                    .put("outcome", "timeout") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("timeout", true) //$NON-NLS-1$
                    .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                    .put("message", "Task is still active after bounded wait; no background wait continues.") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("preferredFollowUpTool", NAME), //$NON-NLS-1$
                    task, "timeout", NAME).toJson(); //$NON-NLS-1$
        }

        ToolResult result = TaskLifecycleToolSupport.addTaskSnapshot(ToolResult.success()
                .put("outcome", "terminal") //$NON-NLS-1$ //$NON-NLS-2$
                .put("timeout", false) //$NON-NLS-1$
                .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                .put("message", "Task reached a terminal state."), //$NON-NLS-1$ //$NON-NLS-2$
                task, "terminal", null); //$NON-NLS-1$
        return TaskLifecycleToolSupport.addRetainedResult(result, task).toJson();
    }
}
