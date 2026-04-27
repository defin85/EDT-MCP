/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RevalidateObjectsToolDiagnosticsTest
{
    @Test
    public void testBuildDiagnosticLabelIncludesCorrelationFieldsForObjectScopedRequests()
    {
        RevalidateObjectsTool tool = new RevalidateObjectsTool();
        ToolExecutionContext context = new ToolExecutionContext("req-17", RevalidateObjectsTool.NAME, "session-1", null, //$NON-NLS-1$ //$NON-NLS-2$
                false, ToolExecutionContext.TRANSPORT_MODE_JSON, "op-77", null); //$NON-NLS-1$

        String label = tool.buildDiagnosticLabel(context, "Demo", false, List.of("Document.SalesOrder", //$NON-NLS-1$ //$NON-NLS-2$
                "Catalog.Products", "CommonModule.Common", "Enum.Status")); //$NON-NLS-1$ //$NON-NLS-2$

        assertTrue(label.contains("tool=revalidate_objects")); //$NON-NLS-1$
        assertTrue(label.contains("project=Demo")); //$NON-NLS-1$
        assertTrue(label.contains("requestId=req-17")); //$NON-NLS-1$
        assertTrue(label.contains("operationId=op-77")); //$NON-NLS-1$
        assertTrue(label.contains("objects=[Document.SalesOrder, Catalog.Products, CommonModule.Common] +1")); //$NON-NLS-1$
        assertTrue(label.contains("thread=" + Thread.currentThread().getName())); //$NON-NLS-1$
    }

    @Test
    public void testBuildDiagnosticLabelUsesFullMarkerAndNoneFallbacks()
    {
        RevalidateObjectsTool tool = new RevalidateObjectsTool();

        String label = tool.buildDiagnosticLabel(null, "Demo", true, List.of()); //$NON-NLS-1$

        assertTrue(label.contains("requestId=<none>")); //$NON-NLS-1$
        assertTrue(label.contains("operationId=<none>")); //$NON-NLS-1$
        assertTrue(label.contains("objects=full")); //$NON-NLS-1$
    }

    @Test
    public void testSafetyControlsAreExposedInSchema()
    {
        String schema = new RevalidateObjectsTool().getInputSchema();

        assertTrue(schema.contains("\"dryRun\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"timeoutSeconds\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"failFast\"")); //$NON-NLS-1$
    }

    @Test
    public void testDryRunPreflightDoesNotExecuteWhenProjectIsMissing()
    {
        JsonObject payload = parse(new RevalidateObjectsTool().execute(java.util.Map.of(
                "projectName", "MissingProjectForPreflight", //$NON-NLS-1$ //$NON-NLS-2$
                "objects", "[\"Catalog.Products\"]", //$NON-NLS-1$ //$NON-NLS-2$
                "dryRun", "true"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("executed").getAsBoolean()); //$NON-NLS-1$
        assertEquals("preflight", payload.get("mode").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject preflight = payload.getAsJsonObject("preflight"); //$NON-NLS-1$
        assertEquals("invalid", preflight.get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("object_validation", preflight.get("wouldSchedule").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testObjectScopedTimeoutFailsClosedAtPreflight()
    {
        RevalidateObjectsTool.PreflightResult preflight = new RevalidateObjectsTool().buildPreflight("Demo", //$NON-NLS-1$
                List.of("Catalog.Products"), false, false, new RevalidateObjectsTool.TimeoutRequest(true, false, 5)); //$NON-NLS-1$

        JsonObject payload = parse(preflight.toJson(false));

        assertFalse(payload.get("executed").getAsBoolean()); //$NON-NLS-1$
        JsonObject preflightJson = payload.getAsJsonObject("preflight"); //$NON-NLS-1$
        assertEquals("invalid", preflightJson.get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("unsupported", preflightJson.getAsJsonObject("safetySemantics").get("timeout").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
