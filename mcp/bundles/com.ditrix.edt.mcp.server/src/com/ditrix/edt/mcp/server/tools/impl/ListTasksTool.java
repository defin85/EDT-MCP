/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.TaskInfo;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.TaskLifecycleEnvelope;
import com.ditrix.edt.mcp.server.tasks.TaskRecord;
import com.ditrix.edt.mcp.server.tasks.TaskRegistry;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;

/**
 * Tool-level view over session-visible MCP tasks.
 */
public class ListTasksTool implements IMcpTool
{
    public static final String NAME = "list_tasks"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: task lifecycle access. List tasks visible to the current MCP session."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("cursor", "Opaque cursor returned by a previous list_tasks call.") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("limit", "Maximum number of task summaries to return (default 20, max 100).") //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("List session tasks"); //$NON-NLS-1$
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        McpServer server = TaskLifecycleToolSupport.currentServer();
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").toJson(); //$NON-NLS-1$
        }

        String sessionId = TaskLifecycleToolSupport.currentSessionId();
        String cursor = params != null ? params.get("cursor") : null; //$NON-NLS-1$
        int limit = TaskLifecycleToolSupport.normalizeLimit(params != null ? params.get("limit") : null); //$NON-NLS-1$
        TaskRegistry.ListPage page = TaskLifecycleToolSupport.listTasks(server, sessionId, cursor, limit);

        List<TaskInfo> tasks = new ArrayList<>();
        List<TaskLifecycleEnvelope> lifecycles = new ArrayList<>();
        for (TaskRecord task : page.getTasks())
        {
            tasks.add(new TaskInfo(task));
            lifecycles.add(new TaskLifecycleEnvelope(task));
        }

        ToolResult result = ToolResult.success()
                .put("tasks", tasks) //$NON-NLS-1$
                .put("lifecycles", lifecycles) //$NON-NLS-1$
                .put("count", tasks.size()); //$NON-NLS-1$
        if (page.getNextCursor() != null)
        {
            result.put("nextCursor", page.getNextCursor()); //$NON-NLS-1$
        }
        return result.toJson();
    }
}
