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
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugBreakpointBridge;

/**
 * Lists supported EDT BSL line breakpoints.
 */
public class ListDebugBreakpointsTool implements IMcpTool
{
    public static final String NAME = "list_debug_breakpoints"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "List supported EDT BSL line breakpoints visible to the Eclipse breakpoint manager."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Optional EDT project name filter") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("modulePath", "Optional BSL module path relative to src") //$NON-NLS-1$ //$NON-NLS-2$
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
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String modulePath = JsonUtils.extractStringArgument(params, "modulePath"); //$NON-NLS-1$
        return RuntimeDebugBreakpointBridge.listBreakpoints(projectName, modulePath);
    }
}
