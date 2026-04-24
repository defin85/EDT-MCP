/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

/**
 * Public run_unit_tests warm-session routing outcomes.
 */
public enum UnitTestSessionRunOutcome
{
    COLD_STARTED("cold_started"), //$NON-NLS-1$
    REUSED("reused"), //$NON-NLS-1$
    RECYCLED("recycled"), //$NON-NLS-1$
    STALE_REJECTED("stale_rejected"); //$NON-NLS-1$

    private final String wireValue;

    UnitTestSessionRunOutcome(String wireValue)
    {
        this.wireValue = wireValue;
    }

    public String wireValue()
    {
        return wireValue;
    }
}
