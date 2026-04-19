/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class BlockingOperationDiagnosticsTest
{
    @Test
    public void testProjectBuildBlockedAddsExactHintForSingleMatchingOperation()
    {
        McpServer server = new McpServer();
        server.setActiveOperation(createReporter("op-1", "clean_project", Map.of("projectName", "Demo"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        String json = BlockingOperationDiagnostics.projectBuildBlocked(server, "Demo", "Project build is in progress") //$NON-NLS-1$ //$NON-NLS-2$
                .toJson();

        JsonObject hint = parseBlockingHint(json);
        assertEquals(BlockingOperationDiagnostics.REASON_PROJECT_BUILD_IN_PROGRESS,
                hint.get("reasonCode").getAsString()); //$NON-NLS-1$
        assertEquals("project", hint.get("scope").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Demo", hint.get("projectName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("op-1", hint.get("operationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("get_operation_snapshot", hint.get("pollTool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testProjectBuildBlockedOmitsExactHintWhenCorrelationIsAmbiguous()
    {
        McpServer server = new McpServer();
        server.setActiveOperation(createReporter("op-1", "clean_project", Map.of("projectName", "Demo"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        server.setActiveOperation(createReporter("op-2", "revalidate_objects", Map.of("projectName", "Demo"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        String json = BlockingOperationDiagnostics.projectBuildBlocked(server, "Demo", "Project build is in progress") //$NON-NLS-1$ //$NON-NLS-2$
                .toJson();

        JsonObject hint = parseBlockingHint(json);
        assertFalse(hint.has("operationId")); //$NON-NLS-1$
        assertFalse(hint.has("pollTool")); //$NON-NLS-1$
    }

    @Test
    public void testProjectBuildBlockedMatchesProjectListsFromWorkspaceWideOperation()
    {
        McpServer server = new McpServer();
        server.setActiveOperation(createReporter("op-1", "clean_project", Map.of("projects", List.of( //$NON-NLS-1$ //$NON-NLS-2$
                Map.of("projectName", "Alpha"), Map.of("projectName", "Beta"))))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        String json = BlockingOperationDiagnostics.projectBuildBlocked(server, "Beta", "Project build is in progress") //$NON-NLS-1$ //$NON-NLS-2$
                .toJson();

        JsonObject hint = parseBlockingHint(json);
        assertEquals("op-1", hint.get("operationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testApplicationSyncBlockedAddsApplicationScopeIdentifiers()
    {
        McpServer server = new McpServer();
        server.setActiveOperation(createReporter("op-7", "update_database", Map.of("projectName", "Demo", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "applicationId", "app-1", "applicationName", "Main"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        String json = BlockingOperationDiagnostics.applicationSyncBlocked(server, "Demo", "app-1", "Main", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "Application synchronization is already in progress").toJson(); //$NON-NLS-1$

        JsonObject hint = parseBlockingHint(json);
        assertEquals(BlockingOperationDiagnostics.REASON_APPLICATION_SYNC_IN_PROGRESS,
                hint.get("reasonCode").getAsString()); //$NON-NLS-1$
        assertEquals("application", hint.get("scope").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("app-1", hint.get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Main", hint.get("applicationName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("op-7", hint.get("operationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private OperationProgressReporter createReporter(String operationId, String toolName, Map<String, Object> details)
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start(operationId, toolName, "validation", "Working", null, null, null, details); //$NON-NLS-1$ //$NON-NLS-2$
        return reporter;
    }

    private JsonObject parseBlockingHint(String json)
    {
        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        return payload.getAsJsonObject("_meta").getAsJsonObject(McpConstants.META_BLOCKING_OPERATION); //$NON-NLS-1$
    }
}
