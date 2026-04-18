/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation token for task-backed execution.
 */
public final class TaskCancellationToken
{
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);

    public boolean cancel()
    {
        return cancellationRequested.compareAndSet(false, true);
    }

    public boolean isCancellationRequested()
    {
        return cancellationRequested.get();
    }
}
