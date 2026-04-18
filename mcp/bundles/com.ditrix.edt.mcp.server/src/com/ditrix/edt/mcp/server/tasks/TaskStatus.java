/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

/**
 * Supported MCP task states.
 */
public enum TaskStatus
{
    WORKING("working", false), //$NON-NLS-1$
    INPUT_REQUIRED("input_required", false), //$NON-NLS-1$
    COMPLETED("completed", true), //$NON-NLS-1$
    FAILED("failed", true), //$NON-NLS-1$
    CANCELLED("cancelled", true); //$NON-NLS-1$

    private final String wireValue;
    private final boolean terminal;

    TaskStatus(String wireValue, boolean terminal)
    {
        this.wireValue = wireValue;
        this.terminal = terminal;
    }

    public String getWireValue()
    {
        return wireValue;
    }

    public boolean isTerminal()
    {
        return terminal;
    }

    @Override
    public String toString()
    {
        return wireValue;
    }
}
