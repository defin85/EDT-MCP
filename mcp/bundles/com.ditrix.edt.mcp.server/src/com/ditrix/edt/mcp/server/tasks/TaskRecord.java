/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

import java.time.Instant;
import java.util.Objects;

import com.ditrix.edt.mcp.server.progress.OperationProgressState;

/**
 * Mutable runtime record for one task.
 */
public final class TaskRecord
{
    private final String taskId;
    private final String requestId;
    private final String sessionId;
    private final String toolName;
    private final String projectName;
    private final TaskSchedulingKey schedulingKey;
    private final Long ttl;
    private final int pollInterval;
    private final Instant createdAt;

    private TaskStatus status;
    private String statusMessage;
    private Instant lastUpdatedAt;
    private TaskExecutionHandle executionHandle;
    private TaskResultEnvelope resultEnvelope;
    private OperationProgressState progressState;

    public TaskRecord(String taskId, String requestId, String sessionId, String toolName, String projectName,
            TaskSchedulingKey schedulingKey, Long ttl, int pollInterval)
    {
        this.taskId = Objects.requireNonNull(taskId);
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.projectName = projectName;
        this.schedulingKey = schedulingKey != null ? schedulingKey : TaskSchedulingKey.none();
        this.ttl = ttl;
        this.pollInterval = pollInterval;
        this.createdAt = Instant.now();
        this.status = TaskStatus.WORKING;
        this.statusMessage = "The operation is now in progress."; //$NON-NLS-1$
        this.lastUpdatedAt = createdAt;
    }

    public synchronized String getTaskId()
    {
        return taskId;
    }

    public synchronized String getRequestId()
    {
        return requestId;
    }

    public synchronized String getSessionId()
    {
        return sessionId;
    }

    public synchronized String getToolName()
    {
        return toolName;
    }

    public synchronized String getProjectName()
    {
        return projectName;
    }

    public synchronized TaskSchedulingKey getSchedulingKey()
    {
        return schedulingKey;
    }

    public synchronized TaskStatus getStatus()
    {
        return status;
    }

    public synchronized String getStatusMessage()
    {
        return statusMessage;
    }

    public synchronized Instant getCreatedAt()
    {
        return createdAt;
    }

    public synchronized Instant getLastUpdatedAt()
    {
        return lastUpdatedAt;
    }

    public synchronized Long getTtl()
    {
        return ttl;
    }

    public synchronized int getPollInterval()
    {
        return pollInterval;
    }

    public synchronized TaskExecutionHandle getExecutionHandle()
    {
        return executionHandle;
    }

    public synchronized OperationProgressState getProgressState()
    {
        return progressState;
    }

    public synchronized TaskResultEnvelope getResultEnvelope()
    {
        return resultEnvelope;
    }

    public synchronized void setExecutionHandle(TaskExecutionHandle executionHandle)
    {
        this.executionHandle = executionHandle;
    }

    public synchronized void markWorking(String statusMessage)
    {
        if (status.isTerminal())
        {
            return;
        }
        this.status = TaskStatus.WORKING;
        if (hasText(statusMessage))
        {
            this.statusMessage = statusMessage;
        }
        touch();
    }

    public synchronized void updateProgress(OperationProgressState progressState)
    {
        if (progressState == null)
        {
            return;
        }
        this.progressState = progressState;
        if (!status.isTerminal())
        {
            this.status = TaskStatus.WORKING;
            if (hasText(progressState.getMessage()))
            {
                this.statusMessage = progressState.getMessage();
            }
            else if (hasText(progressState.getStage()))
            {
                this.statusMessage = progressState.getStage();
            }
        }
        touch();
    }

    public synchronized boolean markCompleted(TaskResultEnvelope resultEnvelope, String statusMessage)
    {
        if (status.isTerminal())
        {
            return false;
        }
        this.status = TaskStatus.COMPLETED;
        this.resultEnvelope = resultEnvelope;
        if (hasText(statusMessage))
        {
            this.statusMessage = statusMessage;
        }
        touch();
        notifyAll();
        return true;
    }

    public synchronized boolean markFailed(TaskResultEnvelope resultEnvelope, String statusMessage)
    {
        if (status.isTerminal())
        {
            return false;
        }
        this.status = TaskStatus.FAILED;
        this.resultEnvelope = resultEnvelope;
        if (hasText(statusMessage))
        {
            this.statusMessage = statusMessage;
        }
        touch();
        notifyAll();
        return true;
    }

    public synchronized boolean markCancelled(TaskResultEnvelope resultEnvelope, String statusMessage)
    {
        if (status.isTerminal())
        {
            return false;
        }
        this.status = TaskStatus.CANCELLED;
        this.resultEnvelope = resultEnvelope;
        this.statusMessage = hasText(statusMessage) ? statusMessage : "Task was cancelled."; //$NON-NLS-1$
        touch();
        notifyAll();
        return true;
    }

    public synchronized void awaitTerminal() throws InterruptedException
    {
        while (!status.isTerminal())
        {
            wait();
        }
    }

    public synchronized boolean isExpired(Instant now)
    {
        if (ttl == null || ttl.longValue() <= 0 || !status.isTerminal())
        {
            return false;
        }
        return lastUpdatedAt.plusMillis(ttl.longValue()).isBefore(now);
    }

    public synchronized boolean belongsToSession(String currentSessionId)
    {
        if (!hasText(sessionId))
        {
            return !hasText(currentSessionId);
        }
        return sessionId.equals(currentSessionId);
    }

    private void touch()
    {
        this.lastUpdatedAt = Instant.now();
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
