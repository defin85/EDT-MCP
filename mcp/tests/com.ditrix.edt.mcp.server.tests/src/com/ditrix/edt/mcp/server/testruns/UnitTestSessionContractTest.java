/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.Test;

import com.ditrix.edt.mcp.server.protocol.GsonProvider;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UnitTestSessionContractTest
{
    @Test
    public void testLifecycleStatesExposeStableWireValues()
    {
        assertEquals("starting", UnitTestSessionState.STARTING.wireValue()); //$NON-NLS-1$
        assertEquals("ready", UnitTestSessionState.READY.wireValue()); //$NON-NLS-1$
        assertEquals("busy", UnitTestSessionState.BUSY.wireValue()); //$NON-NLS-1$
        assertEquals("stale", UnitTestSessionState.STALE.wireValue()); //$NON-NLS-1$
        assertEquals("dead", UnitTestSessionState.DEAD.wireValue()); //$NON-NLS-1$
        assertEquals(UnitTestSessionState.STALE, UnitTestSessionState.fromWireValue("stale")); //$NON-NLS-1$
        assertNull(UnitTestSessionState.fromWireValue("unknown")); //$NON-NLS-1$
    }

    @Test
    public void testToolNamesExposeStableWireValues()
    {
        assertEquals("prepare_test_session", UnitTestSessionToolContract.TOOL_PREPARE_TEST_SESSION); //$NON-NLS-1$
        assertEquals("get_test_session_status", UnitTestSessionToolContract.TOOL_GET_TEST_SESSION_STATUS); //$NON-NLS-1$
        assertEquals("recycle_test_session", UnitTestSessionToolContract.TOOL_RECYCLE_TEST_SESSION); //$NON-NLS-1$
    }

    @Test
    public void testRunSessionModeWireValues()
    {
        assertEquals("cold", UnitTestSessionMode.COLD.wireValue()); //$NON-NLS-1$
        assertEquals("prefer_warm", UnitTestSessionMode.PREFER_WARM.wireValue()); //$NON-NLS-1$
        assertEquals("require_warm", UnitTestSessionMode.REQUIRE_WARM.wireValue()); //$NON-NLS-1$
        assertEquals("recycle_then_run", UnitTestSessionMode.RECYCLE_THEN_RUN.wireValue()); //$NON-NLS-1$
        assertEquals(UnitTestSessionMode.COLD, UnitTestSessionMode.fromWireValue(null));
        assertEquals(UnitTestSessionMode.PREFER_WARM, UnitTestSessionMode.fromWireValue("PREFER_WARM")); //$NON-NLS-1$
        assertNull(UnitTestSessionMode.fromWireValue("fast")); //$NON-NLS-1$
        assertFalse(UnitTestSessionMode.REQUIRE_WARM.allowsColdFallback());
        assertTrue(UnitTestSessionMode.REQUIRE_WARM.requiresReusableWarmSession());
    }

    @Test
    public void testRunAndRecycleOutcomeWireValues()
    {
        assertEquals("cold_started", UnitTestSessionRunOutcome.COLD_STARTED.wireValue()); //$NON-NLS-1$
        assertEquals("reused", UnitTestSessionRunOutcome.REUSED.wireValue()); //$NON-NLS-1$
        assertEquals("recycled", UnitTestSessionRunOutcome.RECYCLED.wireValue()); //$NON-NLS-1$
        assertEquals("stale_rejected", UnitTestSessionRunOutcome.STALE_REJECTED.wireValue()); //$NON-NLS-1$
        assertEquals("marked_stale", UnitTestSessionRecycleOutcome.MARKED_STALE.wireValue()); //$NON-NLS-1$
        assertEquals("terminated", UnitTestSessionRecycleOutcome.TERMINATED.wireValue()); //$NON-NLS-1$
        assertEquals("replaced", UnitTestSessionRecycleOutcome.REPLACED.wireValue()); //$NON-NLS-1$
    }

    @Test
    public void testStaleReasonsExposeStableWireValues()
    {
        assertEquals("workspace_changed", UnitTestSessionStaleReason.WORKSPACE_CHANGED.wireValue()); //$NON-NLS-1$
        assertEquals("runtime_target_changed", UnitTestSessionStaleReason.RUNTIME_TARGET_CHANGED.wireValue()); //$NON-NLS-1$
        assertEquals("infobase_sync_performed", UnitTestSessionStaleReason.INFOBASE_SYNC_PERFORMED.wireValue()); //$NON-NLS-1$
        assertEquals("heartbeat_lost", UnitTestSessionStaleReason.HEARTBEAT_LOST.wireValue()); //$NON-NLS-1$
        assertEquals("explicit_recycle", UnitTestSessionStaleReason.EXPLICIT_RECYCLE.wireValue()); //$NON-NLS-1$
        assertEquals("provider_error", UnitTestSessionStaleReason.PROVIDER_ERROR.wireValue()); //$NON-NLS-1$
        assertEquals(UnitTestSessionStaleReason.PROVIDER_ERROR,
                UnitTestSessionStaleReason.fromWireValue("provider_error")); //$NON-NLS-1$
        assertNull(UnitTestSessionStaleReason.fromWireValue("unknown")); //$NON-NLS-1$
    }

    @Test
    public void testTargetIdentityDefinesReuseScope()
    {
        UnitTestSessionTarget target = new UnitTestSessionTarget("YAXUNIT", "Demo", "app-1", "Demo App"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        assertEquals("yaxunit", target.getProvider()); //$NON-NLS-1$
        assertTrue(target.matchesReuseScope(new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertFalse(target.matchesReuseScope(new UnitTestSessionTarget("yaxunit", "Demo", "app-2", null))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testSnapshotPublishesSessionIdentityOwnershipAndReuseScope()
    {
        Instant createdAt = Instant.parse("2026-04-24T07:00:00Z"); //$NON-NLS-1$
        UnitTestSessionSnapshot snapshot = new UnitTestSessionSnapshot("session-1", "mcp-session-1", //$NON-NLS-1$ //$NON-NLS-2$
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", "Demo App"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                UnitTestSessionState.STALE, UnitTestSessionStaleReason.INFOBASE_SYNC_PERFORMED, "run-1", //$NON-NLS-1$
                createdAt, createdAt, createdAt, createdAt.plusSeconds(60), Map.of("pid", 1234)); //$NON-NLS-1$

        JsonObject json = JsonParser.parseString(GsonProvider.toJson(snapshot.toPublicMap())).getAsJsonObject();
        assertEquals("session-1", json.get("sessionId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("mcp-session-1", json.get("ownerSessionId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("stale", json.get("state").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("infobase_sync_performed", json.get("staleReason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("yaxunit", json.get("provider").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Demo", json.get("projectName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("app-1", json.get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("app-1", json.getAsJsonObject("reuseScope").get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(1234, json.getAsJsonObject("providerCorrelation").get("pid").getAsInt()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("2026-04-24T07:01:00Z", json.get("expiresAt").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
