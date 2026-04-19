/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2026 Diversus (https://github.com/Diversus23)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link InfobaseSyncUtils}.
 */
public class InfobaseSyncUtilsTest
{
    @Test
    public void testValidateRequestedUpdateModeRejectsIncrementalWhenFullSyncRequired()
    {
        String message = InfobaseSyncUtils.validateRequestedUpdateMode(false, "FULL_UPDATE_REQUIRED"); //$NON-NLS-1$

        assertNotNull(message);
        assertTrue(message.contains("fullUpdate=true")); //$NON-NLS-1$
    }

    @Test
    public void testValidateRequestedUpdateModeAllowsIncrementalWhenDiffSyncIsEnough()
    {
        assertNull(InfobaseSyncUtils.validateRequestedUpdateMode(false, "INCREMENTAL_UPDATE_REQUIRED")); //$NON-NLS-1$
        assertNull(InfobaseSyncUtils.validateRequestedUpdateMode(false, "UPDATED")); //$NON-NLS-1$
    }

    @Test
    public void testValidateRequestedUpdateModeAllowsExplicitFullUpdate()
    {
        assertNull(InfobaseSyncUtils.validateRequestedUpdateMode(true, "FULL_UPDATE_REQUIRED")); //$NON-NLS-1$
    }
}
