/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol;

import static org.junit.Assert.*;

import org.junit.Test;

import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.progress.OperationProgressState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for detached continuation metadata helpers.
 */
public class DetachedContinuationMetaTest
{
    @Test
    public void testAttachAddsContinuationHintToExistingMeta()
    {
        JsonObject payload = JsonParser.parseString(
                "{\"content\":[{\"type\":\"text\",\"text\":\"Task was cancelled\"}],\"_meta\":{\"existing\":true}}") //$NON-NLS-1$
                .getAsJsonObject();

        JsonObject attached = DetachedContinuationMeta.attach(payload, "task-1").getAsJsonObject(); //$NON-NLS-1$

        assertTrue(attached.has("_meta")); //$NON-NLS-1$
        JsonObject meta = attached.getAsJsonObject("_meta"); //$NON-NLS-1$
        assertTrue(meta.get("existing").getAsBoolean()); //$NON-NLS-1$
        assertTrue(meta.has(McpConstants.META_DETACHED_CONTINUATION));
        JsonObject continuation = meta.getAsJsonObject(McpConstants.META_DETACHED_CONTINUATION);
        assertEquals("task-1", continuation.get("operationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(continuation.get("detached").getAsBoolean()); //$NON-NLS-1$
        assertEquals("get_operation_snapshot", continuation.get("pollTool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testShouldExposeForCancellationOnlyAfterRealEdtWorkStarted()
    {
        assertTrue(DetachedContinuationMeta.shouldExposeForCancellation("clean_project", "clean_build")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(DetachedContinuationMeta.shouldExposeForCancellation("revalidate_objects", "waiting_for_build")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(DetachedContinuationMeta.shouldExposeForCancellation("update_database", "waiting_for_edt")); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(DetachedContinuationMeta.shouldExposeForCancellation("clean_project", "validation")); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(DetachedContinuationMeta.shouldExposeForCancellation("update_database", "sync_state_check")); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(DetachedContinuationMeta.shouldExposeForCancellation("unknown_tool", "clean_build")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testShouldExposeForCancellationAcceptsBuildProgressStagesForCleanProject()
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("task-1", "clean_project", "validation", "Preparing clean", null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        reporter.stage("Обновление: /DORolf.", "Обновление: /DORolf."); //$NON-NLS-1$ //$NON-NLS-2$

        OperationProgressState snapshot = reporter.snapshot();
        assertTrue(DetachedContinuationMeta.shouldExposeForCancellation("clean_project", snapshot)); //$NON-NLS-1$
    }

    @Test
    public void testShouldExposeForCancellationKeepsUpdateDatabaseConservativeForLocalizedStage()
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("task-2", "update_database", "update_start", "Starting update", null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        reporter.stage("Экспорт файла: Ext\\\\ParentConfigurations.bin", //$NON-NLS-1$
                "Экспорт файла: Ext\\\\ParentConfigurations.bin"); //$NON-NLS-1$

        OperationProgressState snapshot = reporter.snapshot();
        assertFalse(DetachedContinuationMeta.shouldExposeForCancellation("update_database", snapshot)); //$NON-NLS-1$
    }
}
