/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory registry for persistent unit-test sessions.
 */
public final class UnitTestSessionRegistry
{
    public static final long DEFAULT_TTL_MS = UnitTestRunStore.DEFAULT_TTL_MS;

    private final ConcurrentMap<String, UnitTestSessionSnapshot> sessions = new ConcurrentHashMap<>();
    private final long ttlMs;

    public UnitTestSessionRegistry()
    {
        this(DEFAULT_TTL_MS);
    }

    public UnitTestSessionRegistry(long ttlMs)
    {
        this.ttlMs = Math.max(1L, ttlMs);
    }

    public UnitTestSessionSnapshot createStartingSession(String ownerSessionId, UnitTestSessionTarget target,
            Map<String, Object> providerCorrelation)
    {
        cleanupExpired();
        Instant now = Instant.now();
        UnitTestSessionSnapshot snapshot = new UnitTestSessionSnapshot(UUID.randomUUID().toString(), ownerSessionId,
                target, UnitTestSessionState.STARTING, null, null, now, now, now, now.plusMillis(ttlMs),
                providerCorrelation);
        sessions.put(snapshot.getSessionId(), snapshot);
        return snapshot;
    }

    public UnitTestSessionSnapshot put(UnitTestSessionSnapshot snapshot)
    {
        cleanupExpired();
        if (snapshot != null)
        {
            sessions.put(snapshot.getSessionId(), snapshot);
        }
        return snapshot;
    }

    public UnitTestSessionSnapshot get(String sessionId)
    {
        cleanupExpired();
        if (sessionId == null || sessionId.isBlank())
        {
            return null;
        }
        return sessions.get(sessionId);
    }

    public synchronized UnitTestSessionSnapshot markReady(String sessionId)
    {
        return transition(sessionId, UnitTestSessionState.READY, null, null, true, null);
    }

    public synchronized UnitTestSessionSnapshot touchHeartbeat(String sessionId)
    {
        cleanupExpired();
        UnitTestSessionSnapshot current = get(sessionId);
        if (current == null)
        {
            return null;
        }
        Instant now = Instant.now();
        return replace(current, current.getState(), current.getStaleReason(), current.getBusyRunId(), now, now);
    }

    public synchronized BusyAcquisition tryAcquireBusy(String sessionId, String runId)
    {
        cleanupExpired();
        String normalizedRunId = trimToNull(runId);
        if (normalizedRunId == null)
        {
            throw new IllegalArgumentException("runId is required"); //$NON-NLS-1$
        }
        UnitTestSessionSnapshot current = get(sessionId);
        if (current == null)
        {
            return new BusyAcquisition(false, null);
        }
        if (!UnitTestSessionState.READY.equals(current.getState()))
        {
            return new BusyAcquisition(false, current);
        }
        UnitTestSessionSnapshot busy = replace(current, UnitTestSessionState.BUSY, null, normalizedRunId,
                Instant.now(), current.getLastHeartbeatAt());
        return new BusyAcquisition(true, busy);
    }

    public synchronized UnitTestSessionSnapshot releaseBusy(String sessionId, String runId)
    {
        cleanupExpired();
        String normalizedRunId = trimToNull(runId);
        UnitTestSessionSnapshot current = get(sessionId);
        if (current == null || !UnitTestSessionState.BUSY.equals(current.getState()))
        {
            return current;
        }
        if (normalizedRunId == null || !normalizedRunId.equals(current.getBusyRunId()))
        {
            return current;
        }
        return transition(sessionId, UnitTestSessionState.READY, null, null, true, null);
    }

    public synchronized UnitTestSessionSnapshot markStale(String sessionId, UnitTestSessionStaleReason reason)
    {
        return transition(sessionId, UnitTestSessionState.STALE, requireReason(reason), null, false, null);
    }

    public synchronized UnitTestSessionSnapshot markDead(String sessionId, UnitTestSessionStaleReason reason)
    {
        return transition(sessionId, UnitTestSessionState.DEAD, requireReason(reason), null, false, null);
    }

    public synchronized List<UnitTestSessionSnapshot> invalidateTarget(UnitTestSessionTarget target,
            UnitTestSessionStaleReason reason)
    {
        cleanupExpired();
        UnitTestSessionStaleReason requiredReason = requireReason(reason);
        List<UnitTestSessionSnapshot> invalidated = new ArrayList<>();
        for (UnitTestSessionSnapshot snapshot : sessions.values())
        {
            if (snapshot != null && snapshot.matchesReuseScope(target) && isInvalidatable(snapshot))
            {
                invalidated.add(replace(snapshot, UnitTestSessionState.STALE, requiredReason, null, Instant.now(),
                        snapshot.getLastHeartbeatAt()));
            }
        }
        return invalidated;
    }

    public synchronized List<UnitTestSessionSnapshot> invalidateProject(String projectName,
            UnitTestSessionStaleReason reason)
    {
        cleanupExpired();
        UnitTestSessionStaleReason requiredReason = requireReason(reason);
        String normalizedProjectName = trimToNull(projectName);
        List<UnitTestSessionSnapshot> invalidated = new ArrayList<>();
        if (normalizedProjectName == null)
        {
            return invalidated;
        }
        for (UnitTestSessionSnapshot snapshot : sessions.values())
        {
            if (snapshot != null && normalizedProjectName.equals(snapshot.getTarget().getProjectName())
                    && isInvalidatable(snapshot))
            {
                invalidated.add(replace(snapshot, UnitTestSessionState.STALE, requiredReason, null, Instant.now(),
                        snapshot.getLastHeartbeatAt()));
            }
        }
        return invalidated;
    }

    public synchronized List<UnitTestSessionSnapshot> markHeartbeatLost(long heartbeatTimeoutMs)
    {
        cleanupExpired();
        long timeoutMs = Math.max(1L, heartbeatTimeoutMs);
        return markHeartbeatLostBefore(Instant.now().minusMillis(timeoutMs));
    }

    synchronized List<UnitTestSessionSnapshot> markHeartbeatLostBefore(Instant cutoff)
    {
        List<UnitTestSessionSnapshot> invalidated = new ArrayList<>();
        for (UnitTestSessionSnapshot snapshot : sessions.values())
        {
            if (cutoff != null && snapshot != null && isInvalidatable(snapshot) && snapshot.getLastHeartbeatAt() != null
                    && snapshot.getLastHeartbeatAt().isBefore(cutoff))
            {
                invalidated.add(replace(snapshot, UnitTestSessionState.DEAD, UnitTestSessionStaleReason.HEARTBEAT_LOST,
                        null, Instant.now(), snapshot.getLastHeartbeatAt()));
            }
        }
        return invalidated;
    }

    public UnitTestSessionSnapshot findLatestForTarget(String ownerSessionId, UnitTestSessionTarget target)
    {
        cleanupExpired();
        String normalizedOwner = trimToNull(ownerSessionId);
        return sessions.values().stream()
                .filter(snapshot -> snapshot != null && snapshot.matchesReuseScope(target))
                .filter(snapshot -> sameOwner(normalizedOwner, snapshot.getOwnerSessionId()))
                .max(Comparator.comparing(UnitTestSessionSnapshot::getExpiresAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    public List<UnitTestSessionSnapshot> list()
    {
        cleanupExpired();
        return new ArrayList<>(sessions.values());
    }

    public int cleanupExpired()
    {
        return cleanupExpired(Instant.now());
    }

    int cleanupExpired(Instant now)
    {
        int removed = 0;
        for (UnitTestSessionSnapshot snapshot : sessions.values())
        {
            if (snapshot != null && snapshot.isExpired(now) && sessions.remove(snapshot.getSessionId(), snapshot))
            {
                removed++;
            }
        }
        return removed;
    }

    public void clear()
    {
        sessions.clear();
    }

    private UnitTestSessionSnapshot transition(String sessionId, UnitTestSessionState state,
            UnitTestSessionStaleReason staleReason, String busyRunId, boolean heartbeat, Instant expiresAt)
    {
        cleanupExpired();
        UnitTestSessionSnapshot current = get(sessionId);
        if (current == null)
        {
            return null;
        }
        Instant now = Instant.now();
        return replace(current, state, staleReason, busyRunId, now, heartbeat ? now : current.getLastHeartbeatAt(),
                expiresAt != null ? expiresAt : current.getExpiresAt());
    }

    private UnitTestSessionSnapshot replace(UnitTestSessionSnapshot current, UnitTestSessionState state,
            UnitTestSessionStaleReason staleReason, String busyRunId, Instant updatedAt, Instant lastHeartbeatAt)
    {
        return replace(current, state, staleReason, busyRunId, updatedAt, lastHeartbeatAt, current.getExpiresAt());
    }

    private UnitTestSessionSnapshot replace(UnitTestSessionSnapshot current, UnitTestSessionState state,
            UnitTestSessionStaleReason staleReason, String busyRunId, Instant updatedAt, Instant lastHeartbeatAt,
            Instant expiresAt)
    {
        UnitTestSessionSnapshot replacement = new UnitTestSessionSnapshot(current.getSessionId(),
                current.getOwnerSessionId(), current.getTarget(), state, staleReason, busyRunId,
                current.getCreatedAt(), updatedAt, lastHeartbeatAt, expiresAt, current.getProviderCorrelation());
        sessions.put(replacement.getSessionId(), replacement);
        return replacement;
    }

    private static boolean isInvalidatable(UnitTestSessionSnapshot snapshot)
    {
        return snapshot != null && !UnitTestSessionState.STALE.equals(snapshot.getState())
                && !UnitTestSessionState.DEAD.equals(snapshot.getState());
    }

    private static UnitTestSessionStaleReason requireReason(UnitTestSessionStaleReason reason)
    {
        if (reason == null)
        {
            throw new IllegalArgumentException("stale reason is required"); //$NON-NLS-1$
        }
        return reason;
    }

    public static final class BusyAcquisition
    {
        private final boolean acquired;
        private final UnitTestSessionSnapshot snapshot;

        private BusyAcquisition(boolean acquired, UnitTestSessionSnapshot snapshot)
        {
            this.acquired = acquired;
            this.snapshot = snapshot;
        }

        public boolean isAcquired()
        {
            return acquired;
        }

        public UnitTestSessionSnapshot getSnapshot()
        {
            return snapshot;
        }
    }

    private static boolean sameOwner(String requestedOwner, String sessionOwner)
    {
        String normalizedSessionOwner = trimToNull(sessionOwner);
        if (requestedOwner == null)
        {
            return normalizedSessionOwner == null;
        }
        return requestedOwner.equals(normalizedSessionOwner);
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
