/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugModelBridge;

/**
 * Reads stack frames for a suspended EDT runtime debug thread.
 */
public class GetDebugStackTool implements IMcpTool
{
    public static final String NAME = "get_debug_stack"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Get stack frames for a suspended thread in a supported EDT runtime debug session."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("threadId", "Thread ID returned by list_debug_sessions", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("maxFrames", "Maximum number of stack frames to return (default 100, max 200)") //$NON-NLS-1$ //$NON-NLS-2$
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
        String threadId = JsonUtils.extractStringArgument(params, "threadId"); //$NON-NLS-1$
        int maxFrames = JsonUtils.extractIntArgument(params, "maxFrames", 0); //$NON-NLS-1$
        return RuntimeDebugModelBridge.getStack(threadId, maxFrames);
    }
}
