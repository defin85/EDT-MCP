/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRegistry;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionSnapshot;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionTarget;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GetTestSessionStatusToolTest
{
    @After
    public void tearDown()
    {
        clearTestActivator();
    }

    @Test
    public void testExecuteReturnsExplicitNotFoundOutcome()
            throws Exception
    {
        installTestActivator(new McpServer());

        String json = new GetTestSessionStatusTool().execute(Map.of("sessionId", "missing-session")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("found").getAsBoolean()); //$NON-NLS-1$
        assertEquals("missing-session", payload.get("requestedSessionId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteReturnsCurrentSessionSnapshot()
            throws Exception
    {
        McpServer server = new McpServer();
        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        UnitTestSessionSnapshot session = registry.createStartingSession("mcp-session-1", //$NON-NLS-1$
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", "Demo App"), Map.of("pid", 1234)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        registry.markReady(session.getSessionId());
        installTestActivator(server);

        String json = new GetTestSessionStatusTool().execute(Map.of("sessionId", session.getSessionId())); //$NON-NLS-1$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("found").getAsBoolean()); //$NON-NLS-1$
        assertEquals(session.getSessionId(), payload.get("sessionId").getAsString()); //$NON-NLS-1$
        assertEquals("ready", payload.get("state").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("yaxunit", payload.get("provider").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("app-1", payload.getAsJsonObject("reuseScope").get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void installTestActivator(McpServer server)
            throws Exception
    {
        Activator activator = new Activator();
        Field pluginField = Activator.class.getDeclaredField("plugin"); //$NON-NLS-1$
        pluginField.setAccessible(true);
        pluginField.set(null, activator);

        Field serverField = Activator.class.getDeclaredField("mcpServer"); //$NON-NLS-1$
        serverField.setAccessible(true);
        serverField.set(activator, server);
    }

    private void clearTestActivator()
    {
        try
        {
            Field pluginField = Activator.class.getDeclaredField("plugin"); //$NON-NLS-1$
            pluginField.setAccessible(true);
            pluginField.set(null, null);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }
}
