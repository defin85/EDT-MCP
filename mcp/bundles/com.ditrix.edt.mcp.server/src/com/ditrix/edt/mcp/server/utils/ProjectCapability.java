package com.ditrix.edt.mcp.server.utils;

/**
 * Stable project capability categories exposed to MCP clients.
 */
public enum ProjectCapability
{
    METADATA_READ("metadataRead"), //$NON-NLS-1$
    MODULE_READ("moduleRead"), //$NON-NLS-1$
    MUTATION_REFACTOR("mutationRefactor"), //$NON-NLS-1$
    RUNTIME_APPLICATION("runtimeApplication"); //$NON-NLS-1$

    private final String wireValue;

    ProjectCapability(String wireValue)
    {
        this.wireValue = wireValue;
    }

    public String getWireValue()
    {
        return wireValue;
    }
}
