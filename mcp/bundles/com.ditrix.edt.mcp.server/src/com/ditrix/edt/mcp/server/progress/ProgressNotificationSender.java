/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.google.gson.JsonObject;

/**
 * Sends MCP notifications/progress over registered SSE sessions.
 */
public class ProgressNotificationSender
{
    private static final int MAX_DELIVERY_THREADS = 10;

    private final SseSessionRegistry sseSessionRegistry;
    private final ConcurrentMap<String, NotificationFingerprint> lastSentFingerprints = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionDeliveryState> sessionDeliveryStates = new ConcurrentHashMap<>();
    private final ExecutorService deliveryExecutor;

    public ProgressNotificationSender(SseSessionRegistry sseSessionRegistry)
    {
        this(sseSessionRegistry, createDefaultDeliveryExecutor());
    }

    ProgressNotificationSender(SseSessionRegistry sseSessionRegistry, ExecutorService deliveryExecutor)
    {
        this.sseSessionRegistry = Objects.requireNonNull(sseSessionRegistry);
        this.deliveryExecutor = Objects.requireNonNull(deliveryExecutor);
    }

    public void onOperationUpdated(OperationProgressState state)
    {
        if (state == null || state.getProgressToken() == null || !hasText(state.getSessionId()))
        {
            return;
        }

        String key = fingerprintKey(state);
        NotificationFingerprint fingerprint = new NotificationFingerprint(extractProgressValue(state),
                extractTotalValue(state), buildMessage(state));
        NotificationFingerprint previous = lastSentFingerprints.get(key);
        if (fingerprint.equals(previous))
        {
            if (isTerminal(state))
            {
                lastSentFingerprints.remove(key);
                discardPendingEvent(state.getSessionId(), key);
            }
            return;
        }

        enqueueProgressEvent(state, key, fingerprint);
    }

    public void clear()
    {
        sessionDeliveryStates.clear();
        lastSentFingerprints.clear();
    }

    public void shutdown()
    {
        clear();
        deliveryExecutor.shutdownNow();
    }

    private String buildPayload(OperationProgressState state)
    {
        JsonObject root = new JsonObject();
        root.addProperty("jsonrpc", McpConstants.JSONRPC_VERSION); //$NON-NLS-1$
        root.addProperty("method", McpConstants.METHOD_NOTIFICATION_PROGRESS); //$NON-NLS-1$

        JsonObject params = new JsonObject();
        addDynamicValue(params, "progressToken", state.getProgressToken()); //$NON-NLS-1$
        params.addProperty("progress", extractProgressValue(state)); //$NON-NLS-1$

        Double totalValue = extractTotalValue(state);
        if (totalValue != null)
        {
            params.addProperty("total", totalValue); //$NON-NLS-1$
        }

        String message = buildMessage(state);
        if (hasText(message))
        {
            params.addProperty("message", message); //$NON-NLS-1$
        }

        root.add("params", params); //$NON-NLS-1$
        return root.toString();
    }

    private void addDynamicValue(JsonObject object, String propertyName, Object value)
    {
        if (value instanceof Number)
        {
            object.addProperty(propertyName, (Number) value);
        }
        else if (value instanceof Boolean)
        {
            object.addProperty(propertyName, (Boolean) value);
        }
        else
        {
            object.addProperty(propertyName, String.valueOf(value));
        }
    }

    private double extractProgressValue(OperationProgressState state)
    {
        if (state.getProgress() != null)
        {
            return state.getProgress().doubleValue();
        }
        return Math.max(1, state.getRecentEvents().size());
    }

    private Double extractTotalValue(OperationProgressState state)
    {
        if (!state.isIndeterminate() && state.getTotal() != null && state.getTotal().doubleValue() > 0)
        {
            return state.getTotal();
        }
        return null;
    }

    private String buildMessage(OperationProgressState state)
    {
        if (hasText(state.getMessage()))
        {
            return state.getMessage();
        }
        if (hasText(state.getStage()))
        {
            return state.getStage().replace('_', ' ');
        }
        return null;
    }

    private boolean isTerminal(OperationProgressState state)
    {
        return OperationProgressState.STATUS_COMPLETED.equals(state.getStatus())
                || OperationProgressState.STATUS_FAILED.equals(state.getStatus())
                || OperationProgressState.STATUS_CANCELLED.equals(state.getStatus());
    }

    private boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private void enqueueProgressEvent(OperationProgressState state, String key, NotificationFingerprint fingerprint)
    {
        SessionDeliveryState sessionState = sessionDeliveryStates.computeIfAbsent(state.getSessionId(),
                ignored -> new SessionDeliveryState());
        PendingNotification replaced = sessionState.pendingByToken.put(key,
                new PendingNotification(state, key, fingerprint));
        if (replaced != null)
        {
            logDropped(replaced.state, "coalesced_pending_event"); //$NON-NLS-1$
        }
        scheduleDrain(state.getSessionId(), sessionState);
    }

    private void discardPendingEvent(String sessionId, String key)
    {
        if (!hasText(sessionId) || !hasText(key))
        {
            return;
        }
        SessionDeliveryState sessionState = sessionDeliveryStates.get(sessionId);
        if (sessionState == null)
        {
            return;
        }
        sessionState.pendingByToken.remove(key);
        cleanupSessionState(sessionId, sessionState);
    }

    private void scheduleDrain(String sessionId, SessionDeliveryState sessionState)
    {
        if (!sessionState.draining.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            deliveryExecutor.execute(() -> drainSession(sessionId, sessionState));
        }
        catch (RejectedExecutionException e)
        {
            sessionState.draining.set(false);
            PendingNotification pending = pollPending(sessionState);
            if (pending != null)
            {
                logDropped(pending.state, "delivery_executor_rejected"); //$NON-NLS-1$
            }
            sessionState.pendingByToken.clear();
            cleanupSessionState(sessionId, sessionState);
        }
    }

    private void drainSession(String sessionId, SessionDeliveryState sessionState)
    {
        try
        {
            PendingNotification pending;
            while ((pending = pollPending(sessionState)) != null)
            {
                boolean sent = sseSessionRegistry.sendEvent(sessionId, "message", buildPayload(pending.state)); //$NON-NLS-1$
                if (!sent)
                {
                    clearSessionFingerprints(sessionId);
                    sessionState.pendingByToken.clear();
                    Activator.logWarning("[diag] SSE session evicted after failed progress delivery: " //$NON-NLS-1$
                            + describeState(pending.state));
                    return;
                }
                rememberDeliveredFingerprint(pending);
            }
        }
        finally
        {
            sessionState.draining.set(false);
            if (!sessionState.pendingByToken.isEmpty())
            {
                scheduleDrain(sessionId, sessionState);
            }
            else
            {
                cleanupSessionState(sessionId, sessionState);
            }
        }
    }

    private PendingNotification pollPending(SessionDeliveryState sessionState)
    {
        for (Entry<String, PendingNotification> entry : sessionState.pendingByToken.entrySet())
        {
            PendingNotification value = entry.getValue();
            if (value != null && sessionState.pendingByToken.remove(entry.getKey(), value))
            {
                return value;
            }
        }
        return null;
    }

    private void rememberDeliveredFingerprint(PendingNotification pending)
    {
        if (isTerminal(pending.state))
        {
            lastSentFingerprints.remove(pending.fingerprintKey);
        }
        else
        {
            lastSentFingerprints.put(pending.fingerprintKey, pending.fingerprint);
        }
    }

    private void clearSessionFingerprints(String sessionId)
    {
        if (!hasText(sessionId))
        {
            return;
        }
        String prefix = sessionId + "|"; //$NON-NLS-1$
        for (String key : lastSentFingerprints.keySet())
        {
            if (key.startsWith(prefix))
            {
                lastSentFingerprints.remove(key);
            }
        }
    }

    private void cleanupSessionState(String sessionId, SessionDeliveryState sessionState)
    {
        if (sessionState != null && !sessionState.draining.get() && sessionState.pendingByToken.isEmpty())
        {
            sessionDeliveryStates.remove(sessionId, sessionState);
        }
    }

    private String fingerprintKey(OperationProgressState state)
    {
        return state.getSessionId() + "|" + progressTokenKey(state.getProgressToken()); //$NON-NLS-1$
    }

    private String progressTokenKey(Object progressToken)
    {
        return progressToken != null
                ? progressToken.getClass().getName() + ":" + String.valueOf(progressToken) //$NON-NLS-1$
                : "null"; //$NON-NLS-1$
    }

    private void logDropped(OperationProgressState state, String reason)
    {
        Activator.logWarning("[diag] progress event dropped before SSE delivery: " + describeState(state) //$NON-NLS-1$
                + ", reason=" + reason); //$NON-NLS-1$
    }

    private String describeState(OperationProgressState state)
    {
        String toolName = state != null && hasText(state.getToolName()) ? state.getToolName() : "unknown"; //$NON-NLS-1$
        String sessionId = state != null && hasText(state.getSessionId()) ? state.getSessionId() : "unknown"; //$NON-NLS-1$
        String requestId = state != null && hasText(state.getRequestId()) ? state.getRequestId() : "unknown"; //$NON-NLS-1$
        String operationId = state != null && hasText(state.getOperationId()) ? state.getOperationId() : "unknown"; //$NON-NLS-1$
        String stage = state != null && hasText(state.getStage()) ? state.getStage() : "unknown"; //$NON-NLS-1$
        return "tool=" + toolName + ", sessionId=" + sessionId + ", requestId=" + requestId + ", operationId=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                + operationId + ", stage=" + stage; //$NON-NLS-1$
    }

    private static ExecutorService createDefaultDeliveryExecutor()
    {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0, MAX_DELIVERY_THREADS, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "MCP-Progress-SSE-" + System.currentTimeMillis()); //$NON-NLS-1$
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class NotificationFingerprint
    {
        private final double progress;
        private final Double total;
        private final String message;

        private NotificationFingerprint(double progress, Double total, String message)
        {
            this.progress = progress;
            this.total = total;
            this.message = message;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (!(obj instanceof NotificationFingerprint))
            {
                return false;
            }
            NotificationFingerprint other = (NotificationFingerprint) obj;
            return Double.doubleToLongBits(progress) == Double.doubleToLongBits(other.progress)
                    && Objects.equals(total, other.total) && Objects.equals(message, other.message);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(progress, total, message);
        }
    }

    private static final class SessionDeliveryState
    {
        private final ConcurrentMap<String, PendingNotification> pendingByToken = new ConcurrentHashMap<>();
        private final AtomicBoolean draining = new AtomicBoolean();
    }

    private static final class PendingNotification
    {
        private final OperationProgressState state;
        private final String fingerprintKey;
        private final NotificationFingerprint fingerprint;

        private PendingNotification(OperationProgressState state, String fingerprintKey,
                NotificationFingerprint fingerprint)
        {
            this.state = state;
            this.fingerprintKey = fingerprintKey;
            this.fingerprint = fingerprint;
        }
    }
}
