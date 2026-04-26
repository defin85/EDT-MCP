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
 * Removes MCP-owned EDT BSL line breakpoints while preserving user breakpoints.
 */
public class CleanupMcpDebugBreakpointsTool implements IMcpTool
{
    public static final String NAME = "cleanup_mcp_debug_breakpoints"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug control. Remove MCP-owned BSL breakpoints only; preserves user-owned breakpoints and reports skipped protected entries."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Clean up MCP debug breakpoints") //$NON-NLS-1$
                .readOnlyHint(false)
                .destructiveHint(true)
                .idempotentHint(true)
                .openWorldHint(false)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Optional EDT project name filter") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("modulePath", "Optional BSL module path relative to src") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("dryRun", "Report matching breakpoints without deleting them (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
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
        boolean dryRun = JsonUtils.extractBooleanArgument(params, "dryRun", false); //$NON-NLS-1$
        return RuntimeDebugBreakpointBridge.cleanupMcpBreakpoints(projectName, modulePath, dryRun);
    }
}
