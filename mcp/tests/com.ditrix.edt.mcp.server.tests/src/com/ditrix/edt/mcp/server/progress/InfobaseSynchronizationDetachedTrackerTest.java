/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import static org.junit.Assert.*;

import org.junit.Test;

import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;

/**
 * Tests for detached infobase synchronization projection mapping.
 */
public class InfobaseSynchronizationDetachedTrackerTest
{
    @Test
    public void testCreateProjectionUsesAuthoritativeSynchronizationState()
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("op-1", "update_database", "waiting_for_edt", "Waiting for EDT", null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        OperationProgressState handoffState = reporter.snapshot();

        InfobaseSynchronizationDetachedTracker.DetachedProjection projection = InfobaseSynchronizationDetachedTracker
                .createProjection(handoffState, "DemoProject", "app-1", "DemoApp", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        InfobaseSynchronizationState.SYNCHRONIZING, InfobaseEqualityState.NOT_EQUAL, false, false);

        assertNotNull(projection);
        assertTrue(projection.isAuthoritative());
        assertEquals("infobase_sync_synchronizing", projection.getStage()); //$NON-NLS-1$
        assertEquals("BEING_UPDATED", projection.getDetails().get("derivedUpdateState")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("SYNCHRONIZING", projection.getDetails().get("synchronizationState")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("NOT_EQUAL", projection.getDetails().get("equalityState")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testCreateProjectionKeepsPendingSnapshotBeforeSynchronizationStateIsVisible()
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("op-2", "update_database", "update_start", "Starting update", null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        reporter.indeterminate("waiting_for_edt", "Waiting for EDT synchronization to finish"); //$NON-NLS-1$ //$NON-NLS-2$
        reporter.stage("Экспорт файла: Ext\\\\ParentConfigurations.bin", //$NON-NLS-1$
                "Экспорт файла: Ext\\\\ParentConfigurations.bin"); //$NON-NLS-1$
        OperationProgressState handoffState = reporter.snapshot();

        InfobaseSynchronizationDetachedTracker.DetachedProjection projection = InfobaseSynchronizationDetachedTracker
                .createProjection(handoffState, "DemoProject", "app-2", "DemoApp", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        InfobaseSynchronizationState.SYNCHRONIZED, InfobaseEqualityState.NOT_EQUAL, false, true);

        assertNotNull(projection);
        assertFalse(projection.isAuthoritative());
        assertEquals("infobase_sync_pending", projection.getStage()); //$NON-NLS-1$
        assertEquals(Boolean.TRUE, projection.getDetails().get("pendingSyncState")); //$NON-NLS-1$
        assertEquals("Экспорт файла: Ext\\\\ParentConfigurations.bin", projection.getDetails().get("handoffStage")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("INCREMENTAL_UPDATE_REQUIRED", projection.getDetails().get("derivedUpdateState")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testCreateProjectionStopsAfterAuthoritativeSyncEnds()
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("op-3", "update_database", "waiting_for_edt", "Waiting for EDT", null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        OperationProgressState handoffState = reporter.snapshot();

        InfobaseSynchronizationDetachedTracker.DetachedProjection projection = InfobaseSynchronizationDetachedTracker
                .createProjection(handoffState, "DemoProject", "app-3", "DemoApp", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        InfobaseSynchronizationState.SYNCHRONIZED, InfobaseEqualityState.EQUAL, true, true);

        assertNull(projection);
    }
}
