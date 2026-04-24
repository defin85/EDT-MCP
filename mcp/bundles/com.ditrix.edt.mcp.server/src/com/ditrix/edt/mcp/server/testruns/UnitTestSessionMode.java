/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

/**
 * Public run_unit_tests session selection policy.
 */
public enum UnitTestSessionMode
{
    COLD("cold"), //$NON-NLS-1$
    PREFER_WARM("prefer_warm"), //$NON-NLS-1$
    REQUIRE_WARM("require_warm"), //$NON-NLS-1$
    RECYCLE_THEN_RUN("recycle_then_run"); //$NON-NLS-1$

    private final String wireValue;

    UnitTestSessionMode(String wireValue)
    {
        this.wireValue = wireValue;
    }

    public String wireValue()
    {
        return wireValue;
    }

    public boolean allowsColdFallback()
    {
        return COLD.equals(this) || PREFER_WARM.equals(this) || RECYCLE_THEN_RUN.equals(this);
    }

    public boolean requiresReusableWarmSession()
    {
        return REQUIRE_WARM.equals(this);
    }

    public static UnitTestSessionMode fromWireValue(String value)
    {
        if (value == null || value.isBlank())
        {
            return COLD;
        }
        String normalized = value.trim().toLowerCase();
        for (UnitTestSessionMode mode : values())
        {
            if (mode.wireValue.equals(normalized))
            {
                return mode;
            }
        }
        return null;
    }
}
