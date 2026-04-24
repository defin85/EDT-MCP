/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.Test;

public class UnitTestSessionRegistryTest
{
    @Test
    public void testCreateStartingSessionAssignsStableIdentityAndTarget()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(60_000L);
        UnitTestSessionTarget target = new UnitTestSessionTarget("yaxunit", "Demo", "app-1", "Demo App"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        UnitTestSessionSnapshot session = registry.createStartingSession("mcp-session-1", target, //$NON-NLS-1$
                Map.of("pid", 1234)); //$NON-NLS-1$

        assertNotNull(session.getSessionId());
        assertEquals(UnitTestSessionState.STARTING, session.getState());
        assertEquals("mcp-session-1", session.getOwnerSessionId()); //$NON-NLS-1$
        assertEquals(target, session.getTarget());
        assertEquals(session, registry.get(session.getSessionId()));
    }

    @Test
    public void testFindLatestForTargetHonorsOwnerAndTargetScope()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(60_000L);
        UnitTestSessionTarget target = new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        UnitTestSessionSnapshot first = registry.createStartingSession("mcp-session-1", target, null); //$NON-NLS-1$
        UnitTestSessionSnapshot second = registry.createStartingSession("mcp-session-1", target, null); //$NON-NLS-1$
        registry.createStartingSession("mcp-session-2", target, null); //$NON-NLS-1$

        UnitTestSessionSnapshot found = registry.findLatestForTarget("mcp-session-1", //$NON-NLS-1$
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertEquals(second.getSessionId(), found.getSessionId());
        assertNotEquals(first.getSessionId(), found.getSessionId());
        assertNull(registry.findLatestForTarget("mcp-session-1", //$NON-NLS-1$
                new UnitTestSessionTarget("yaxunit", "Demo", "app-2", null))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testCleanupExpiredRemovesBoundedRetentionRecords()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(1L);
        UnitTestSessionSnapshot session = registry.createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        int removed = registry.cleanupExpired(Instant.now().plusSeconds(1));

        assertEquals(1, removed);
        assertNull(registry.get(session.getSessionId()));
        assertTrue(registry.list().isEmpty());
    }

    @Test
    public void testHeartbeatUpdatesLivenessTimestamp()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(60_000L);
        UnitTestSessionSnapshot session = registry.createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        UnitTestSessionSnapshot ready = registry.markReady(session.getSessionId());
        UnitTestSessionSnapshot heartbeat = registry.touchHeartbeat(session.getSessionId());

        assertEquals(UnitTestSessionState.READY, ready.getState());
        assertEquals(UnitTestSessionState.READY, heartbeat.getState());
        assertTrue(!heartbeat.getLastHeartbeatAt().isBefore(ready.getLastHeartbeatAt()));
    }

    @Test
    public void testBusyAcquisitionSerializesConcurrentRuns()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(60_000L);
        UnitTestSessionSnapshot session = registry.createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        registry.markReady(session.getSessionId());

        UnitTestSessionRegistry.BusyAcquisition first = registry.tryAcquireBusy(session.getSessionId(), "run-1"); //$NON-NLS-1$
        UnitTestSessionRegistry.BusyAcquisition second = registry.tryAcquireBusy(session.getSessionId(), "run-2"); //$NON-NLS-1$

        assertTrue(first.isAcquired());
        assertEquals(UnitTestSessionState.BUSY, first.getSnapshot().getState());
        assertEquals("run-1", first.getSnapshot().getBusyRunId()); //$NON-NLS-1$
        assertFalse(second.isAcquired());
        assertEquals("run-1", second.getSnapshot().getBusyRunId()); //$NON-NLS-1$

        UnitTestSessionSnapshot wrongRelease = registry.releaseBusy(session.getSessionId(), "run-2"); //$NON-NLS-1$
        assertEquals(UnitTestSessionState.BUSY, wrongRelease.getState());

        UnitTestSessionSnapshot released = registry.releaseBusy(session.getSessionId(), "run-1"); //$NON-NLS-1$
        assertEquals(UnitTestSessionState.READY, released.getState());
        assertNull(released.getBusyRunId());
    }

    @Test
    public void testMarkStaleMakesSessionNonReusableWithReason()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(60_000L);
        UnitTestSessionSnapshot session = registry.createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        registry.markReady(session.getSessionId());

        UnitTestSessionSnapshot stale = registry.markStale(session.getSessionId(),
                UnitTestSessionStaleReason.EXPLICIT_RECYCLE);

        assertEquals(UnitTestSessionState.STALE, stale.getState());
        assertEquals(UnitTestSessionStaleReason.EXPLICIT_RECYCLE, stale.getStaleReason());
        assertFalse(stale.isReusable(Instant.now()));
    }

    @Test
    public void testInvalidateTargetOnlyMarksMatchingSessions()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(60_000L);
        UnitTestSessionTarget target = new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        UnitTestSessionSnapshot matching = registry.createStartingSession(null, target, null);
        UnitTestSessionSnapshot other = registry.createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-2", null), null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertEquals(1,
                registry.invalidateTarget(target, UnitTestSessionStaleReason.INFOBASE_SYNC_PERFORMED).size());

        assertEquals(UnitTestSessionState.STALE, registry.get(matching.getSessionId()).getState());
        assertEquals(UnitTestSessionState.STARTING, registry.get(other.getSessionId()).getState());
    }

    @Test
    public void testHeartbeatLossMarksLiveSessionDead()
    {
        UnitTestSessionRegistry registry = new UnitTestSessionRegistry(60_000L);
        UnitTestSessionSnapshot session = registry.createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertEquals(1, registry.markHeartbeatLostBefore(Instant.now().plusSeconds(1)).size());

        UnitTestSessionSnapshot dead = registry.get(session.getSessionId());
        assertEquals(UnitTestSessionState.DEAD, dead.getState());
        assertEquals(UnitTestSessionStaleReason.HEARTBEAT_LOST, dead.getStaleReason());
    }
}
