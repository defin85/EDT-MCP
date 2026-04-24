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
 * Creates a supported EDT BSL line breakpoint.
 */
public class SetDebugBreakpointTool implements IMcpTool
{
    public static final String NAME = "set_debug_breakpoint"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Set a supported EDT BSL line breakpoint by project, module path, and 1-based line number."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("modulePath", "BSL module path relative to src", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("lineNumber", "1-based source line number", true) //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("persisted", //$NON-NLS-1$
                        "Persist the breakpoint in the EDT workspace; defaults to false for MCP-created breakpoints")
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
        int lineNumber = JsonUtils.extractIntArgument(params, "lineNumber", -1); //$NON-NLS-1$
        boolean persisted = JsonUtils.extractBooleanArgument(params, "persisted", false); //$NON-NLS-1$
        return RuntimeDebugBreakpointBridge.setBreakpoint(projectName, modulePath, lineNumber, persisted);
    }
}
