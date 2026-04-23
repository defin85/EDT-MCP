/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.ToolResult;

/**
 * Retained summary and report payloads for a completed unit-test run.
 */
public final class UnitTestRunRecord
{
    public static final String FORMAT_SUMMARY = "summary"; //$NON-NLS-1$
    public static final String FORMAT_JUNIT = "junit"; //$NON-NLS-1$
    public static final String FORMAT_MANIFEST = "manifest"; //$NON-NLS-1$

    private final String runId;
    private final String provider;
    private final String projectName;
    private final String applicationId;
    private final String applicationName;
    private final String scope;
    private final String status;
    private final String message;
    private final int total;
    private final int passed;
    private final int failed;
    private final int skipped;
    private final int errored;
    private final long durationMs;
    private final Instant completedAt;
    private final Instant expiresAt;
    private final List<String> reportFormats;
    private final List<String> failedTestsSample;
    private final Map<String, Object> filters;
    private final String junitReport;
    private final int junitReportBytes;

    public UnitTestRunRecord(String runId, String provider, String projectName, String applicationId,
            String applicationName, String scope, String status, String message, int total, int passed, int failed,
            int skipped, int errored, long durationMs, Instant completedAt, Instant expiresAt,
            List<String> reportFormats, List<String> failedTestsSample, Map<String, Object> filters, String junitReport)
    {
        this.runId = runId;
        this.provider = provider;
        this.projectName = projectName;
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.scope = scope;
        this.status = status;
        this.message = message;
        this.total = total;
        this.passed = passed;
        this.failed = failed;
        this.skipped = skipped;
        this.errored = errored;
        this.durationMs = durationMs;
        this.completedAt = completedAt;
        this.expiresAt = expiresAt;
        this.reportFormats = reportFormats != null ? List.copyOf(reportFormats) : List.of(FORMAT_SUMMARY);
        this.failedTestsSample = failedTestsSample != null ? List.copyOf(failedTestsSample) : List.of();
        this.filters = filters != null ? Map.copyOf(filters) : Map.of();
        this.junitReport = junitReport;
        this.junitReportBytes = junitReport != null ? junitReport.length() : 0;
    }

    public String getRunId()
    {
        return runId;
    }

    public Instant getExpiresAt()
    {
        return expiresAt;
    }

    public boolean isExpired(Instant now)
    {
        return expiresAt != null && now != null && !expiresAt.isAfter(now);
    }

    public ToolResult toSummaryResult()
    {
        ToolResult result = ToolResult.success()
                .put("success", isSuccessfulStatus()) //$NON-NLS-1$
                .put("runId", runId) //$NON-NLS-1$
                .put("provider", provider) //$NON-NLS-1$
                .put("projectName", projectName) //$NON-NLS-1$
                .put("applicationId", applicationId) //$NON-NLS-1$
                .put("scope", scope) //$NON-NLS-1$
                .put("status", status) //$NON-NLS-1$
                .put("message", message) //$NON-NLS-1$
                .put("total", total) //$NON-NLS-1$
                .put("passed", passed) //$NON-NLS-1$
                .put("failed", failed) //$NON-NLS-1$
                .put("skipped", skipped) //$NON-NLS-1$
                .put("errored", errored) //$NON-NLS-1$
                .put("durationMs", durationMs) //$NON-NLS-1$
                .put("reportFormats", reportFormats) //$NON-NLS-1$
                .put("completedAt", completedAt != null ? completedAt.toString() : null) //$NON-NLS-1$
                .put("expiresAt", expiresAt != null ? expiresAt.toString() : null) //$NON-NLS-1$
                .put("filters", new LinkedHashMap<>(filters)); //$NON-NLS-1$
        if (applicationName != null && !applicationName.isBlank())
        {
            result.put("applicationName", applicationName); //$NON-NLS-1$
        }
        if (!failedTestsSample.isEmpty())
        {
            result.put("failedTestsSample", new ArrayList<>(failedTestsSample)); //$NON-NLS-1$
        }
        return result;
    }

    public ToolResult toReportResult(String format)
    {
        String normalizedFormat = format == null || format.isBlank() ? FORMAT_SUMMARY : format;
        if (FORMAT_SUMMARY.equals(normalizedFormat))
        {
            return toSummaryResult()
                    .put("found", true) //$NON-NLS-1$
                    .put("format", FORMAT_SUMMARY); //$NON-NLS-1$
        }
        if (FORMAT_MANIFEST.equals(normalizedFormat))
        {
            return ToolResult.success()
                    .put("found", true) //$NON-NLS-1$
                    .put("runId", runId) //$NON-NLS-1$
                    .put("format", FORMAT_MANIFEST) //$NON-NLS-1$
                    .put("provider", provider) //$NON-NLS-1$
                    .put("reportFormats", reportFormats) //$NON-NLS-1$
                    .put("defaultFormat", FORMAT_SUMMARY) //$NON-NLS-1$
                    .put("expiresAt", expiresAt != null ? expiresAt.toString() : null) //$NON-NLS-1$
                    .put("junitXmlAvailable", junitReport != null) //$NON-NLS-1$
                    .put("junitXmlBytes", junitReportBytes); //$NON-NLS-1$
        }
        if (FORMAT_JUNIT.equals(normalizedFormat))
        {
            ToolResult result = toSummaryResult()
                    .put("found", true) //$NON-NLS-1$
                    .put("format", FORMAT_JUNIT) //$NON-NLS-1$
                    .put("report", junitReport != null ? junitReport : ""); //$NON-NLS-1$ //$NON-NLS-2$
            if (junitReport == null)
            {
                result.put("available", false); //$NON-NLS-1$
                result.put("message", "JUnit report is not retained for runId=" + runId); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
                result.put("available", true); //$NON-NLS-1$
            }
            return result;
        }
        return ToolResult.error("Unsupported report format: " + normalizedFormat) //$NON-NLS-1$
                .put("runId", runId) //$NON-NLS-1$
                .put("supportedFormats", reportFormats); //$NON-NLS-1$
    }

    private boolean isSuccessfulStatus()
    {
        return "passed".equals(status); //$NON-NLS-1$
    }
}
