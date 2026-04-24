/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

/**
 * Stable public field and tool names for persistent unit-test sessions.
 */
public final class UnitTestSessionToolContract
{
    public static final String TOOL_PREPARE_TEST_SESSION = "prepare_test_session"; //$NON-NLS-1$
    public static final String TOOL_GET_TEST_SESSION_STATUS = "get_test_session_status"; //$NON-NLS-1$
    public static final String TOOL_RECYCLE_TEST_SESSION = "recycle_test_session"; //$NON-NLS-1$

    public static final String FIELD_SESSION_ID = "sessionId"; //$NON-NLS-1$
    public static final String FIELD_SESSION_MODE = "sessionMode"; //$NON-NLS-1$
    public static final String FIELD_SESSION_OUTCOME = "sessionOutcome"; //$NON-NLS-1$
    public static final String FIELD_RECYCLE_OUTCOME = "recycleOutcome"; //$NON-NLS-1$
    public static final String FIELD_STATE = "state"; //$NON-NLS-1$
    public static final String FIELD_STALE_REASON = "staleReason"; //$NON-NLS-1$
    public static final String FIELD_OWNER_SESSION_ID = "ownerSessionId"; //$NON-NLS-1$
    public static final String FIELD_REUSE_SCOPE = "reuseScope"; //$NON-NLS-1$

    private UnitTestSessionToolContract()
    {
    }
}
