/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.List;
import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugModelBridge;

/**
 * Dispatches basic control actions for supported EDT runtime debug sessions.
 */
public class ControlDebugSessionTool implements IMcpTool
{
    public static final String NAME = "control_debug_session"; //$NON-NLS-1$

    private static final List<String> ACTIONS = List.of("resume", "suspend", "step_over", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "step_into", "step_return", "terminate"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Dispatch a basic control action for a supported EDT runtime debug session or thread."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("sessionId", "Session ID returned by list_debug_sessions") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("threadId", "Thread ID returned by list_debug_sessions") //$NON-NLS-1$ //$NON-NLS-2$
                .stringEnumProperty("action", "Action to dispatch", ACTIONS, true) //$NON-NLS-1$ //$NON-NLS-2$
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
        String sessionId = JsonUtils.extractStringArgument(params, "sessionId"); //$NON-NLS-1$
        String threadId = JsonUtils.extractStringArgument(params, "threadId"); //$NON-NLS-1$
        String action = JsonUtils.extractStringArgument(params, "action"); //$NON-NLS-1$
        return RuntimeDebugModelBridge.control(sessionId, threadId, action);
    }
}
