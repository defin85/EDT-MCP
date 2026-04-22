/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class UnitTestRunStoreTest
{
    private UnitTestRunStore store;

    @Before
    public void setUp()
    {
        store = new UnitTestRunStore();
    }

    @Test
    public void testRetainsFreshRun()
    {
        UnitTestRunRecord record = buildRecord("run-1", Instant.now().plusMillis(1000)); //$NON-NLS-1$

        store.put(record);

        assertNotNull(store.get("run-1")); //$NON-NLS-1$
    }

    @Test
    public void testCleanupExpiresRun()
    {
        UnitTestRunRecord record = buildRecord("run-2", Instant.now().plusMillis(10)); //$NON-NLS-1$
        store.put(record);

        try
        {
            Thread.sleep(20);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        store.cleanupExpired();
        assertNull(store.get("run-2")); //$NON-NLS-1$
    }

    private UnitTestRunRecord buildRecord(String runId, Instant expiresAt)
    {
        return new UnitTestRunRecord(runId, "yaxunit", "TestConfiguration", "app-1", "Demo", "all", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                "passed", "ok", 2, 2, 0, 0, 0, 1000L, Instant.now(), expiresAt, //$NON-NLS-1$ //$NON-NLS-2$
                List.of(UnitTestRunRecord.FORMAT_SUMMARY, UnitTestRunRecord.FORMAT_JUNIT), List.of(), Map.of(),
                "<testsuite tests=\"2\"/>"); //$NON-NLS-1$
    }
}
