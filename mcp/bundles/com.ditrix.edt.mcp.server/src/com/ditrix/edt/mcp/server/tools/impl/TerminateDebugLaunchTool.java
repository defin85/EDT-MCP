/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugLaunchLifecycleBridge;

/**
 * Terminates a matching RuntimeClient debug launch through Eclipse debug model only.
 */
public class TerminateDebugLaunchTool implements IMcpTool
{
    public static final String NAME = "terminate_debug_launch"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug launch lifecycle. Terminate a matching RuntimeClient debug launch through Eclipse launch/process termination only."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Terminate runtime debug launch") //$NON-NLS-1$
                .readOnlyHint(false)
                .destructiveHint(true)
                .idempotentHint(false)
                .openWorldHint(true)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("launchId", "Optional launchId returned by list_debug_launches") //$NON-NLS-1$ //$NON-NLS-2$
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
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        String launchId = JsonUtils.extractStringArgument(params, "launchId"); //$NON-NLS-1$
        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (applicationId == null || applicationId.isEmpty())
        {
            return ToolResult.error("applicationId is required").toJson(); //$NON-NLS-1$
        }
        return RuntimeDebugLaunchLifecycleBridge.terminateLaunch(projectName, applicationId, launchId);
    }
}
