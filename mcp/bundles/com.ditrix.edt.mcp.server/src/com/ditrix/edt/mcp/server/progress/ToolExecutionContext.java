/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import com.ditrix.edt.mcp.server.tasks.TaskCancellationToken;

/**
 * Immutable request-scoped runtime context for the currently executing tool.
 */
public final class ToolExecutionContext
{
    public static final String TRANSPORT_MODE_JSON = "json"; //$NON-NLS-1$
    public static final String TRANSPORT_MODE_SSE = "sse"; //$NON-NLS-1$

    private final String requestId;
    private final String toolName;
    private final String sessionId;
    private final Object progressToken;
    private final boolean acceptsSse;
    private final String transportMode;
    private final String operationId;
    private final TaskCancellationToken cancellationToken;

    public ToolExecutionContext(String requestId, String toolName, String sessionId, Object progressToken,
            boolean acceptsSse, String transportMode, String operationId, TaskCancellationToken cancellationToken)
    {
        this.requestId = requestId;
        this.toolName = toolName;
        this.sessionId = sessionId;
        this.progressToken = progressToken;
        this.acceptsSse = acceptsSse;
        this.transportMode = transportMode;
        this.operationId = operationId;
        this.cancellationToken = cancellationToken;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public String getToolName()
    {
        return toolName;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public Object getProgressToken()
    {
        return progressToken;
    }

    public boolean isAcceptsSse()
    {
        return acceptsSse;
    }

    public String getTransportMode()
    {
        return transportMode;
    }

    public String getOperationId()
    {
        return operationId;
    }

    public TaskCancellationToken getCancellationToken()
    {
        return cancellationToken;
    }
}
