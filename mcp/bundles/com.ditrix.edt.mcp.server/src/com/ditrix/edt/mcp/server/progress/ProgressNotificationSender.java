/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.google.gson.JsonObject;

/**
 * Sends MCP notifications/progress over registered SSE sessions.
 */
public class ProgressNotificationSender
{
    private final SseSessionRegistry sseSessionRegistry;
    private final ConcurrentMap<String, NotificationFingerprint> lastSentFingerprints = new ConcurrentHashMap<>();

    public ProgressNotificationSender(SseSessionRegistry sseSessionRegistry)
    {
        this.sseSessionRegistry = Objects.requireNonNull(sseSessionRegistry);
    }

    public void onOperationUpdated(OperationProgressState state)
    {
        if (state == null || state.getProgressToken() == null || !hasText(state.getSessionId()))
        {
            return;
        }

        String key = state.getSessionId() + "|" + state.getProgressToken(); //$NON-NLS-1$
        NotificationFingerprint fingerprint = new NotificationFingerprint(extractProgressValue(state),
                extractTotalValue(state), buildMessage(state));
        NotificationFingerprint previous = lastSentFingerprints.get(key);
        if (fingerprint.equals(previous))
        {
            if (isTerminal(state))
            {
                lastSentFingerprints.remove(key);
            }
            return;
        }

        boolean sent = sseSessionRegistry.sendEvent(state.getSessionId(), "message", buildPayload(state)); //$NON-NLS-1$
        if (sent)
        {
            if (isTerminal(state))
            {
                lastSentFingerprints.remove(key);
            }
            else
            {
                lastSentFingerprints.put(key, fingerprint);
            }
        }
        else if (isTerminal(state))
        {
            lastSentFingerprints.remove(key);
        }
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
}
