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
 * Exact polling tool for tracked long-running operations by stable operation id.
 */
public class GetOperationSnapshotTool implements IMcpTool
{
    public static final String NAME = "get_operation_snapshot"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Get progress snapshot for a tracked long-running operation by operationId."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("operationId", "Stable operation identifier returned by progress/task metadata.", true) //$NON-NLS-1$ //$NON-NLS-2$
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
        String operationId = params != null ? params.get("operationId") : null; //$NON-NLS-1$
        if (!hasText(operationId))
        {
            return ToolResult.error("operationId is required").toJson(); //$NON-NLS-1$
        }

        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").toJson(); //$NON-NLS-1$
        }

        OperationProgressState operation = server.getOperationSnapshot(operationId);
        if (operation == null)
        {
            return ToolResult.success()
                    .put("found", false) //$NON-NLS-1$
                    .put("requestedOperationId", operationId) //$NON-NLS-1$
                    .put("message", "Operation not found: " + operationId) //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        return OperationSnapshotPayloads.addSnapshot(ToolResult.success().put("found", true), operation) //$NON-NLS-1$
                .toJson();
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
