/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.ditrix.edt.mcp.server.protocol.jsonrpc.JsonRpcError;
import com.google.gson.JsonParser;

/**
 * Tests for {@link TaskRegistry} scheduling and retention behavior.
 */
public class TaskRegistryTest
{
    private TaskRegistry registry;

    @Before
    public void setUp()
    {
        registry = new TaskRegistry();
    }

    @Test
    public void testProjectScopedTasksConflictOnlyWithinSameProject()
    {
        TaskRecord running = registry.createToolTask("req-1", "session-1", "update_database", Long.valueOf(60000),
                "ProjectA", TaskSchedulingKey.projectScoped("ProjectA"));

        assertSame(running, registry.findConflictingTask(TaskSchedulingKey.projectScoped("ProjectA")));
        assertNull(registry.findConflictingTask(TaskSchedulingKey.projectScoped("ProjectB")));
    }

    @Test
    public void testWorkspaceWideTaskConflictsWithProjectScopedTask()
    {
        TaskRecord workspaceTask = registry.createToolTask("req-1", "session-1", "clean_project", Long.valueOf(60000),
                null, TaskSchedulingKey.workspaceWide());

        assertSame(workspaceTask, registry.findConflictingTask(TaskSchedulingKey.projectScoped("ProjectA")));
        assertSame(workspaceTask, registry.findConflictingTask(TaskSchedulingKey.workspaceWide()));
    }

    @Test
    public void testTerminalTaskRetentionUsesLastUpdatedAt() throws Exception
    {
        TaskRecord task = registry.createToolTask("req-1", "session-1", "update_database", Long.valueOf(10),
                "ProjectA", TaskSchedulingKey.projectScoped("ProjectA"));

        Thread.sleep(20);
        registry.markCompleted(task.getTaskId(),
                TaskResultEnvelope.success(JsonParser.parseString("{\"ok\":true}")), "done");

        registry.cleanupExpired();
        assertNotNull("Freshly completed task should still be retained",
                registry.getTask(task.getTaskId(), "session-1"));

        Thread.sleep(20);
        registry.cleanupExpired();
        assertNull("Task should expire after ttl since terminal update",
                registry.getTask(task.getTaskId(), "session-1"));
    }

    @Test
    public void testTaskInfoIncludesProgressAndResultAvailability()
    {
        TaskRecord task = registry.createToolTask("req-1", "session-1", "update_database", Long.valueOf(60000),
                "ProjectA", TaskSchedulingKey.projectScoped("ProjectA"));
        task.updateProgress(new com.ditrix.edt.mcp.server.progress.OperationProgressState("op-1",
                "update_database", "sync", "Halfway there", Double.valueOf(5), Double.valueOf(10), false,
                com.ditrix.edt.mcp.server.progress.OperationProgressState.STATUS_RUNNING, java.time.Instant.now(),
                java.time.Instant.now(), 3L, "req-1", "session-1", "progress-1", false, java.util.Map.of(),
                java.util.List.of()));
        task.markFailed(TaskResultEnvelope.error(new JsonRpcError(-32603, "boom")), "boom");

        com.ditrix.edt.mcp.server.protocol.jsonrpc.TaskInfo info =
                new com.ditrix.edt.mcp.server.protocol.jsonrpc.TaskInfo(task);

        assertEquals("update_database", info.getToolName());
        assertEquals("ProjectA", info.getProjectName());
        assertEquals("sync", info.getStage());
        assertEquals(Double.valueOf(5), info.getProgress());
        assertEquals(Double.valueOf(10), info.getTotal());
        assertEquals(Boolean.FALSE, info.getIndeterminate());
        assertEquals(Boolean.TRUE, info.getResultAvailable());
    }
}
