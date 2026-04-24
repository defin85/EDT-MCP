/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionProviderBridge;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRegistry;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionSnapshot;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionState;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionTarget;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionToolContract;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class PrepareTestSessionToolTest
{
    @After
    public void tearDown()
    {
        clearTestActivator();
    }

    @Test
    public void testExecuteFailsClosedWhenProviderBridgeIsAbsent()
            throws Exception
    {
        installTestActivator(new McpServer());

        String json = new PrepareTestSessionTool().execute(Map.of("projectName", "Demo", "applicationId", "app-1")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("yaxunit", payload.get("provider").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteAttachesReusableExistingSession()
            throws Exception
    {
        McpServer server = new McpServer();
        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        UnitTestSessionSnapshot session = registry.createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), Map.of()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        registry.markReady(session.getSessionId());
        installTestActivator(server);

        String json = new PrepareTestSessionTool().execute(Map.of("projectName", "Demo", "applicationId", "app-1")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("attached").getAsBoolean()); //$NON-NLS-1$
        assertEquals(session.getSessionId(), payload.get("sessionId").getAsString()); //$NON-NLS-1$
        assertEquals("ready", payload.get("state").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecutePreparesSessionThroughProviderBridge()
            throws Exception
    {
        McpServer server = new McpServer();
        server.getUnitTestSessionProviderBridgeRegistry().register(new FakeBridge());
        installTestActivator(server);

        String json = new PrepareTestSessionTool().execute(Map.of("projectName", "Demo", "applicationId", "app-1")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("attached").getAsBoolean()); //$NON-NLS-1$
        assertEquals("ready", payload.get("state").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull(server.getUnitTestSessionRegistry().get(payload.get("sessionId").getAsString())); //$NON-NLS-1$
        assertEquals(UnitTestSessionState.READY,
                server.getUnitTestSessionRegistry().get(payload.get("sessionId").getAsString()).getState()); //$NON-NLS-1$
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

    private static final class FakeBridge implements UnitTestSessionProviderBridge
    {
        @Override
        public String getProvider()
        {
            return "yaxunit"; //$NON-NLS-1$
        }

        @Override
        public PrepareResult prepare(PrepareRequest request)
        {
            String sessionId = String.valueOf(request.getProviderParameters()
                    .get(UnitTestSessionToolContract.FIELD_SESSION_ID));
            Instant now = Instant.now();
            UnitTestSessionSnapshot snapshot = new UnitTestSessionSnapshot(sessionId, request.getOwnerSessionId(),
                    request.getTarget(), UnitTestSessionState.READY, null, null, now, now, now, now.plusSeconds(60L),
                    Map.of("pid", 1234)); //$NON-NLS-1$
            return PrepareResult.prepared(snapshot);
        }

        @Override
        public ExecuteResult execute(ExecuteRequest request)
        {
            return ExecuteResult.failed(ToolResult.error("not implemented")); //$NON-NLS-1$
        }

        @Override
        public RecycleResult recycle(RecycleRequest request)
        {
            return RecycleResult.failed(ToolResult.error("not implemented")); //$NON-NLS-1$
        }
    }
}
