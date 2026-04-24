/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.Map;

import org.junit.Test;

public class UnitTestSessionRunPolicyTest
{
    private static final Instant NOW = Instant.parse("2026-04-24T08:00:00Z"); //$NON-NLS-1$

    @Test
    public void testColdModeIgnoresReusableSession()
    {
        UnitTestSessionRunPolicy.Decision decision = UnitTestSessionRunPolicy.decide(UnitTestSessionMode.COLD,
                readySession(), true, NOW);

        assertEquals(UnitTestSessionRunPolicy.Route.COLD, decision.getRoute());
        assertEquals(UnitTestSessionRunOutcome.COLD_STARTED, decision.getOutcome());
    }

    @Test
    public void testPreferWarmUsesReadySessionWhenBridgeExists()
    {
        UnitTestSessionSnapshot session = readySession();

        UnitTestSessionRunPolicy.Decision decision = UnitTestSessionRunPolicy.decide(UnitTestSessionMode.PREFER_WARM,
                session, true, NOW);

        assertEquals(UnitTestSessionRunPolicy.Route.WARM, decision.getRoute());
        assertEquals(UnitTestSessionRunOutcome.REUSED, decision.getOutcome());
        assertEquals(session, decision.getMatchingSession());
    }

    @Test
    public void testPreferWarmFallsBackToColdWithoutBridge()
    {
        UnitTestSessionRunPolicy.Decision decision = UnitTestSessionRunPolicy.decide(UnitTestSessionMode.PREFER_WARM,
                readySession(), false, NOW);

        assertEquals(UnitTestSessionRunPolicy.Route.COLD, decision.getRoute());
        assertEquals(UnitTestSessionRunOutcome.COLD_STARTED, decision.getOutcome());
    }

    @Test
    public void testRequireWarmRejectsMissingStaleOrBusySession()
    {
        assertEquals(UnitTestSessionRunPolicy.Route.REJECT,
                UnitTestSessionRunPolicy.decide(UnitTestSessionMode.REQUIRE_WARM, null, true, NOW).getRoute());
        assertEquals(UnitTestSessionRunPolicy.Route.REJECT,
                UnitTestSessionRunPolicy.decide(UnitTestSessionMode.REQUIRE_WARM, staleSession(), true, NOW)
                        .getRoute());
        assertEquals(UnitTestSessionRunPolicy.Route.REJECT,
                UnitTestSessionRunPolicy.decide(UnitTestSessionMode.REQUIRE_WARM, busySession(), true, NOW)
                        .getRoute());
    }

    @Test
    public void testRecycleThenRunRoutesThroughRecycleBeforeCold()
    {
        UnitTestSessionRunPolicy.Decision decision = UnitTestSessionRunPolicy.decide(
                UnitTestSessionMode.RECYCLE_THEN_RUN, readySession(), true, NOW);

        assertEquals(UnitTestSessionRunPolicy.Route.RECYCLE_THEN_COLD, decision.getRoute());
        assertEquals(UnitTestSessionRunOutcome.RECYCLED, decision.getOutcome());
    }

    private static UnitTestSessionSnapshot readySession()
    {
        return session(UnitTestSessionState.READY, null, null);
    }

    private static UnitTestSessionSnapshot staleSession()
    {
        return session(UnitTestSessionState.STALE, UnitTestSessionStaleReason.INFOBASE_SYNC_PERFORMED, null);
    }

    private static UnitTestSessionSnapshot busySession()
    {
        return session(UnitTestSessionState.BUSY, null, "run-1"); //$NON-NLS-1$
    }

    private static UnitTestSessionSnapshot session(UnitTestSessionState state, UnitTestSessionStaleReason staleReason,
            String busyRunId)
    {
        return new UnitTestSessionSnapshot("session-1", null, //$NON-NLS-1$
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), state, staleReason, busyRunId, NOW, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                NOW, NOW, NOW.plusSeconds(60L), Map.of());
    }
}
