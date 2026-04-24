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
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugModelBridge;

/**
 * Reads variables for a suspended EDT runtime stack frame.
 */
public class GetDebugVariablesTool implements IMcpTool
{
    public static final String NAME = "get_debug_variables"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug control. Get bounded variables for a suspended frameId returned by get_debug_stack."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("Read runtime debug variables"); //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("frameId", "Frame ID returned by get_debug_stack", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringArrayProperty("variablePath", "Optional variable path to expand from the frame") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("maxVariables", "Maximum number of variables to return (default 100, max 200)") //$NON-NLS-1$ //$NON-NLS-2$
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
        String frameId = JsonUtils.extractStringArgument(params, "frameId"); //$NON-NLS-1$
        List<String> variablePath = JsonUtils.extractArrayArgument(params, "variablePath"); //$NON-NLS-1$
        int maxVariables = JsonUtils.extractIntArgument(params, "maxVariables", 0); //$NON-NLS-1$
        return RuntimeDebugModelBridge.getVariables(frameId, variablePath, maxVariables);
    }
}
