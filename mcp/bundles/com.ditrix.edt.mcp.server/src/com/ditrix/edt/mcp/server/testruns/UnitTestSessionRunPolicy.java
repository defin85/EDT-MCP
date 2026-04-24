/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.time.Instant;

/**
 * Pure routing policy for run_unit_tests warm-session selection.
 */
public final class UnitTestSessionRunPolicy
{
    private UnitTestSessionRunPolicy()
    {
    }

    public static Decision decide(UnitTestSessionMode mode, UnitTestSessionSnapshot matchingSession,
            boolean providerBridgeAvailable, Instant now)
    {
        UnitTestSessionMode effectiveMode = mode != null ? mode : UnitTestSessionMode.COLD;
        if (UnitTestSessionMode.COLD.equals(effectiveMode))
        {
            return Decision.cold(UnitTestSessionRunOutcome.COLD_STARTED, matchingSession);
        }
        if (UnitTestSessionMode.RECYCLE_THEN_RUN.equals(effectiveMode))
        {
            return Decision.recycleThenCold(matchingSession);
        }

        boolean reusable = matchingSession != null && matchingSession.isReusable(now != null ? now : Instant.now());
        if (reusable && providerBridgeAvailable)
        {
            return Decision.warm(matchingSession);
        }
        if (UnitTestSessionMode.REQUIRE_WARM.equals(effectiveMode))
        {
            return Decision.reject(matchingSession);
        }
        return Decision.cold(UnitTestSessionRunOutcome.COLD_STARTED, matchingSession);
    }

    public enum Route
    {
        COLD,
        WARM,
        REJECT,
        RECYCLE_THEN_COLD
    }

    public static final class Decision
    {
        private final Route route;
        private final UnitTestSessionRunOutcome outcome;
        private final UnitTestSessionSnapshot matchingSession;

        private Decision(Route route, UnitTestSessionRunOutcome outcome, UnitTestSessionSnapshot matchingSession)
        {
            this.route = route;
            this.outcome = outcome;
            this.matchingSession = matchingSession;
        }

        static Decision cold(UnitTestSessionRunOutcome outcome, UnitTestSessionSnapshot matchingSession)
        {
            return new Decision(Route.COLD, outcome, matchingSession);
        }

        static Decision warm(UnitTestSessionSnapshot matchingSession)
        {
            return new Decision(Route.WARM, UnitTestSessionRunOutcome.REUSED, matchingSession);
        }

        static Decision reject(UnitTestSessionSnapshot matchingSession)
        {
            return new Decision(Route.REJECT, UnitTestSessionRunOutcome.STALE_REJECTED, matchingSession);
        }

        static Decision recycleThenCold(UnitTestSessionSnapshot matchingSession)
        {
            return new Decision(Route.RECYCLE_THEN_COLD, UnitTestSessionRunOutcome.RECYCLED, matchingSession);
        }

        public Route getRoute()
        {
            return route;
        }

        public UnitTestSessionRunOutcome getOutcome()
        {
            return outcome;
        }

        public UnitTestSessionSnapshot getMatchingSession()
        {
            return matchingSession;
        }
    }
}
