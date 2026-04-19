/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

/**
 * Tests for detached progress snapshot semantics.
 */
public class OperationProgressReporterTest
{
    @Test
    public void testDetachedCopyPreservesOperationIdentityAndDropsProgressToken()
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("op-1", "clean_project", "clean_build", "Running clean build", "req-1", "session-1", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                "progress-1"); //$NON-NLS-1$
        reporter.progress(5, Double.valueOf(10), "Halfway"); //$NON-NLS-1$

        OperationProgressReporter detached = reporter.detachedCopy("derived_data", //$NON-NLS-1$
                "Detached derived data continues", Map.of("trackingType", "derived_data")); //$NON-NLS-1$ //$NON-NLS-2$
        OperationProgressState snapshot = detached.snapshot();

        assertNotNull(snapshot);
        assertEquals("op-1", snapshot.getOperationId()); //$NON-NLS-1$
        assertEquals("clean_project", snapshot.getToolName()); //$NON-NLS-1$
        assertTrue(snapshot.isDetached());
        assertNull(snapshot.getProgressToken());
        assertNull(snapshot.getProgress());
        assertNull(snapshot.getTotal());
        assertTrue(snapshot.isIndeterminate());
        assertEquals("derived_data", snapshot.getStage()); //$NON-NLS-1$
        assertEquals("Detached derived data continues", snapshot.getMessage()); //$NON-NLS-1$
        assertEquals("derived_data", snapshot.getDetails().get("trackingType")); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(snapshot.getRecentEvents().isEmpty());
    }

    @Test
    public void testDetachedUpdatePublishesStructuredDetails()
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("op-2", "update_database", "update_start", "Starting update", "req-2", "session-2", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                "progress-2"); //$NON-NLS-1$

        reporter.detachedUpdate("infobase_sync_synchronizing", "Detached infobase synchronization continues", //$NON-NLS-1$ //$NON-NLS-2$
                Map.of("trackingType", "infobase_sync", "synchronizationState", "SYNCHRONIZING")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        OperationProgressState snapshot = reporter.snapshot();
        assertNotNull(snapshot);
        assertTrue(snapshot.isDetached());
        assertEquals("infobase_sync", snapshot.getDetails().get("trackingType")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("SYNCHRONIZING", snapshot.getDetails().get("synchronizationState")); //$NON-NLS-1$ //$NON-NLS-2$
        assertNull(snapshot.getProgressToken());
        assertEquals(OperationProgressState.STATUS_RUNNING, snapshot.getStatus());
    }
}
