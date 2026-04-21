/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;

/**
 * Discovery tool that exposes the exact installed EDT-MCP runtime build metadata.
 */
public class GetServerBuildInfoTool implements IMcpTool
{
    public static final String NAME = "get_server_build_info"; //$NON-NLS-1$

    private static final String UNKNOWN = "unknown"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Get exact EDT-MCP server build information"; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object().build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        Bundle bundle = FrameworkUtil.getBundle(GetServerBuildInfoTool.class);
        return buildToolResult(bundle != null ? bundle.getSymbolicName() : null,
                bundle != null ? bundle.getVersion() : null, GetEdtVersionTool.getEdtVersion()).toJson();
    }

    static ToolResult buildToolResult(String bundleSymbolicName, Version bundleVersion, String edtVersion)
    {
        return ToolResult.success()
                .put("serverName", McpConstants.SERVER_NAME) //$NON-NLS-1$
                .put("bundleSymbolicName", hasText(bundleSymbolicName) ? bundleSymbolicName : UNKNOWN) //$NON-NLS-1$
                .put("bundleVersion", bundleVersion != null ? bundleVersion.toString() : UNKNOWN) //$NON-NLS-1$
                .put("buildQualifier", getQualifier(bundleVersion)) //$NON-NLS-1$
                .put("pluginVersion", McpConstants.PLUGIN_VERSION) //$NON-NLS-1$
                .put("protocolVersion", McpConstants.PROTOCOL_VERSION) //$NON-NLS-1$
                .put("edtVersion", hasText(edtVersion) ? edtVersion : "Unknown"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String getQualifier(Version bundleVersion)
    {
        if (bundleVersion == null)
        {
            return UNKNOWN;
        }
        String qualifier = bundleVersion.getQualifier();
        return hasText(qualifier) ? qualifier : UNKNOWN;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
