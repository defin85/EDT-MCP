/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.List;
import java.util.Map;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.testruns.UnitTestRunRecord;
import com.ditrix.edt.mcp.server.testruns.UnitTestRunStore;
import com.ditrix.edt.mcp.server.tools.IMcpTool;

/**
 * Read-only lookup surface for retained unit-test run reports.
 */
public class GetTestRunReportTool implements IMcpTool
{
    public static final String NAME = "get_test_run_report"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Get retained summary or report payload for a completed unit-test run by stable runId."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("runId", "Stable unit-test run identifier returned by run_unit_tests.", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringEnumProperty("format", "Report format: summary, manifest, or junit (default: summary).", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of(UnitTestRunRecord.FORMAT_SUMMARY, UnitTestRunRecord.FORMAT_MANIFEST,
                                UnitTestRunRecord.FORMAT_JUNIT))
                .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String runId = JsonUtils.extractStringArgument(params, "runId"); //$NON-NLS-1$
        String format = JsonUtils.extractStringArgument(params, "format"); //$NON-NLS-1$
        if (runId == null || runId.isBlank())
        {
            return ToolResult.error("runId is required").toJson(); //$NON-NLS-1$
        }

        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").toJson(); //$NON-NLS-1$
        }

        UnitTestRunStore store = server.getUnitTestRunStore();
        UnitTestRunRecord record = store != null ? store.get(runId) : null;
        if (record == null)
        {
            return ToolResult.success()
                    .put("found", false) //$NON-NLS-1$
                    .put("requestedRunId", runId) //$NON-NLS-1$
                    .put("message", "Test run not found or expired: " + runId) //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        return record.toReportResult(format).toJson();
    }
}
