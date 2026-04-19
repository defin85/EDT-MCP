/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

/**
 * Lifecycle handle for runtime tracking resources attached to an active operation.
 */
public interface OperationTrackerHandle
{
    /**
     * Releases listeners, background threads, and other resources held by the tracker.
     */
    void dispose();
}
