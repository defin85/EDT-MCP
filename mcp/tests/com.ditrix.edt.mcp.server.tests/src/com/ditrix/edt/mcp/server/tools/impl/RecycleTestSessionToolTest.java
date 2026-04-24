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
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRecycleOutcome;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRegistry;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionSnapshot;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionStaleReason;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionState;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionTarget;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RecycleTestSessionToolTest
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

        String json = new RecycleTestSessionTool().execute(Map.of("sessionId", "missing-session")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("found").getAsBoolean()); //$NON-NLS-1$
        assertEquals("missing-session", payload.get("requestedSessionId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExecuteMarksSessionStaleWhenProviderBridgeIsAbsent()
            throws Exception
    {
        McpServer server = new McpServer();
        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        UnitTestSessionSnapshot session = registry.createStartingSession("mcp-session-1", //$NON-NLS-1$
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), Map.of()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        registry.markReady(session.getSessionId());
        installTestActivator(server);

        String json = new RecycleTestSessionTool().execute(Map.of("sessionId", session.getSessionId())); //$NON-NLS-1$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("marked_stale", payload.get("recycleOutcome").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("stale", payload.get("state").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("explicit_recycle", payload.get("staleReason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(UnitTestSessionState.STALE, registry.get(session.getSessionId()).getState());
        assertEquals(UnitTestSessionStaleReason.EXPLICIT_RECYCLE,
                registry.get(session.getSessionId()).getStaleReason());
    }

    @Test
    public void testExecuteStoresProviderReplacementSession()
            throws Exception
    {
        McpServer server = new McpServer();
        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        UnitTestSessionTarget target = new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        UnitTestSessionSnapshot session = registry.createStartingSession("mcp-session-1", target, Map.of()); //$NON-NLS-1$
        registry.markReady(session.getSessionId());
        UnitTestSessionSnapshot replacement = replacementSession(target);
        server.getUnitTestSessionProviderBridgeRegistry().register(new FakeBridge(replacement));
        installTestActivator(server);

        String json = new RecycleTestSessionTool().execute(Map.of("sessionId", session.getSessionId())); //$NON-NLS-1$

        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("replaced", payload.get("recycleOutcome").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(replacement.getSessionId(), payload.get("replacementSessionId").getAsString()); //$NON-NLS-1$
        assertEquals("ready", payload.getAsJsonObject("replacementSession").get("state").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals(UnitTestSessionState.STALE, registry.get(session.getSessionId()).getState());
        assertNotNull(registry.get(replacement.getSessionId()));
    }

    private static UnitTestSessionSnapshot replacementSession(UnitTestSessionTarget target)
    {
        Instant now = Instant.now();
        return new UnitTestSessionSnapshot("replacement-session", "mcp-session-1", target, //$NON-NLS-1$ //$NON-NLS-2$
                UnitTestSessionState.READY, null, null, now, now, now, now.plusSeconds(60L), Map.of("pid", 5678)); //$NON-NLS-1$
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
        private final UnitTestSessionSnapshot replacement;

        private FakeBridge(UnitTestSessionSnapshot replacement)
        {
            this.replacement = replacement;
        }

        @Override
        public String getProvider()
        {
            return "yaxunit"; //$NON-NLS-1$
        }

        @Override
        public PrepareResult prepare(PrepareRequest request)
        {
            return PrepareResult.failed(ToolResult.error("not implemented")); //$NON-NLS-1$
        }

        @Override
        public ExecuteResult execute(ExecuteRequest request)
        {
            return ExecuteResult.failed(ToolResult.error("not implemented")); //$NON-NLS-1$
        }

        @Override
        public RecycleResult recycle(RecycleRequest request)
        {
            return RecycleResult.recycled(UnitTestSessionRecycleOutcome.REPLACED, request.getCurrentSnapshot(),
                    replacement);
        }
    }
}
