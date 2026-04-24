/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

/**
 * Machine-readable reasons why a persistent unit-test session cannot be reused safely.
 */
public enum UnitTestSessionStaleReason
{
    WORKSPACE_CHANGED("workspace_changed"), //$NON-NLS-1$
    RUNTIME_TARGET_CHANGED("runtime_target_changed"), //$NON-NLS-1$
    INFOBASE_SYNC_PERFORMED("infobase_sync_performed"), //$NON-NLS-1$
    HEARTBEAT_LOST("heartbeat_lost"), //$NON-NLS-1$
    EXPLICIT_RECYCLE("explicit_recycle"), //$NON-NLS-1$
    PROVIDER_ERROR("provider_error"); //$NON-NLS-1$

    private final String wireValue;

    UnitTestSessionStaleReason(String wireValue)
    {
        this.wireValue = wireValue;
    }

    public String wireValue()
    {
        return wireValue;
    }

    public static UnitTestSessionStaleReason fromWireValue(String value)
    {
        if (value == null)
        {
            return null;
        }
        for (UnitTestSessionStaleReason reason : values())
        {
            if (reason.wireValue.equals(value))
            {
                return reason;
            }
        }
        return null;
    }
}
