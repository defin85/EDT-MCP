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
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;

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

        return OperationSnapshotPayloads.addSnapshot(ToolResult.success().put("active", true), operation) //$NON-NLS-1$
                .toJson();
    }
}
