/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

/**
 * Per-thread holder for {@link ToolExecutionContext}.
 */
public final class ToolExecutionContextHolder
{
    private static final ThreadLocal<ToolExecutionContext> HOLDER = new ThreadLocal<>();

    private ToolExecutionContextHolder()
    {
        // Utility class
    }

    public static void set(ToolExecutionContext context)
    {
        HOLDER.set(context);
    }

    public static ToolExecutionContext get()
    {
        return HOLDER.get();
    }

    public static void clear()
    {
        HOLDER.remove();
    }
}
