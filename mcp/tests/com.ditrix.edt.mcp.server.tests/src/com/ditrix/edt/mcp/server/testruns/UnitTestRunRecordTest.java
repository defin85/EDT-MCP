/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UnitTestRunRecordTest
{
    @Test
    public void testFailedSummaryMarksSuccessFalse()
    {
        UnitTestRunRecord record = buildRecord("failed", "failed"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(record.toSummaryResult().toJson()).getAsJsonObject();

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("failed").getAsInt() > 0); //$NON-NLS-1$
    }

    @Test
    public void testManifestRetrievalRemainsSuccessfulForFailedRun()
    {
        UnitTestRunRecord record = buildRecord("failed", "failed"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(record.toReportResult(UnitTestRunRecord.FORMAT_MANIFEST).toJson())
                .getAsJsonObject();

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("found").getAsBoolean()); //$NON-NLS-1$
    }

    @Test
    public void testPassedSummaryKeepsSuccessTrue()
    {
        UnitTestRunRecord record = buildRecord("passed", "passed"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = JsonParser.parseString(record.toSummaryResult().toJson()).getAsJsonObject();

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("passed").getAsInt() > 0); //$NON-NLS-1$
    }

    private UnitTestRunRecord buildRecord(String runId, String status)
    {
        return new UnitTestRunRecord(runId, "yaxunit", "TestConfiguration", "app-1", "Demo", "all", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                status, "message", 2, "passed".equals(status) ? 2 : 1, "failed".equals(status) ? 1 : 0, 0, 0, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                1000L, Instant.now(), Instant.now().plusSeconds(60),
                List.of(UnitTestRunRecord.FORMAT_SUMMARY, UnitTestRunRecord.FORMAT_JUNIT), //$NON-NLS-1$
                List.of("tests.Module.Fail"), Map.of("scope", "all"), "<testsuite tests=\"2\"/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
