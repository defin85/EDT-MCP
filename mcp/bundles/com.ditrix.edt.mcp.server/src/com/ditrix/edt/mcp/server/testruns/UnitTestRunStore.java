/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory retention store for completed test-run summaries and reports.
 */
public final class UnitTestRunStore
{
    public static final long DEFAULT_TTL_MS = 15 * 60 * 1000L;

    private final ConcurrentMap<String, UnitTestRunRecord> records = new ConcurrentHashMap<>();

    public void put(UnitTestRunRecord record)
    {
        cleanupExpired();
        if (record != null && record.getRunId() != null && !record.getRunId().isBlank())
        {
            records.put(record.getRunId(), record);
        }
    }

    public UnitTestRunRecord get(String runId)
    {
        cleanupExpired();
        if (runId == null || runId.isBlank())
        {
            return null;
        }
        return records.get(runId);
    }

    public void cleanupExpired()
    {
        Instant now = Instant.now();
        for (UnitTestRunRecord record : records.values())
        {
            if (record != null && record.isExpired(now))
            {
                records.remove(record.getRunId(), record);
            }
        }
    }

    public void clear()
    {
        records.clear();
    }
}
