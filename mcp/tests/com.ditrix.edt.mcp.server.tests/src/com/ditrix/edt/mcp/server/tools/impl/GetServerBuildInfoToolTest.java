/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.osgi.framework.Version;

import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GetServerBuildInfoToolTest
{
    @Test
    public void testExecuteReturnsStructuredBuildInfo()
    {
        String json = new GetServerBuildInfoTool().execute(Map.of());

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(McpConstants.SERVER_NAME, payload.get("serverName").getAsString()); //$NON-NLS-1$
        assertEquals(McpConstants.PLUGIN_VERSION, payload.get("pluginVersion").getAsString()); //$NON-NLS-1$
        assertEquals(McpConstants.PROTOCOL_VERSION, payload.get("protocolVersion").getAsString()); //$NON-NLS-1$
        assertTrue(payload.get("edtVersion").getAsString().length() > 0); //$NON-NLS-1$
        assertTrue(payload.get("bundleSymbolicName").getAsString().length() > 0); //$NON-NLS-1$
        assertTrue(payload.get("bundleVersion").getAsString().length() > 0); //$NON-NLS-1$
        assertTrue(payload.get("buildQualifier").getAsString().length() > 0); //$NON-NLS-1$
    }

    @Test
    public void testBuildToolResultUsesExactBundleVersionAndQualifier()
    {
        Version version = new Version(1, 2, 3, "v202604211530"); //$NON-NLS-1$

        JsonObject payload = JsonParser.parseString(GetServerBuildInfoTool
                .buildToolResult("com.ditrix.edt.mcp.server", version, "2026.1.0 (2026.1.0.123)") //$NON-NLS-1$ //$NON-NLS-2$
                .toJson()).getAsJsonObject();

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("com.ditrix.edt.mcp.server", payload.get("bundleSymbolicName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("1.2.3.v202604211530", payload.get("bundleVersion").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("v202604211530", payload.get("buildQualifier").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse("Short plugin version should remain distinct from exact bundle version",
                payload.get("pluginVersion").getAsString().equals(payload.get("bundleVersion").getAsString())); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testBuildToolResultFallsBackToUnknownWithoutRuntimeBundleMetadata()
    {
        JsonObject payload = JsonParser.parseString(GetServerBuildInfoTool.buildToolResult(null, null, null).toJson())
                .getAsJsonObject();

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("unknown", payload.get("bundleSymbolicName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("unknown", payload.get("bundleVersion").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("unknown", payload.get("buildQualifier").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Unknown", payload.get("edtVersion").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
