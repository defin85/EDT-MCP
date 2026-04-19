/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.OperationProgressState;
import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.impl.GetOperationSnapshotTool;

/**
 * Builds normalized busy-state diagnostics with optional exact-operation hints.
 */
public final class BlockingOperationDiagnostics
{
    public static final String REASON_PROJECT_BUILD_IN_PROGRESS = "project_build_in_progress"; //$NON-NLS-1$
    public static final String REASON_APPLICATION_SYNC_IN_PROGRESS = "application_sync_in_progress"; //$NON-NLS-1$

    private BlockingOperationDiagnostics()
    {
        // Utility class
    }

    public static ToolResult projectBuildBlocked(McpServer server, String projectName, String message)
    {
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("reasonCode", REASON_PROJECT_BUILD_IN_PROGRESS); //$NON-NLS-1$
        hint.put("scope", "project"); //$NON-NLS-1$ //$NON-NLS-2$
        hint.put("projectName", projectName); //$NON-NLS-1$
        attachExactCorrelation(hint, correlate(server, snapshot -> matchesProject(snapshot, projectName)));
        return ToolResult.error(message).putMeta(McpConstants.META_BLOCKING_OPERATION, hint);
    }

    public static ToolResult applicationSyncBlocked(McpServer server, String projectName, String applicationId,
            String applicationName, String message)
    {
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("reasonCode", REASON_APPLICATION_SYNC_IN_PROGRESS); //$NON-NLS-1$
        hint.put("scope", "application"); //$NON-NLS-1$ //$NON-NLS-2$
        hint.put("projectName", projectName); //$NON-NLS-1$
        hint.put("applicationId", applicationId); //$NON-NLS-1$
        if (hasText(applicationName))
        {
            hint.put("applicationName", applicationName); //$NON-NLS-1$
        }
        attachExactCorrelation(hint, correlate(server,
                snapshot -> matchesApplication(snapshot, projectName, applicationId)));
        return ToolResult.error(message).putMeta(McpConstants.META_BLOCKING_OPERATION, hint);
    }

    private static void attachExactCorrelation(Map<String, Object> hint, OperationProgressState correlated)
    {
        if (correlated == null || !hasText(correlated.getOperationId()))
        {
            return;
        }
        hint.put("operationId", correlated.getOperationId()); //$NON-NLS-1$
        hint.put("pollTool", GetOperationSnapshotTool.NAME); //$NON-NLS-1$
    }

    private static OperationProgressState correlate(McpServer server, Predicate<OperationProgressState> predicate)
    {
        if (server == null || predicate == null)
        {
            return null;
        }

        List<OperationProgressState> matches = server.getTrackedOperationSnapshots().stream()
                .filter(BlockingOperationDiagnostics::isRunning)
                .filter(predicate)
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean matchesProject(OperationProgressState snapshot, String projectName)
    {
        if (snapshot == null || !hasText(projectName))
        {
            return false;
        }

        Map<String, Object> details = snapshot.getDetails();
        if (matchesText(details.get("projectName"), projectName)) //$NON-NLS-1$
        {
            return true;
        }

        Object projects = details.get("projects"); //$NON-NLS-1$
        if (!(projects instanceof List<?>))
        {
            return false;
        }

        for (Object projectEntry : (List<?>) projects)
        {
            if (projectEntry instanceof Map<?, ?> map && matchesText(map.get("projectName"), projectName)) //$NON-NLS-1$
            {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesApplication(OperationProgressState snapshot, String projectName, String applicationId)
    {
        if (!matchesProject(snapshot, projectName) || !hasText(applicationId))
        {
            return false;
        }
        return matchesText(snapshot.getDetails().get("applicationId"), applicationId); //$NON-NLS-1$
    }

    private static boolean isRunning(OperationProgressState snapshot)
    {
        return snapshot != null && OperationProgressState.STATUS_RUNNING.equals(snapshot.getStatus());
    }

    private static boolean matchesText(Object value, String expected)
    {
        return hasText(expected) && value != null && expected.equals(String.valueOf(value));
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
