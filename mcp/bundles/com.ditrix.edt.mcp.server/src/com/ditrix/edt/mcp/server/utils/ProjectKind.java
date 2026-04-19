package com.ditrix.edt.mcp.server.utils;

/**
 * Stable project-kind vocabulary exposed to MCP clients.
 */
public enum ProjectKind
{
    CONFIGURATION("configuration"), //$NON-NLS-1$
    EXTENSION("extension"), //$NON-NLS-1$
    UNKNOWN("unknown"); //$NON-NLS-1$

    private final String wireValue;

    ProjectKind(String wireValue)
    {
        this.wireValue = wireValue;
    }

    public String getWireValue()
    {
        return wireValue;
    }
}
