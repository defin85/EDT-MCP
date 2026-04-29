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
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugLaunchLifecycleBridge;

/**
 * Lists launch-level EDT runtime debug lifecycle diagnostics.
 */
public class ListDebugLaunchesTool implements IMcpTool
{
    public static final String NAME = "list_debug_launches"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug launch lifecycle. List RuntimeClient debug launches, processes, lifecycle phases, and unsupported/filter reasons."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("List runtime debug launches"); //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Optional EDT project name filter") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Optional application ID filter") //$NON-NLS-1$ //$NON-NLS-2$
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
        return RuntimeDebugLaunchLifecycleBridge.listLaunches(projectName, applicationId);
    }
}
