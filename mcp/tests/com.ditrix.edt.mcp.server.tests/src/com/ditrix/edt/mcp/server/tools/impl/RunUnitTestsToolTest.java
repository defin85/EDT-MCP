/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RunUnitTestsToolTest
{
    @Test
    public void testInputSchemaAdvertisesProviderAndScopeEnums()
    {
        JsonObject schema = JsonParser.parseString(new RunUnitTestsTool().getInputSchema()).getAsJsonObject();
        JsonObject properties = schema.getAsJsonObject("properties"); //$NON-NLS-1$

        assertTrue(properties.getAsJsonObject("provider").getAsJsonArray("enum").toString().contains("yaxunit")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(properties.getAsJsonObject("scope").getAsJsonArray("enum").toString().contains("module")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(properties.getAsJsonObject("sessionMode").getAsJsonArray("enum").toString() //$NON-NLS-1$ //$NON-NLS-2$
                .contains("require_warm")); //$NON-NLS-1$
        assertTrue(properties.getAsJsonObject("sessionMode").getAsJsonArray("enum").toString() //$NON-NLS-1$ //$NON-NLS-2$
                .contains("recycle_then_run")); //$NON-NLS-1$
    }

    @Test
    public void testExecuteRejectsUnsupportedProvider()
    {
        String json = new RunUnitTestsTool().execute(Map.of("projectName", "TestConfiguration", "applicationId", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "app-1", "provider", "vanessa")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(!payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("vanessa", payload.get("provider").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteFailsClosedWhenTagsExcludeRequested()
    {
        String json = new RunUnitTestsTool().execute(Map.of("projectName", "TestConfiguration", "applicationId", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "app-1", "tagsExclude", "[\"slow\"]")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(!payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("error").getAsString().contains("tagsExclude")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteRejectsUnsupportedSessionModeBeforeRuntimeResolution()
    {
        String json = new RunUnitTestsTool().execute(Map.of("projectName", "TestConfiguration", "applicationId", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "app-1", "sessionMode", "fast")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(!payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("error").getAsString().contains("sessionMode")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(payload.getAsJsonArray("supportedSessionModes").toString().contains("prefer_warm")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteRequiresScopeSpecificFilter()
    {
        String json = new RunUnitTestsTool().execute(Map.of("projectName", "TestConfiguration", "applicationId", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "app-1", "scope", "module")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(!payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("error").getAsString().contains("testModule")); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
