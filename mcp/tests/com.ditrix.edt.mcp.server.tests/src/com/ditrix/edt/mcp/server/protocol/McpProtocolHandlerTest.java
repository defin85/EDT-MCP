/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.IMcpTool.TaskSupport;
import com.ditrix.edt.mcp.server.tools.McpToolRegistry;
import com.ditrix.edt.mcp.server.tools.impl.ApplyExtensionToInfobaseTool;
import com.ditrix.edt.mcp.server.tools.impl.CleanProjectTool;
import com.ditrix.edt.mcp.server.tools.impl.DebugLaunchTool;
import com.ditrix.edt.mcp.server.tools.impl.GetProblemSummaryTool;
import com.ditrix.edt.mcp.server.tools.impl.GetProjectErrorsTool;
import com.ditrix.edt.mcp.server.tools.impl.GetServerBuildInfoTool;
import com.ditrix.edt.mcp.server.tools.impl.GetTestRunReportTool;
import com.ditrix.edt.mcp.server.tools.impl.RevalidateObjectsTool;
import com.ditrix.edt.mcp.server.tools.impl.RunUnitTestsTool;
import com.ditrix.edt.mcp.server.tools.impl.UpdateDatabaseTool;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.ToolCallResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link McpProtocolHandler}.
 * Verifies JSON-RPC protocol handling for initialize, tools/list, and error cases.
 * <p>
 * Note: tools/call with successful execution cannot be fully tested without
 * OSGi runtime (Activator.getDefault() returns null), but error paths are testable.
 * </p>
 */
public class McpProtocolHandlerTest
{
    private McpProtocolHandler handler;
    private McpToolRegistry registry;
    private McpServer testServer;

    @Before
    public void setUp()
    {
        registry = McpToolRegistry.getInstance();
        registry.clear();
        handler = new McpProtocolHandler();
    }

    @After
    public void tearDown()
    {
        registry.clear();
        clearTestActivator();
        shutdownTestServer();
    }

    // === Initialize ===

    @Test
    public void testInitialize()
    {
        String request = buildJsonRpcRequest(1, "initialize", null);
        String response = handler.processRequest(request);

        assertNotNull(response);
        JsonObject json = parseResponse(response);
        assertEquals("2.0", json.get("jsonrpc").getAsString());
        assertNotNull(json.get("result"));

        JsonObject result = json.getAsJsonObject("result");
        assertNotNull("Should have protocolVersion", result.get("protocolVersion"));
        assertNotNull("Should have capabilities", result.get("capabilities"));
        assertNotNull("Should have serverInfo", result.get("serverInfo"));
        assertNotNull("Should advertise tasks capability",
            result.getAsJsonObject("capabilities").getAsJsonObject("tasks"));

        JsonObject serverInfo = result.getAsJsonObject("serverInfo");
        assertNotNull(serverInfo.get("name"));
        assertNotNull(serverInfo.get("version"));
    }

    @Test
    public void testInitializeEchosClientProtocolVersion()
    {
        // Per MCP spec: server must echo back client's requested protocol version
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"lmstudio\",\"version\":\"1.0.0\"}}}";
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        String echoed = json.getAsJsonObject("result").get("protocolVersion").getAsString();
        assertEquals("Server must echo back client's protocol version", "2025-06-18", echoed);
    }

    @Test
    public void testInitializeUsesOwnVersionWhenClientVersionMissing()
    {
        // When no protocolVersion in params, fall back to server's latest
        String request = buildJsonRpcRequest(1, "initialize", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        String version = json.getAsJsonObject("result").get("protocolVersion").getAsString();
        assertEquals(McpConstants.PROTOCOL_VERSION, version);
    }

    @Test
    public void testInitializePreservesRequestId()
    {
        String request = buildJsonRpcRequest(42, "initialize", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertEquals(42, json.get("id").getAsInt());
    }

    @Test
    public void testInitializeWithIdZeroPreservesIntegerType()
    {
        // LM Studio sends "id":0 - must not become "id":0.0 in response
        String request = buildJsonRpcRequest(0, "initialize", null);
        String response = handler.processRequest(request);

        assertNotNull(response);
        assertFalse("Response must not contain 0.0 as id", response.contains("\"id\":0.0"));
        JsonObject json = parseResponse(response);
        assertEquals(0, json.get("id").getAsInt());
    }

    @Test
    public void testInitializeStringId()
    {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":\"abc-123\",\"method\":\"initialize\"}";
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertEquals("abc-123", json.get("id").getAsString());
    }

    // === Initialized notification ===

    @Test
    public void testInitializedNotification()
    {
        String request = buildJsonRpcRequest(1, "notifications/initialized", null);
        String response = handler.processRequest(request);
        assertNull("notifications/initialized should return null (202 Accepted)", response);
    }

    // === Tools/List ===

    @Test
    public void testToolsListEmpty()
    {
        String request = buildJsonRpcRequest(1, "tools/list", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result");
        assertNotNull(result.get("tools"));
        assertEquals(0, result.getAsJsonArray("tools").size());
    }

    @Test
    public void testToolsListWithTools()
    {
        registry.register(new StubTool("tool_alpha", "Alpha tool", "{\"type\":\"object\"}", TaskSupport.FORBIDDEN));
        registry.register(new StubTool("tool_beta", "Beta tool",
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}", TaskSupport.OPTIONAL));

        String request = buildJsonRpcRequest(1, "tools/list", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result");
        assertEquals(2, result.getAsJsonArray("tools").size());

        // Verify tool entries have required fields
        for (JsonElement toolEl : result.getAsJsonArray("tools"))
        {
            JsonObject tool = toolEl.getAsJsonObject();
            assertNotNull("Tool should have name", tool.get("name"));
            assertNotNull("Tool should have description", tool.get("description"));
            assertNotNull("Tool should have inputSchema", tool.get("inputSchema"));
            assertNotNull("Tool should advertise task execution metadata", tool.getAsJsonObject("execution"));
            assertNotNull("Tool should advertise task support",
                tool.getAsJsonObject("execution").get("taskSupport"));
        }
    }

    @Test
    public void testToolsListReportsRealToolTaskPolicies()
    {
        registry.register(new GetServerBuildInfoTool());
        registry.register(new UpdateDatabaseTool());
        registry.register(new CleanProjectTool());
        registry.register(new RevalidateObjectsTool());
        registry.register(new DebugLaunchTool());
        registry.register(new RunUnitTestsTool());
        registry.register(new GetTestRunReportTool());
        registry.register(new GetProblemSummaryTool());
        registry.register(new GetProjectErrorsTool());

        String request = buildJsonRpcRequest(1, "tools/list", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result");
        Map<String, String> taskPolicies = new java.util.HashMap<>();
        for (JsonElement toolEl : result.getAsJsonArray("tools"))
        {
            JsonObject tool = toolEl.getAsJsonObject();
            taskPolicies.put(tool.get("name").getAsString(),
                tool.getAsJsonObject("execution").get("taskSupport").getAsString());
        }

        assertEquals("forbidden", taskPolicies.get(GetServerBuildInfoTool.NAME));
        assertEquals("optional", taskPolicies.get(UpdateDatabaseTool.NAME));
        assertEquals("optional", taskPolicies.get(CleanProjectTool.NAME));
        assertEquals("optional", taskPolicies.get(RevalidateObjectsTool.NAME));
        assertEquals("optional", taskPolicies.get(RunUnitTestsTool.NAME));
        assertEquals("forbidden", taskPolicies.get(GetTestRunReportTool.NAME));
        assertEquals("forbidden", taskPolicies.get(DebugLaunchTool.NAME));
        assertEquals("forbidden", taskPolicies.get(GetProblemSummaryTool.NAME));
        assertEquals("forbidden", taskPolicies.get(GetProjectErrorsTool.NAME));
    }

    // === Invalid Requests ===

    @Test
    public void testInvalidJsonRpcVersion()
    {
        String request = "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"initialize\"}";
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
        assertEquals(McpConstants.ERROR_INVALID_REQUEST,
            json.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testMissingJsonRpcVersion()
    {
        String request = "{\"id\":1,\"method\":\"initialize\"}";
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
    }

    @Test
    public void testMethodNotFound()
    {
        String request = buildJsonRpcRequest(1, "unknown/method", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
        assertEquals(McpConstants.ERROR_METHOD_NOT_FOUND,
            json.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testInvalidJson()
    {
        String response = handler.processRequest("not valid json {{{");
        // Should return an error response (either parse error or invalid request)
        assertNotNull(response);
        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
    }

    @Test
    public void testEmptyBody()
    {
        String response = handler.processRequest("");
        assertNotNull(response);
        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
    }

    @Test
    public void testNullBody()
    {
        String response = handler.processRequest(null);
        assertNotNull(response);
        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
    }

    // === Tools/Call Error Cases ===

    @Test
    public void testToolCallToolNotFound()
    {
        String request = buildToolCallRequest(1, "nonexistent_tool", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
        String message = json.getAsJsonObject("error").get("message").getAsString();
        assertTrue("Error should mention tool name", message.contains("nonexistent_tool"));
    }

    @Test
    public void testToolCallNullToolName()
    {
        String request = buildJsonRpcRequest(1, "tools/call",
            "{\"arguments\":{}}");
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull("Should return error for null tool name", json.get("error"));
    }

    @Test
    public void testToolCallRejectsSyncInvocationForTaskRequiredTool()
    {
        registry.register(new StubTool("task_required_tool", "Requires task", "{\"type\":\"object\"}", TaskSupport.REQUIRED));

        String request = buildToolCallRequest(1, "task_required_tool", null);
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
        assertEquals(McpConstants.ERROR_INVALID_PARAMS, json.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testToolCallRejectsTaskInvocationForForbiddenTool()
    {
        registry.register(new StubTool("sync_only_tool", "Sync only", "{\"type\":\"object\"}", TaskSupport.FORBIDDEN));

        String request = buildTaskToolCallRequest(1, "sync_only_tool", null, "{\"ttl\":60000}");
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
        assertEquals(McpConstants.ERROR_INVALID_PARAMS, json.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testToolCallRejectsTaskInvocationForDebugLaunch()
    {
        registry.register(new DebugLaunchTool());

        String request = buildTaskToolCallRequest(1, DebugLaunchTool.NAME,
            "{\"projectName\":\"TestConfiguration\",\"applicationId\":\"app-1\"}", "{\"ttl\":60000}");
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        assertNotNull(json.get("error"));
        assertEquals(McpConstants.ERROR_INVALID_PARAMS, json.getAsJsonObject("error").get("code").getAsInt());
        assertTrue(json.getAsJsonObject("error").get("message").getAsString().contains(DebugLaunchTool.NAME));
    }

    @Test
    public void testToolCallAutoPromotesBareUpdateDatabaseRequestIntoTask() throws Exception
    {
        registry.register(new StubTool(UpdateDatabaseTool.NAME, "Async-first", "{\"type\":\"object\"}", //$NON-NLS-1$ //$NON-NLS-2$
                TaskSupport.OPTIONAL));
        installTestActivator(createTaskCapableServer());

        String request = buildToolCallRequest(1, UpdateDatabaseTool.NAME,
                "{\"projectName\":\"TestConfiguration\",\"applicationId\":\"app-1\"}"); //$NON-NLS-1$ //$NON-NLS-2$
        String response = handler.processRequest(request, "session-1", false, "json"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result"); //$NON-NLS-1$
        assertNotNull(result);
        assertTrue(result.has("task")); //$NON-NLS-1$

        String taskId = result.getAsJsonObject("task").get("taskId").getAsString(); //$NON-NLS-1$ //$NON-NLS-2$
        String getRequest = buildJsonRpcRequest(2, "tasks/get", "{\"taskId\":\"" + taskId + "\"}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        JsonObject ownSession = parseResponse(handler.processRequest(getRequest, "session-1", false, "json")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(taskId, ownSession.getAsJsonObject("result").get("taskId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject foreignSession = parseResponse(handler.processRequest(getRequest, "session-2", false, "json")); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull(foreignSession.get("error")); //$NON-NLS-1$
        assertEquals(McpConstants.ERROR_INVALID_PARAMS,
                foreignSession.getAsJsonObject("error").get("code").getAsInt()); //$NON-NLS-1$

        String resultRequest = buildJsonRpcRequest(3, "tasks/result", "{\"taskId\":\"" + taskId + "\"}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        JsonObject taskResult = parseResponse(handler.processRequest(resultRequest, "session-1", false, "json")); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject taskPayload = taskResult.getAsJsonObject("result"); //$NON-NLS-1$
        assertNotNull(taskPayload);
        assertTrue(taskPayload.has("_meta")); //$NON-NLS-1$
        assertEquals(taskId,
                taskPayload.getAsJsonObject("_meta").getAsJsonObject(McpConstants.META_RELATED_TASK) //$NON-NLS-1$
                        .get("taskId").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testToolCallAutoPromotesBareApplyExtensionRequestIntoTask() throws Exception
    {
        registry.register(new StubTool(ApplyExtensionToInfobaseTool.NAME, "Async-first", "{\"type\":\"object\"}", //$NON-NLS-1$ //$NON-NLS-2$
                TaskSupport.OPTIONAL));
        installTestActivator(createTaskCapableServer());

        String request = buildToolCallRequest(1, ApplyExtensionToInfobaseTool.NAME,
                "{\"projectName\":\"EXT_001\",\"applicationId\":\"app-1\"}"); //$NON-NLS-1$ //$NON-NLS-2$
        String response = handler.processRequest(request, "session-1", false, "json"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result"); //$NON-NLS-1$
        assertNotNull(result);
        assertTrue(result.has("task")); //$NON-NLS-1$
    }

    @Test
    public void testToolCallAutoPromotesBareRunUnitTestsRequestIntoTask() throws Exception
    {
        registry.register(new StubTool(RunUnitTestsTool.NAME, "Async-first", "{\"type\":\"object\"}", //$NON-NLS-1$ //$NON-NLS-2$
                TaskSupport.OPTIONAL));
        installTestActivator(createTaskCapableServer());

        String request = buildToolCallRequest(1, RunUnitTestsTool.NAME,
                "{\"projectName\":\"TestConfiguration\",\"applicationId\":\"app-1\"}"); //$NON-NLS-1$ //$NON-NLS-2$
        String response = handler.processRequest(request, "session-1", false, "json"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result"); //$NON-NLS-1$
        assertNotNull(result);
        assertTrue(result.has("task")); //$NON-NLS-1$
    }

    @Test
    public void testToolCallKeepsPartialRevalidateObjectsSynchronousWithoutTask()
    {
        registry.register(new StubTool(RevalidateObjectsTool.NAME, "Revalidate", "{\"type\":\"object\"}", //$NON-NLS-1$ //$NON-NLS-2$
                TaskSupport.OPTIONAL));

        String request = buildToolCallRequest(1, RevalidateObjectsTool.NAME,
                "{\"projectName\":\"TestConfiguration\",\"objects\":[\"Document.SalesOrder\"]}"); //$NON-NLS-1$ //$NON-NLS-2$
        String response = handler.processRequest(request);

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result"); //$NON-NLS-1$
        assertNotNull(result);
        assertFalse("Partial revalidate should remain synchronous without task", result.has("task")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testExtractToolFailureMessageReturnsErrorForFailedStructuredContent()
    {
        JsonElement payload = JsonParser.parseString(GsonProvider.toJson(
            ToolCallResult.json(JsonParser.parseString("{\"success\":false,\"error\":\"boom\"}"))));

        assertEquals("boom", McpProtocolHandler.extractToolFailureMessage(payload));
    }

    @Test
    public void testExtractToolFailureMessageReturnsNullForSuccessfulStructuredContent()
    {
        JsonElement payload = JsonParser.parseString(GsonProvider.toJson(
            ToolCallResult.json(JsonParser.parseString("{\"success\":true,\"message\":\"ok\"}"))));

        assertNull(McpProtocolHandler.extractToolFailureMessage(payload));
    }

    @Test
    public void testToolCallJsonPayloadLiftsStructuredMetaToTopLevelMeta() throws Exception
    {
        registry.register(new IMcpTool()
        {
            @Override
            public String getName()
            {
                return "meta_tool"; //$NON-NLS-1$
            }

            @Override
            public String getDescription()
            {
                return "Meta tool"; //$NON-NLS-1$
            }

            @Override
            public String getInputSchema()
            {
                return "{\"type\":\"object\"}"; //$NON-NLS-1$
            }

            @Override
            public String execute(Map<String, String> params)
            {
                return ToolResult.success()
                        .put("message", "blocked") //$NON-NLS-1$ //$NON-NLS-2$
                        .putMeta("io.ditrix.edt.mcp/example", Map.of("reasonCode", "busy")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .toJson();
            }

            @Override
            public ResponseType getResponseType()
            {
                return ResponseType.JSON;
            }
        });
        installTestActivator(new McpServer());

        String response = handler.processRequest(buildToolCallRequest(1, "meta_tool", "{}")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result"); //$NON-NLS-1$
        assertTrue(result.has("_meta")); //$NON-NLS-1$
        assertEquals("busy", result.getAsJsonObject("_meta") //$NON-NLS-1$
                .getAsJsonObject("io.ditrix.edt.mcp/example") //$NON-NLS-1$
                .get("reasonCode").getAsString()); //$NON-NLS-1$
        assertFalse(result.getAsJsonObject("structuredContent").has("_meta")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testToolCallMarkdownPayloadCarriesStructuredContent() throws Exception
    {
        registry.register(new IMcpTool()
        {
            @Override
            public String getName()
            {
                return "markdown_structured_tool"; //$NON-NLS-1$
            }

            @Override
            public String getDescription()
            {
                return "Markdown structured tool"; //$NON-NLS-1$
            }

            @Override
            public String getInputSchema()
            {
                return "{\"type\":\"object\"}"; //$NON-NLS-1$
            }

            @Override
            public String execute(Map<String, String> params)
            {
                return "## Workspace Projects"; //$NON-NLS-1$
            }

            @Override
            public Object getStructuredContent(Map<String, String> params, String result)
            {
                return JsonParser.parseString("{\"success\":true,\"projectCount\":2}"); //$NON-NLS-1$
            }

            @Override
            public ResponseType getResponseType()
            {
                return ResponseType.MARKDOWN;
            }
        });
        installTestActivator(new McpServer());

        String response = handler.processRequest(buildToolCallRequest(1, "markdown_structured_tool", "{}")); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject json = parseResponse(response);
        JsonObject result = json.getAsJsonObject("result"); //$NON-NLS-1$
        assertTrue(result.has("structuredContent")); //$NON-NLS-1$
        assertEquals(2, result.getAsJsonObject("structuredContent").get("projectCount").getAsInt()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("resource", result.getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testAttachTaskResultMetaAddsDetachedContinuationWhenSnapshotExists()
    {
        McpServer server = new McpServer();
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start("task-1", "clean_project", "clean_build", "Running clean build", null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        reporter.detachedUpdate("derived_data", "Detached derived data continues", Map.of("trackingType", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "derived_data")); //$NON-NLS-1$
        server.setActiveOperation(reporter);

        JsonElement payload = JsonParser.parseString(GsonProvider.toJson(
            ToolCallResult.json(JsonParser.parseString("{\"success\":false,\"error\":\"Task was cancelled\"}"))));

        JsonObject enriched = McpProtocolHandler.attachTaskResultMeta(payload, "task-1", server).getAsJsonObject(); //$NON-NLS-1$
        JsonObject meta = enriched.getAsJsonObject("_meta"); //$NON-NLS-1$

        assertNotNull(meta);
        assertTrue(meta.has(McpConstants.META_RELATED_TASK));
        assertEquals("task-1", meta.getAsJsonObject(McpConstants.META_RELATED_TASK).get("taskId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(meta.has(McpConstants.META_DETACHED_CONTINUATION));
        assertEquals("task-1", meta.getAsJsonObject(McpConstants.META_DETACHED_CONTINUATION) //$NON-NLS-1$
                .get("operationId").getAsString()); //$NON-NLS-1$
        assertEquals("get_operation_snapshot", meta.getAsJsonObject(McpConstants.META_DETACHED_CONTINUATION) //$NON-NLS-1$
                .get("pollTool").getAsString()); //$NON-NLS-1$
    }

    // === Helpers ===

    private String buildJsonRpcRequest(Object id, String method, String paramsJson)
    {
        StringBuilder sb = new StringBuilder("{\"jsonrpc\":\"2.0\"");
        if (id instanceof String)
        {
            sb.append(",\"id\":\"").append(id).append("\"");
        }
        else
        {
            sb.append(",\"id\":").append(id);
        }
        sb.append(",\"method\":\"").append(method).append("\"");
        if (paramsJson != null)
        {
            sb.append(",\"params\":").append(paramsJson);
        }
        sb.append("}");
        return sb.toString();
    }

    private String buildToolCallRequest(Object id, String toolName, String argsJson)
    {
        StringBuilder params = new StringBuilder("{\"name\":\"").append(toolName).append("\"");
        if (argsJson != null)
        {
            params.append(",\"arguments\":").append(argsJson);
        }
        else
        {
            params.append(",\"arguments\":{}");
        }
        params.append("}");
        return buildJsonRpcRequest(id, "tools/call", params.toString());
    }

    private String buildTaskToolCallRequest(Object id, String toolName, String argsJson, String taskJson)
    {
        StringBuilder params = new StringBuilder("{\"name\":\"").append(toolName).append("\"");
        if (argsJson != null)
        {
            params.append(",\"arguments\":").append(argsJson);
        }
        else
        {
            params.append(",\"arguments\":{}");
        }
        params.append(",\"task\":").append(taskJson);
        params.append("}");
        return buildJsonRpcRequest(id, "tools/call", params.toString());
    }

    private JsonObject parseResponse(String response)
    {
        return JsonParser.parseString(response).getAsJsonObject();
    }

    private McpServer createTaskCapableServer() throws Exception
    {
        McpServer server = new McpServer();
        Field taskExecutorField = McpServer.class.getDeclaredField("taskExecutor"); //$NON-NLS-1$
        taskExecutorField.setAccessible(true);
        taskExecutorField.set(server, new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), r -> {
                    Thread thread = new Thread(r, "MCP-Task-Test"); //$NON-NLS-1$
                    thread.setDaemon(true);
                    return thread;
                }));
        this.testServer = server;
        return server;
    }

    private void installTestActivator(McpServer server) throws Exception
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

    private void shutdownTestServer()
    {
        if (testServer == null)
        {
            return;
        }
        try
        {
            Field taskExecutorField = McpServer.class.getDeclaredField("taskExecutor"); //$NON-NLS-1$
            taskExecutorField.setAccessible(true);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) taskExecutorField.get(testServer);
            if (executor != null)
            {
                executor.shutdownNow();
                taskExecutorField.set(testServer, null);
            }
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
        finally
        {
            testServer = null;
        }
    }

    /**
     * Minimal IMcpTool stub for testing.
     */
    private static class StubTool implements IMcpTool
    {
        private final String name;
        private final String description;
        private final String inputSchema;
        private final TaskSupport taskSupport;
        private final String executeResult;

        StubTool(String name, String description, String inputSchema, TaskSupport taskSupport)
        {
            this(name, description, inputSchema, taskSupport, "{}"); //$NON-NLS-1$
        }

        StubTool(String name, String description, String inputSchema, TaskSupport taskSupport, String executeResult)
        {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.taskSupport = taskSupport;
            this.executeResult = executeResult;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return description; }

        @Override
        public String getInputSchema() { return inputSchema; }

        @Override
        public TaskSupport getTaskSupport() { return taskSupport; }

        @Override
        public String execute(Map<String, String> params) { return executeResult; }
    }
}
