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
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GetOperationSnapshotToolTest
{
    @After
    public void tearDown()
    {
        clearTestActivator();
    }

    @Test
    public void testExecuteReturnsFoundSnapshot()
            throws Exception
    {
        McpServer server = new McpServer();
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("op-1", "clean_project", "clean_build", "Running clean build", null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        server.setActiveOperation(reporter);
        installTestActivator(server);

        String json = new GetOperationSnapshotTool().execute(Map.of("operationId", "op-1")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("found").getAsBoolean()); //$NON-NLS-1$
        assertEquals("op-1", payload.get("operationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("clean_project", payload.get("toolName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteReturnsExplicitNotFoundOutcome()
            throws Exception
    {
        installTestActivator(new McpServer());

        String json = new GetOperationSnapshotTool().execute(Map.of("operationId", "missing-op")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("found").getAsBoolean()); //$NON-NLS-1$
        assertEquals("missing-op", payload.get("requestedOperationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
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
