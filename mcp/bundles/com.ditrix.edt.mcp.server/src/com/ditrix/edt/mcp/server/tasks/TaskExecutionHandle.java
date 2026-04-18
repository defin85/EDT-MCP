/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

import java.util.concurrent.Future;

/**
 * Tracks the executing future and cancellation token for a task.
 */
public final class TaskExecutionHandle
{
    private final TaskCancellationToken cancellationToken;
    private volatile Future<?> future;

    public TaskExecutionHandle(TaskCancellationToken cancellationToken)
    {
        this.cancellationToken = cancellationToken;
    }

    public TaskCancellationToken getCancellationToken()
    {
        return cancellationToken;
    }

    public void setFuture(Future<?> future)
    {
        this.future = future;
    }

    public void cancelExecution()
    {
        cancellationToken.cancel();
        Future<?> currentFuture = future;
        if (currentFuture != null)
        {
            currentFuture.cancel(true);
        }
    }
}
