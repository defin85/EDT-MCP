/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Thread-safe mutable reporter for one active operation.
 */
public class OperationProgressReporter
{
    private static final int DEFAULT_RECENT_EVENT_LIMIT = 10;

    private final int recentEventLimit;
    private final Deque<ProgressEvent> recentEvents = new ArrayDeque<>();

    private String operationId;
    private String toolName;
    private String stage;
    private String message;
    private Double progress;
    private Double total;
    private boolean indeterminate = true;
    private String status;
    private Instant startedAt;
    private Instant lastUpdateAt;
    private String requestId;
    private String sessionId;
    private Object progressToken;
    private boolean detached;
    private Map<String, Object> details = Map.of();
    private volatile Consumer<OperationProgressState> stateListener;

    public OperationProgressReporter()
    {
        this(DEFAULT_RECENT_EVENT_LIMIT);
    }

    public OperationProgressReporter(int recentEventLimit)
    {
        this.recentEventLimit = recentEventLimit > 0 ? recentEventLimit : DEFAULT_RECENT_EVENT_LIMIT;
    }

    public OperationProgressState start(String operationId, String toolName, String stage, String message,
            String requestId, String sessionId, Object progressToken)
    {
        return start(operationId, toolName, stage, message, requestId, sessionId, progressToken, Map.of());
    }

    public OperationProgressState start(String operationId, String toolName, String stage, String message,
            String requestId, String sessionId, Object progressToken, Map<String, Object> details)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            recentEvents.clear();
            this.operationId = hasText(operationId) ? operationId : UUID.randomUUID().toString();
            this.toolName = toolName;
            this.stage = stage;
            this.message = message;
            this.progress = null;
            this.total = null;
            this.indeterminate = true;
            this.status = OperationProgressState.STATUS_RUNNING;
            this.startedAt = Instant.now();
            this.lastUpdateAt = startedAt;
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.progressToken = progressToken;
            this.detached = false;
            this.details = sanitizeDetails(details);
            addEvent(lastUpdateAt, stage, message, null, null);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public OperationProgressState stage(String stage, String message)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            this.stage = stage;
            this.message = message;
            this.status = OperationProgressState.STATUS_RUNNING;
            this.lastUpdateAt = Instant.now();
            addEvent(lastUpdateAt, stage, message, progress, total);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public OperationProgressState progress(double progress, Double total, String message)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            if (!hasText(stage) && hasText(message))
            {
                this.stage = message;
            }
            this.progress = Double.valueOf(progress);
            this.total = isKnownTotal(total) ? total : null;
            this.indeterminate = !isKnownTotal(total);
            this.message = message;
            this.status = OperationProgressState.STATUS_RUNNING;
            this.lastUpdateAt = Instant.now();
            addEvent(lastUpdateAt, stage, message, this.progress, this.total);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public OperationProgressState indeterminate(String stage, String message)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            this.stage = stage;
            this.message = message;
            this.total = null;
            this.indeterminate = true;
            this.status = OperationProgressState.STATUS_RUNNING;
            this.lastUpdateAt = Instant.now();
            addEvent(lastUpdateAt, stage, message, progress, null);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public OperationProgressState completed(String message)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            this.message = message;
            this.status = OperationProgressState.STATUS_COMPLETED;
            this.lastUpdateAt = Instant.now();
            addEvent(lastUpdateAt, stage, message, progress, total);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public OperationProgressState failed(String message, Throwable error)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            this.message = hasText(message) ? message : errorMessage(error);
            this.status = OperationProgressState.STATUS_FAILED;
            this.lastUpdateAt = Instant.now();
            addEvent(lastUpdateAt, stage, this.message, progress, total);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public OperationProgressState cancelled(String message)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            this.message = hasText(message) ? message : "Operation cancelled"; //$NON-NLS-1$
            this.status = OperationProgressState.STATUS_CANCELLED;
            this.lastUpdateAt = Instant.now();
            addEvent(lastUpdateAt, stage, this.message, progress, total);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public void appendEvent(ProgressEvent event)
    {
        if (event == null)
        {
            return;
        }
        SnapshotPublication publication;
        synchronized (this)
        {
            lastUpdateAt = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
            addEvent(lastUpdateAt, event.getStage(), event.getMessage(), event.getProgress(), event.getTotal());
            publication = capturePublication();
        }
        publishSnapshot(publication);
    }

    public synchronized OperationProgressReporter detachedCopy(String stage, String message, Map<String, Object> details)
    {
        OperationProgressReporter copy = new OperationProgressReporter(recentEventLimit);
        copy.operationId = operationId;
        copy.toolName = toolName;
        copy.stage = hasText(stage) ? stage : this.stage;
        copy.message = hasText(message) ? message : this.message;
        copy.progress = null;
        copy.total = null;
        copy.indeterminate = true;
        copy.status = OperationProgressState.STATUS_RUNNING;
        copy.startedAt = startedAt != null ? startedAt : Instant.now();
        copy.lastUpdateAt = Instant.now();
        copy.requestId = requestId;
        copy.sessionId = sessionId;
        copy.progressToken = null;
        copy.detached = true;
        copy.details = sanitizeDetails(details != null && !details.isEmpty() ? details : this.details);
        copy.recentEvents.addAll(recentEvents);
        copy.addEvent(copy.lastUpdateAt, copy.stage, copy.message, null, null);
        return copy;
    }

    public OperationProgressState detachedUpdate(String stage, String message, Map<String, Object> details)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            this.stage = stage;
            this.message = message;
            this.progress = null;
            this.total = null;
            this.indeterminate = true;
            this.status = OperationProgressState.STATUS_RUNNING;
            this.progressToken = null;
            this.detached = true;
            this.details = sanitizeDetails(details);
            this.lastUpdateAt = Instant.now();
            addEvent(lastUpdateAt, stage, message, null, null);
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public OperationProgressState updateDetails(Map<String, Object> details)
    {
        SnapshotPublication publication;
        synchronized (this)
        {
            this.details = sanitizeDetails(details);
            this.lastUpdateAt = Instant.now();
            publication = capturePublication();
        }
        return publishSnapshot(publication);
    }

    public void setStateListener(Consumer<OperationProgressState> stateListener)
    {
        this.stateListener = stateListener;
    }

    public synchronized void appendStateListener(Consumer<OperationProgressState> additionalListener)
    {
        if (additionalListener == null)
        {
            return;
        }
        Consumer<OperationProgressState> current = this.stateListener;
        if (current == null)
        {
            this.stateListener = additionalListener;
        }
        else
        {
            this.stateListener = state -> {
                current.accept(state);
                additionalListener.accept(state);
            };
        }
    }

    public synchronized OperationProgressState snapshot()
    {
        return buildSnapshot();
    }

    public synchronized List<ProgressEvent> recentEvents()
    {
        return List.copyOf(new ArrayList<>(recentEvents));
    }

    private SnapshotPublication capturePublication()
    {
        return new SnapshotPublication(buildSnapshot(), stateListener);
    }

    private OperationProgressState buildSnapshot()
    {
        if (startedAt == null)
        {
            return null;
        }
        Instant snapshotTime = Instant.now();
        long elapsedSeconds = Duration.between(startedAt, snapshotTime).getSeconds();
        return new OperationProgressState(operationId, toolName, stage, message, progress, total, indeterminate,
                status, startedAt, lastUpdateAt, elapsedSeconds, requestId, sessionId, progressToken, detached,
                details,
                new ArrayList<>(recentEvents));
    }

    private void addEvent(Instant timestamp, String stage, String message, Double progress, Double total)
    {
        recentEvents.addLast(new ProgressEvent(timestamp, stage, message, progress, total));
        while (recentEvents.size() > recentEventLimit)
        {
            recentEvents.removeFirst();
        }
    }

    private boolean isKnownTotal(Double total)
    {
        return total != null && total.doubleValue() > 0;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private static String errorMessage(Throwable error)
    {
        if (error == null || error.getMessage() == null)
        {
            return null;
        }
        return error.getMessage();
    }

    private OperationProgressState publishSnapshot(SnapshotPublication publication)
    {
        if (publication == null)
        {
            return null;
        }
        Consumer<OperationProgressState> listener = publication.listener;
        OperationProgressState snapshot = publication.snapshot;
        if (listener != null && snapshot != null)
        {
            try
            {
                listener.accept(snapshot);
            }
            catch (RuntimeException e)
            {
                // Progress listeners must not break tool execution.
            }
        }
        return snapshot;
    }

    private Map<String, Object> sanitizeDetails(Map<String, Object> details)
    {
        return details != null && !details.isEmpty() ? Map.copyOf(details) : Map.of();
    }

    private static final class SnapshotPublication
    {
        private final OperationProgressState snapshot;
        private final Consumer<OperationProgressState> listener;

        private SnapshotPublication(OperationProgressState snapshot, Consumer<OperationProgressState> listener)
        {
            this.snapshot = snapshot;
            this.listener = listener;
        }
    }
}
