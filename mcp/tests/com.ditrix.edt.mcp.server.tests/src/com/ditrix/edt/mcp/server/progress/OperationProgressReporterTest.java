/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void testSnapshotDoesNotWaitForBlockingListener() throws Exception
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("op-3", "revalidate_objects", "prepare", "Preparing refresh", "req-3", "session-3", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                "progress-3"); //$NON-NLS-1$

        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);
        reporter.setStateListener(state -> {
            listenerEntered.countDown();
            awaitLatch(releaseListener, "listener release"); //$NON-NLS-1$
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<OperationProgressState> updateFuture = executor.submit(
                    () -> reporter.stage("refresh", "Refreshing project from disk")); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(listenerEntered.await(2, TimeUnit.SECONDS));

            Future<OperationProgressState> snapshotFuture = executor.submit(reporter::snapshot);
            OperationProgressState snapshot = snapshotFuture.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(snapshot);
            assertEquals("refresh", snapshot.getStage()); //$NON-NLS-1$

            releaseListener.countDown();
            assertNotNull(updateFuture.get(2, TimeUnit.SECONDS));
        }
        finally
        {
            releaseListener.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitLatch(CountDownLatch latch, String name)
    {
        try
        {
            assertTrue("Timed out waiting for " + name, latch.await(5, TimeUnit.SECONDS)); //$NON-NLS-1$
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for " + name); //$NON-NLS-1$
        }
    }
}
