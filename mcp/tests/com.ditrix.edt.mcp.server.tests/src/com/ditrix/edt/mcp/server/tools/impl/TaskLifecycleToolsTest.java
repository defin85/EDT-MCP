/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.protocol.McpConstants;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.CreateTaskResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.JsonRpcError;
import com.ditrix.edt.mcp.server.tasks.TaskRecord;
import com.ditrix.edt.mcp.server.tasks.TaskRegistry;
import com.ditrix.edt.mcp.server.tasks.TaskResultEnvelope;
import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for tool-level task lifecycle wrappers.
 */
public class TaskLifecycleToolsTest
{
    private McpServer server;

    @Before
    public void setUp() throws Exception
    {
        server = new McpServer();
        installTestActivator(server);
    }

    @After
    public void tearDown()
    {
        ToolExecutionContextHolder.clear();
        clearTestActivator();
    }

    @Test
    public void testCreateTaskResultCarriesLifecycleEnvelope()
    {
        TaskRecord task = createTask("session-1", "update_database"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject payload = new Gson().toJsonTree(new CreateTaskResult(task)).getAsJsonObject();

        assertTrue(payload.has("task")); //$NON-NLS-1$
        JsonObject lifecycle = payload.getAsJsonObject("lifecycle"); //$NON-NLS-1$
        assertEquals(task.getTaskId(), lifecycle.get("taskId").getAsString()); //$NON-NLS-1$
        assertEquals(task.getTaskId(), lifecycle.get("operationId").getAsString()); //$NON-NLS-1$
        assertEquals("accepted", lifecycle.get("outcome").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("wait_task", lifecycle.get("preferredFollowUpTool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(lifecycle.getAsJsonObject("result").get("available").getAsBoolean()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testListTasksShowsOnlyCurrentSessionTasks()
    {
        TaskRecord ownTask = createTask("session-1", "update_database"); //$NON-NLS-1$ //$NON-NLS-2$
        createTask("session-2", "clean_project"); //$NON-NLS-1$ //$NON-NLS-2$
        setToolContext("session-1"); //$NON-NLS-1$

        JsonObject payload = parse(new ListTasksTool().execute(Map.of()));

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(1, payload.get("count").getAsInt()); //$NON-NLS-1$
        assertEquals(ownTask.getTaskId(),
                payload.getAsJsonArray("tasks").get(0).getAsJsonObject().get("taskId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject lifecycle = payload.getAsJsonArray("lifecycles").get(0).getAsJsonObject(); //$NON-NLS-1$
        assertEquals("working", lifecycle.get("state").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(lifecycle.get("terminal").getAsBoolean()); //$NON-NLS-1$
    }

    @Test
    public void testGetTaskResultRejectsUnknownTask()
    {
        setToolContext("session-1"); //$NON-NLS-1$

        JsonObject payload = parse(new GetTaskResultTool().execute(Map.of("taskId", "missing"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("Task not found", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("missing", payload.get("requestedTaskId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testGetTaskResultPreservesSameSessionOwnership()
    {
        TaskRecord task = createTask("session-1", "update_database"); //$NON-NLS-1$ //$NON-NLS-2$
        setToolContext("session-2"); //$NON-NLS-1$

        JsonObject payload = parse(new GetTaskResultTool().execute(Map.of("taskId", task.getTaskId()))); //$NON-NLS-1$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("Task not found", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testGetTaskResultReturnsActiveSnapshotWithoutWaiting()
    {
        TaskRecord task = createTask("session-1", "update_database"); //$NON-NLS-1$ //$NON-NLS-2$
        setToolContext("session-1"); //$NON-NLS-1$

        JsonObject payload = parse(new GetTaskResultTool().execute(Map.of("taskId", task.getTaskId()))); //$NON-NLS-1$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("active", payload.get("outcome").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(payload.get("terminal").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("resultAvailable").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.getAsJsonObject("lifecycle").getAsJsonObject("result").get("available") //$NON-NLS-1$ //$NON-NLS-2$
                .getAsBoolean());
        assertEquals(WaitTaskTool.NAME,
                payload.getAsJsonObject("lifecycle").get("preferredFollowUpTool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testWaitTaskReturnsTimeoutSnapshot()
    {
        TaskRecord task = createTask("session-1", "update_database"); //$NON-NLS-1$ //$NON-NLS-2$
        setToolContext("session-1"); //$NON-NLS-1$

        JsonObject payload = parse(new WaitTaskTool()
                .execute(Map.of("taskId", task.getTaskId(), "timeoutSeconds", "0"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("timeout", payload.get("outcome").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(payload.get("timeout").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("terminal").getAsBoolean()); //$NON-NLS-1$
        assertEquals(WaitTaskTool.NAME, payload.get("preferredFollowUpTool").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testWaitTaskReturnsRetainedTerminalPayload()
    {
        TaskRecord task = createTask("session-1", "update_database"); //$NON-NLS-1$ //$NON-NLS-2$
        server.getTaskRegistry().markCompleted(task.getTaskId(),
                TaskResultEnvelope.success(JsonParser.parseString("{\"ok\":true}")), "done"); //$NON-NLS-1$ //$NON-NLS-2$
        setToolContext("session-1"); //$NON-NLS-1$

        JsonObject payload = parse(new WaitTaskTool()
                .execute(Map.of("taskId", task.getTaskId(), "timeoutSeconds", "1"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("terminal", payload.get("outcome").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(payload.get("timeout").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("terminal").getAsBoolean()); //$NON-NLS-1$
        assertTrue(payload.get("resultAvailable").getAsBoolean()); //$NON-NLS-1$
        JsonObject lifecycleResult = payload.getAsJsonObject("lifecycle").getAsJsonObject("result"); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(lifecycleResult.get("available").getAsBoolean()); //$NON-NLS-1$
        assertEquals("toolPayload", lifecycleResult.get("kind").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(payload.getAsJsonObject("toolPayload").get("ok").getAsBoolean()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(task.getTaskId(),
                payload.getAsJsonObject("_meta").getAsJsonObject(McpConstants.META_RELATED_TASK) //$NON-NLS-1$
                        .get("taskId").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testGetTaskResultLifecycleCarriesJsonRpcErrorMetadata()
    {
        TaskRecord task = createTask("session-1", "update_database"); //$NON-NLS-1$ //$NON-NLS-2$
        server.getTaskRegistry().markFailed(task.getTaskId(),
                TaskResultEnvelope.error(new JsonRpcError(McpConstants.ERROR_INTERNAL, "boom")), "boom"); //$NON-NLS-1$ //$NON-NLS-2$
        setToolContext("session-1"); //$NON-NLS-1$

        JsonObject payload = parse(new GetTaskResultTool().execute(Map.of("taskId", task.getTaskId()))); //$NON-NLS-1$

        JsonObject lifecycleResult = payload.getAsJsonObject("lifecycle").getAsJsonObject("result"); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(lifecycleResult.get("available").getAsBoolean()); //$NON-NLS-1$
        assertEquals("jsonRpcError", lifecycleResult.get("kind").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject error = lifecycleResult.getAsJsonObject("error"); //$NON-NLS-1$
        assertEquals(McpConstants.ERROR_INTERNAL, error.get("code").getAsInt()); //$NON-NLS-1$
        assertEquals("boom", error.get("message").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private TaskRecord createTask(String sessionId, String toolName)
    {
        TaskRegistry registry = server.getTaskRegistry();
        return registry.createToolTask("req-1", sessionId, toolName, Long.valueOf(60000), //$NON-NLS-1$
                "TestConfiguration", TaskSchedulingKey.projectScoped("TestConfiguration")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void setToolContext(String sessionId)
    {
        ToolExecutionContextHolder.set(new ToolExecutionContext("req-1", "task_lifecycle_test", sessionId, null, //$NON-NLS-1$ //$NON-NLS-2$
                false, ToolExecutionContext.TRANSPORT_MODE_JSON, "op-test", null)); //$NON-NLS-1$
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private void installTestActivator(McpServer server) throws ReflectiveOperationException
    {
        Activator activator = new Activator();
        Field pluginField = Activator.class.getDeclaredField("plugin"); //$NON-NLS-1$
        pluginField.setAccessible(true);
        pluginField.set(null, activator);

        Field serverField = Activator.class.getDeclaredField("mcpServer"); //$NON-NLS-1$
        serverField.setAccessible(true);
        serverField.set(activator, server);
    }

    private void clearTestActivator()
    {
        try
        {
            Field pluginField = Activator.class.getDeclaredField("plugin"); //$NON-NLS-1$
            pluginField.setAccessible(true);
            pluginField.set(null, null);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }
}
