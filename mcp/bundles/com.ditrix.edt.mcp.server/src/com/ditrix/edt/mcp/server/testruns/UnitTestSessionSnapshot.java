/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable public snapshot of a persistent unit-test session.
 */
public final class UnitTestSessionSnapshot
{
    private final String sessionId;
    private final String ownerSessionId;
    private final UnitTestSessionTarget target;
    private final UnitTestSessionState state;
    private final UnitTestSessionStaleReason staleReason;
    private final String busyRunId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastHeartbeatAt;
    private final Instant expiresAt;
    private final Map<String, Object> providerCorrelation;

    public UnitTestSessionSnapshot(String sessionId, String ownerSessionId, UnitTestSessionTarget target,
            UnitTestSessionState state, UnitTestSessionStaleReason staleReason, String busyRunId, Instant createdAt,
            Instant updatedAt, Instant lastHeartbeatAt, Instant expiresAt, Map<String, Object> providerCorrelation)
    {
        this.sessionId = requireText(sessionId, "sessionId"); //$NON-NLS-1$
        this.ownerSessionId = trimToNull(ownerSessionId);
        if (target == null)
        {
            throw new IllegalArgumentException("target is required"); //$NON-NLS-1$
        }
        this.target = target;
        if (state == null)
        {
            throw new IllegalArgumentException("state is required"); //$NON-NLS-1$
        }
        this.state = state;
        this.staleReason = staleReason;
        this.busyRunId = trimToNull(busyRunId);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.expiresAt = expiresAt;
        this.providerCorrelation = providerCorrelation != null ? Map.copyOf(providerCorrelation) : Map.of();
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public String getOwnerSessionId()
    {
        return ownerSessionId;
    }

    public UnitTestSessionTarget getTarget()
    {
        return target;
    }

    public UnitTestSessionState getState()
    {
        return state;
    }

    public UnitTestSessionStaleReason getStaleReason()
    {
        return staleReason;
    }

    public String getBusyRunId()
    {
        return busyRunId;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }

    public Instant getUpdatedAt()
    {
        return updatedAt;
    }

    public Instant getLastHeartbeatAt()
    {
        return lastHeartbeatAt;
    }

    public Instant getExpiresAt()
    {
        return expiresAt;
    }

    public Map<String, Object> getProviderCorrelation()
    {
        return providerCorrelation;
    }

    public boolean isExpired(Instant now)
    {
        return expiresAt != null && now != null && !expiresAt.isAfter(now);
    }

    public boolean isReusable(Instant now)
    {
        return UnitTestSessionState.READY.equals(state) && staleReason == null && !isExpired(now);
    }

    public boolean matchesReuseScope(UnitTestSessionTarget requestedTarget)
    {
        return target.matchesReuseScope(requestedTarget);
    }

    public Map<String, Object> toPublicMap()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(UnitTestSessionToolContract.FIELD_SESSION_ID, sessionId);
        result.put(UnitTestSessionToolContract.FIELD_STATE, state.wireValue());
        result.put("provider", target.getProvider()); //$NON-NLS-1$
        result.put("projectName", target.getProjectName()); //$NON-NLS-1$
        result.put("applicationId", target.getApplicationId()); //$NON-NLS-1$
        if (target.getApplicationName() != null)
        {
            result.put("applicationName", target.getApplicationName()); //$NON-NLS-1$
        }
        if (ownerSessionId != null)
        {
            result.put(UnitTestSessionToolContract.FIELD_OWNER_SESSION_ID, ownerSessionId);
        }
        result.put(UnitTestSessionToolContract.FIELD_REUSE_SCOPE, target.toPublicMap());
        if (staleReason != null)
        {
            result.put(UnitTestSessionToolContract.FIELD_STALE_REASON, staleReason.wireValue());
        }
        if (busyRunId != null)
        {
            result.put("busyRunId", busyRunId); //$NON-NLS-1$
        }
        putInstant(result, "createdAt", createdAt); //$NON-NLS-1$
        putInstant(result, "updatedAt", updatedAt); //$NON-NLS-1$
        putInstant(result, "lastHeartbeatAt", lastHeartbeatAt); //$NON-NLS-1$
        putInstant(result, "expiresAt", expiresAt); //$NON-NLS-1$
        if (!providerCorrelation.isEmpty())
        {
            result.put("providerCorrelation", new LinkedHashMap<>(providerCorrelation)); //$NON-NLS-1$
        }
        return result;
    }

    private static void putInstant(Map<String, Object> result, String key, Instant value)
    {
        if (value != null)
        {
            result.put(key, value.toString());
        }
    }

    private static String requireText(String value, String name)
    {
        String trimmed = trimToNull(value);
        if (trimmed == null)
        {
            throw new IllegalArgumentException(name + " is required"); //$NON-NLS-1$
        }
        return trimmed;
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
