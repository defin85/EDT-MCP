/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.time.Instant;

/**
 * Immutable progress event snapshot for a long-running operation.
 */
public final class ProgressEvent
{
    private final Instant timestamp;
    private final String stage;
    private final String message;
    private final Double progress;
    private final Double total;

    public ProgressEvent(Instant timestamp, String stage, String message, Double progress, Double total)
    {
        this.timestamp = timestamp;
        this.stage = stage;
        this.message = message;
        this.progress = progress;
        this.total = total;
    }

    public Instant getTimestamp()
    {
        return timestamp;
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
}
