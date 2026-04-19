/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a long-running operation progress.
 */
public final class OperationProgressState
{
    public static final String STATUS_RUNNING = "RUNNING"; //$NON-NLS-1$
    public static final String STATUS_COMPLETED = "COMPLETED"; //$NON-NLS-1$
    public static final String STATUS_FAILED = "FAILED"; //$NON-NLS-1$
    public static final String STATUS_CANCELLED = "CANCELLED"; //$NON-NLS-1$

    private final String operationId;
    private final String toolName;
    private final String stage;
    private final String message;
    private final Double progress;
    private final Double total;
    private final boolean indeterminate;
    private final String status;
    private final Instant startedAt;
    private final Instant lastUpdateAt;
    private final long elapsedSeconds;
    private final String requestId;
    private final String sessionId;
    private final Object progressToken;
    private final boolean detached;
    private final Map<String, Object> details;
    private final List<ProgressEvent> recentEvents;

    public OperationProgressState(String operationId, String toolName, String stage, String message, Double progress,
            Double total, boolean indeterminate, String status, Instant startedAt, Instant lastUpdateAt,
            long elapsedSeconds, String requestId, String sessionId, Object progressToken, boolean detached,
            Map<String, Object> details,
            List<ProgressEvent> recentEvents)
    {
        this.operationId = operationId;
        this.toolName = toolName;
        this.stage = stage;
        this.message = message;
        this.progress = progress;
        this.total = total;
        this.indeterminate = indeterminate;
        this.status = status;
        this.startedAt = startedAt;
        this.lastUpdateAt = lastUpdateAt;
        this.elapsedSeconds = elapsedSeconds;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.progressToken = progressToken;
        this.detached = detached;
        this.details = details != null ? Map.copyOf(details) : Map.of();
        this.recentEvents = recentEvents != null ? List.copyOf(recentEvents) : List.of();
    }

    public String getOperationId()
    {
        return operationId;
    }

    public String getToolName()
    {
        return toolName;
    }

    public String getStage()
    {
        return stage;
    }

    public String getMessage()
    {
        return message;
    }

    public Double getProgress()
    {
        return progress;
    }

    public Double getTotal()
    {
        return total;
    }

    public boolean isIndeterminate()
    {
        return indeterminate;
    }

    public String getStatus()
    {
        return status;
    }

    public Instant getStartedAt()
    {
        return startedAt;
    }

    public Instant getLastUpdateAt()
    {
        return lastUpdateAt;
    }

    public long getElapsedSeconds()
    {
        return elapsedSeconds;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public Object getProgressToken()
    {
        return progressToken;
    }

    public boolean isDetached()
    {
        return detached;
    }

    public Map<String, Object> getDetails()
    {
        return details;
    }

    public List<ProgressEvent> getRecentEvents()
    {
        return recentEvents;
    }
}
