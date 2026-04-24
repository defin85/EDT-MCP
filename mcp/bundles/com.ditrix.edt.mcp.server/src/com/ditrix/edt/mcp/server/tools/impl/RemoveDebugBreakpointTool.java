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
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugBreakpointBridge;

/**
 * Removes a supported EDT BSL line breakpoint by MCP identifier.
 */
public class RemoveDebugBreakpointTool implements IMcpTool
{
    public static final String NAME = "remove_debug_breakpoint"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug control. Remove a supported BSL breakpoint by breakpointId; protects pre-existing user breakpoints by default."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Remove BSL debug breakpoint") //$NON-NLS-1$
                .readOnlyHint(false)
                .destructiveHint(true)
                .openWorldHint(false)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("breakpointId", "Breakpoint identifier returned by list/set", true) //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("removeUserBreakpoint", //$NON-NLS-1$
                        "Allow removal of a pre-existing breakpoint not created by MCP") //$NON-NLS-1$
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
        String breakpointId = JsonUtils.extractStringArgument(params, "breakpointId"); //$NON-NLS-1$
        boolean removeUserBreakpoint = JsonUtils.extractBooleanArgument(params, "removeUserBreakpoint", false); //$NON-NLS-1$
        return RuntimeDebugBreakpointBridge.removeBreakpoint(breakpointId, removeUserBreakpoint);
    }
}
