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
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionSnapshot;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionToolContract;
import com.ditrix.edt.mcp.server.tools.IMcpTool;

/**
 * Read-only inspection for persistent unit-test session state.
 */
public class GetTestSessionStatusTool implements IMcpTool
{
    public static final String NAME = UnitTestSessionToolContract.TOOL_GET_TEST_SESSION_STATUS;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Inspect persistent unit-test session lifecycle state by stable sessionId."; //$NON-NLS-1$
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

        UnitTestSessionSnapshot snapshot = server.getUnitTestSessionRegistry().get(sessionId);
        if (snapshot == null)
        {
            return ToolResult.success()
                    .put("found", false) //$NON-NLS-1$
                    .put("requestedSessionId", sessionId) //$NON-NLS-1$
                    .put("message", "Test session not found or expired: " + sessionId) //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        ToolResult result = ToolResult.success().put("found", true); //$NON-NLS-1$
        snapshot.toPublicMap().forEach((key, value) -> result.put(key, value));
        return result.toJson();
    }
}
