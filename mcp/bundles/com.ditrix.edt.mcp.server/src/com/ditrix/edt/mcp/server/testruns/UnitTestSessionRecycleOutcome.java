/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

/**
 * Public recycle_test_session outcome values.
 */
public enum UnitTestSessionRecycleOutcome
{
    MARKED_STALE("marked_stale"), //$NON-NLS-1$
    TERMINATED("terminated"), //$NON-NLS-1$
    REPLACED("replaced"); //$NON-NLS-1$

    private final String wireValue;

    UnitTestSessionRecycleOutcome(String wireValue)
    {
        this.wireValue = wireValue;
    }

    public String wireValue()
    {
        return wireValue;
    }
}
