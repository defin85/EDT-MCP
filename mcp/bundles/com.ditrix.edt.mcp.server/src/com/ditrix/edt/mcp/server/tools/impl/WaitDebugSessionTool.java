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
 * Waits for a supported EDT runtime debug session to become visible.
 */
public class WaitDebugSessionTool implements IMcpTool
{
    public static final String NAME = "wait_debug_session"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug launch lifecycle. Wait boundedly for a supported EDT debug session matching projectName and applicationId."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("Wait for runtime debug session"); //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Bounded wait timeout (default 30, max 300)") //$NON-NLS-1$ //$NON-NLS-2$
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
        int timeoutSeconds = JsonUtils.extractIntArgument(params, "timeoutSeconds", 0); //$NON-NLS-1$
        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (applicationId == null || applicationId.isEmpty())
        {
            return ToolResult.error("applicationId is required").toJson(); //$NON-NLS-1$
        }
        return RuntimeDebugLaunchLifecycleBridge.waitForSupportedSession(projectName, applicationId, timeoutSeconds);
    }
}
