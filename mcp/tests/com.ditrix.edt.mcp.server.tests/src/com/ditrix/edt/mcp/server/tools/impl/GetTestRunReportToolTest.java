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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.testruns.UnitTestRunRecord;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GetTestRunReportToolTest
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

        String json = new GetTestRunReportTool().execute(Map.of("runId", "missing-run")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("found").getAsBoolean()); //$NON-NLS-1$
        assertEquals("missing-run", payload.get("requestedRunId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteReturnsRetainedJUnitReport()
            throws Exception
    {
        McpServer server = new McpServer();
        server.getUnitTestRunStore().put(new UnitTestRunRecord("run-1", "yaxunit", "TestConfiguration", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "app-1", "Demo", "all", "failed", "boom", 2, 1, 1, 0, 0, 1234L, Instant.now(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                Instant.now().plusSeconds(60),
                List.of(UnitTestRunRecord.FORMAT_SUMMARY, UnitTestRunRecord.FORMAT_JUNIT), //$NON-NLS-1$
                List.of("tests.Module.Fail"), Map.of("scope", "all"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "<testsuite tests=\"2\" failures=\"1\"/>")); //$NON-NLS-1$
        installTestActivator(server);

        String json = new GetTestRunReportTool().execute(Map.of("runId", "run-1", "format", "junit")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("found").getAsBoolean()); //$NON-NLS-1$
        assertEquals("junit", payload.get("format").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(payload.get("report").getAsString().contains("testsuite")); //$NON-NLS-1$ //$NON-NLS-2$
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
