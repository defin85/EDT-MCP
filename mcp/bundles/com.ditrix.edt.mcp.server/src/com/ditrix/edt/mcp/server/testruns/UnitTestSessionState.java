/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

/**
 * Public lifecycle states for persistent unit-test sessions.
 */
public enum UnitTestSessionState
{
    STARTING("starting"), //$NON-NLS-1$
    READY("ready"), //$NON-NLS-1$
    BUSY("busy"), //$NON-NLS-1$
    STALE("stale"), //$NON-NLS-1$
    DEAD("dead"); //$NON-NLS-1$

    private final String wireValue;

    UnitTestSessionState(String wireValue)
    {
        this.wireValue = wireValue;
    }

    public String wireValue()
    {
        return wireValue;
    }

    public static UnitTestSessionState fromWireValue(String value)
    {
        if (value == null)
        {
            return null;
        }
        for (UnitTestSessionState state : values())
        {
            if (state.wireValue.equals(value))
            {
                return state;
            }
        }
        return null;
    }
}
