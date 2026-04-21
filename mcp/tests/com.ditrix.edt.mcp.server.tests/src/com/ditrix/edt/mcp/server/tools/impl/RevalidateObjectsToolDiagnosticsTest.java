/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;

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
}
