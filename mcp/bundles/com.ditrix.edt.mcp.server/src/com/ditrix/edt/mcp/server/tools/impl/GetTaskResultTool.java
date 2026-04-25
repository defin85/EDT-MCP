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
 * Tool-level retained result lookup for session-owned MCP tasks.
 */
public class GetTaskResultTool implements IMcpTool
{
    public static final String NAME = "get_task_result"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: task lifecycle access. Get a retained task result or the latest task snapshot without re-running the original operation."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("taskId", "Task identifier returned by a task-backed tool call.", true) //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("Get task result"); //$NON-NLS-1$
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

        if (!task.getStatus().isTerminal())
        {
            return TaskLifecycleToolSupport.addTaskSnapshot(ToolResult.success()
                    .put("outcome", "active") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("message", "Task is not terminal yet; use wait_task or retry get_task_result."), //$NON-NLS-1$ //$NON-NLS-2$
                    task, "active", WaitTaskTool.NAME).toJson(); //$NON-NLS-1$
        }

        ToolResult result = TaskLifecycleToolSupport.addTaskSnapshot(ToolResult.success()
                .put("outcome", "terminal") //$NON-NLS-1$ //$NON-NLS-2$
                .put("message", "Task reached a terminal state."), //$NON-NLS-1$ //$NON-NLS-2$
                task, "terminal", null); //$NON-NLS-1$
        return TaskLifecycleToolSupport.addRetainedResult(result, task).toJson();
    }
}
