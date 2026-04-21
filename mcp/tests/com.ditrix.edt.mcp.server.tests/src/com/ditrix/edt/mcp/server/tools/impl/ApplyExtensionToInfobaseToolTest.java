/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.ditrix.edt.mcp.server.tools.IMcpTool.ResponseType;
import com.ditrix.edt.mcp.server.tools.IMcpTool.TaskSupport;

/**
 * Contract tests for {@link ApplyExtensionToInfobaseTool}.
 */
public class ApplyExtensionToInfobaseToolTest
{
    @Test
    public void testName()
    {
        ApplyExtensionToInfobaseTool tool = new ApplyExtensionToInfobaseTool();
        assertEquals("apply_extension_to_infobase", tool.getName()); //$NON-NLS-1$
    }

    @Test
    public void testResponseType()
    {
        ApplyExtensionToInfobaseTool tool = new ApplyExtensionToInfobaseTool();
        assertEquals(ResponseType.JSON, tool.getResponseType());
    }

    @Test
    public void testTaskSupport()
    {
        ApplyExtensionToInfobaseTool tool = new ApplyExtensionToInfobaseTool();
        assertEquals(TaskSupport.OPTIONAL, tool.getTaskSupport());
    }

    @Test
    public void testInputSchemaContainsRequiredParameters()
    {
        ApplyExtensionToInfobaseTool tool = new ApplyExtensionToInfobaseTool();
        String schema = tool.getInputSchema();

        assertNotNull(schema);
        assertTrue(schema.contains("\"projectName\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"applicationId\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"fullReload\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"autoRestructure\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"required\":[\"projectName\",\"applicationId\"]")); //$NON-NLS-1$
    }

    @Test
    public void testExecuteMissingProjectName()
    {
        ApplyExtensionToInfobaseTool tool = new ApplyExtensionToInfobaseTool();

        Map<String, String> params = new HashMap<>();
        params.put("applicationId", "app-1"); //$NON-NLS-1$ //$NON-NLS-2$

        String result = tool.execute(params);

        assertNotNull(result);
        assertTrue(result.contains("\"success\":false")); //$NON-NLS-1$
        assertTrue(result.contains("projectName is required")); //$NON-NLS-1$
    }

    @Test
    public void testExecuteMissingApplicationId()
    {
        ApplyExtensionToInfobaseTool tool = new ApplyExtensionToInfobaseTool();

        Map<String, String> params = new HashMap<>();
        params.put("projectName", "EXT_001"); //$NON-NLS-1$ //$NON-NLS-2$

        String result = tool.execute(params);

        assertNotNull(result);
        assertTrue(result.contains("\"success\":false")); //$NON-NLS-1$
        assertTrue(result.contains("applicationId is required")); //$NON-NLS-1$
    }
}
