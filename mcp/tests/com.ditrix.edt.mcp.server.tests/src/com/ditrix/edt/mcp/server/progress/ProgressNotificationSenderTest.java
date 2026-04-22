/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * Tests for async/coalesced SSE progress delivery.
 */
public class ProgressNotificationSenderTest
{
    @Test
    public void testOnOperationUpdatedDoesNotWaitForBlockingSseWrite() throws Exception
    {
        BlockingSseSessionRegistry registry = new BlockingSseSessionRegistry();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ProgressNotificationSender sender = new ProgressNotificationSender(registry, executor);
        try
        {
            long startedAt = System.nanoTime();
            sender.onOperationUpdated(createState("op-1", "refresh", "Refreshing project from disk")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue("Progress delivery must stay async for tool execution", elapsedMs < 250); //$NON-NLS-1$
            assertTrue(registry.firstSendStarted.await(2, TimeUnit.SECONDS));
            assertEquals(1, registry.sendCalls.get());
        }
        finally
        {
            registry.allowSends.countDown();
            sender.shutdown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testPendingEventsAreCoalescedPerProgressToken() throws Exception
    {
        BlockingSseSessionRegistry registry = new BlockingSseSessionRegistry();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ProgressNotificationSender sender = new ProgressNotificationSender(registry, executor);
        try
        {
            sender.onOperationUpdated(createState("op-2", "refresh", "first")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            assertTrue(registry.firstSendStarted.await(2, TimeUnit.SECONDS));

            sender.onOperationUpdated(createState("op-2", "refresh", "second")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            sender.onOperationUpdated(createState("op-2", "refresh", "third")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            registry.allowSends.countDown();
            waitForSendCount(registry, 2);

            assertEquals(2, registry.sendCalls.get());
            assertTrue(registry.payloads.get(0).contains("\"message\":\"first\"")); //$NON-NLS-1$
            assertTrue(registry.payloads.get(1).contains("\"message\":\"third\"")); //$NON-NLS-1$
        }
        finally
        {
            registry.allowSends.countDown();
            sender.shutdown();
            executor.shutdownNow();
        }
    }

    private static OperationProgressState createState(String operationId, String stage, String message)
    {
        Instant now = Instant.now();
        return new OperationProgressState(operationId, "revalidate_objects", stage, message, Double.valueOf(1), //$NON-NLS-1$
                Double.valueOf(10), false, OperationProgressState.STATUS_RUNNING, now, now, 0L, "req-1", //$NON-NLS-1$
                "session-1", Integer.valueOf(7), false, Map.of(), List.of()); //$NON-NLS-1$
    }

    private static void waitForSendCount(BlockingSseSessionRegistry registry, int expectedCount) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline)
        {
            if (registry.sendCalls.get() >= expectedCount)
            {
                return;
            }
            Thread.sleep(20);
        }
        fail("Timed out waiting for send count " + expectedCount); //$NON-NLS-1$
    }

    private static final class BlockingSseSessionRegistry extends SseSessionRegistry
    {
        private final CountDownLatch firstSendStarted = new CountDownLatch(1);
        private final CountDownLatch allowSends = new CountDownLatch(1);
        private final AtomicInteger sendCalls = new AtomicInteger();
        private final CopyOnWriteArrayList<String> payloads = new CopyOnWriteArrayList<>();

        @Override
        public boolean sendEvent(String sessionId, String eventName, String dataJson)
        {
            payloads.add(dataJson);
            if (sendCalls.incrementAndGet() == 1)
            {
                firstSendStarted.countDown();
            }
            try
            {
                assertTrue(allowSends.await(5, TimeUnit.SECONDS));
                return true;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                fail("Interrupted while simulating SSE write"); //$NON-NLS-1$
                return false;
            }
        }
    }
}
