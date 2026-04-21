/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.UserSignal;
import com.ditrix.edt.mcp.server.preferences.PreferenceConstants;
import com.ditrix.edt.mcp.server.progress.OperationProgressState;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.InitializeResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.JsonRpcRequest;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.JsonRpcError;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.JsonRpcResponse;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.CreateTaskResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.TaskInfo;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.ToolCallResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.TasksListResult;
import com.ditrix.edt.mcp.server.protocol.jsonrpc.ToolsListResult;
import com.ditrix.edt.mcp.server.tasks.TaskCancellationToken;
import com.ditrix.edt.mcp.server.tasks.TaskExecutionHandle;
import com.ditrix.edt.mcp.server.tasks.TaskRecord;
import com.ditrix.edt.mcp.server.tasks.TaskRegistry;
import com.ditrix.edt.mcp.server.tasks.TaskResultEnvelope;
import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.McpToolRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Handles MCP JSON-RPC protocol messages.
 * Supports Streamable HTTP transport as per MCP 2025-03-26 specification.
 * Uses GsonProvider for JSON serialization/deserialization.
 */
public class McpProtocolHandler
{
    private final McpToolRegistry toolRegistry;
    
    /**
     * Creates a new protocol handler.
     */
    public McpProtocolHandler()
    {
        this.toolRegistry = McpToolRegistry.getInstance();
    }
    
    /**
     * Processes an MCP JSON-RPC request.
     * 
     * @param requestBody the JSON request body
     * @return JSON response with correct id from request
     */
    public String processRequest(String requestBody)
    {
        return processRequest(requestBody, null, false, ToolExecutionContext.TRANSPORT_MODE_JSON);
    }

    /**
     * Processes an MCP JSON-RPC request with HTTP transport metadata.
     *
     * @param requestBody the JSON request body
     * @param sessionId MCP session id from transport layer
     * @param acceptsSse whether the client accepts SSE responses
     * @param transportMode current transport mode identifier
     * @return JSON response with correct id from request
     */
    public String processRequest(String requestBody, String sessionId, boolean acceptsSse, String transportMode)
    {
        Object requestId = 1; // Default id
        
        try
        {
            // Parse request using GsonProvider
            JsonRpcRequest request = parseRequest(requestBody);
            requestId = normalizeRequestId(request);
            
            // Validate JSON-RPC version
            if (request == null || !McpConstants.JSONRPC_VERSION.equals(request.getJsonrpc()))
            {
                return buildErrorResponse(McpConstants.ERROR_INVALID_REQUEST, 
                    "Invalid JSON-RPC version, expected 2.0", requestId); //$NON-NLS-1$
            }
            
            String method = request.getMethod();
            
            // Check for initialize method
            if (McpConstants.METHOD_INITIALIZE.equals(method))
            {
                // Per spec: echo back the client's requested protocol version if it is a
                // known/supported version; otherwise, fall back to our latest version.
                String clientVersion = request.getStringParam("protocolVersion"); //$NON-NLS-1$
                return buildInitializeResponse(requestId, clientVersion);
            }
            
            // Check for initialized notification (no response needed, but return 202)
            if (McpConstants.METHOD_INITIALIZED.equals(method))
            {
                return null; // Signal for 202 Accepted with no body
            }
            
            // Check for tools/list method
            if (McpConstants.METHOD_TOOLS_LIST.equals(method))
            {
                return buildToolsListResponse(requestId);
            }

            if (McpConstants.METHOD_TASKS_GET.equals(method))
            {
                return handleTaskGet(request, requestId, sessionId);
            }

            if (McpConstants.METHOD_TASKS_LIST.equals(method))
            {
                return handleTasksList(request, requestId, sessionId);
            }

            if (McpConstants.METHOD_TASKS_RESULT.equals(method))
            {
                return handleTaskResult(request, requestId, sessionId);
            }

            if (McpConstants.METHOD_TASKS_CANCEL.equals(method))
            {
                return handleTaskCancel(request, requestId, sessionId);
            }
            
            // Check for tools/call method
            if (McpConstants.METHOD_TOOLS_CALL.equals(method))
            {
                return handleToolCall(request, requestId, sessionId, acceptsSse, transportMode);
            }
            
            // Method not found
            return buildErrorResponse(McpConstants.ERROR_METHOD_NOT_FOUND, "Method not found", requestId); //$NON-NLS-1$
        }
        catch (Exception e)
        {
            Activator.logError("Error processing MCP request", e); //$NON-NLS-1$
            return buildErrorResponse(McpConstants.ERROR_INTERNAL, e.getMessage(), requestId);
        }
    }
    
    /**
     * Parses JSON-RPC request using GsonProvider.
     */
    private JsonRpcRequest parseRequest(String requestBody)
    {
        try
        {
            return GsonProvider.fromJson(requestBody, JsonRpcRequest.class);
        }
        catch (JsonSyntaxException e)
        {
            Activator.logError("Failed to parse JSON-RPC request", e); //$NON-NLS-1$
            return null;
        }
    }
    
    /**
     * Handles a tools/call request.
     */
    private String handleToolCall(JsonRpcRequest request, Object requestId, String sessionId, boolean acceptsSse,
            String transportMode)
    {
        String toolName = request != null ? request.getToolName() : null;
        
        // Find tool by name
        IMcpTool tool = toolRegistry.getTool(toolName);
        if (tool == null)
        {
            return buildErrorResponse(McpConstants.ERROR_METHOD_NOT_FOUND, "Tool not found: " + toolName, requestId); //$NON-NLS-1$
        }
        
        Activator.logInfo("Processing tools/call: " + tool.getName()); //$NON-NLS-1$
        Map<String, String> params = extractToolParams(request);

        if (request != null && request.hasTask())
        {
            if (IMcpTool.TaskSupport.FORBIDDEN.equals(tool.getTaskSupport()))
            {
                return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS,
                        "Tool does not support task-augmented execution: " + tool.getName(), requestId); //$NON-NLS-1$
            }
            String validationError = tool.validateTaskRequest(params);
            if (validationError != null)
            {
                return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS, validationError, requestId);
            }
            return handleTaskAugmentedToolCall(tool, params, request, requestId, sessionId, acceptsSse, transportMode);
        }

        if (shouldAutoPromoteBareCallToTask(tool, params))
        {
            return handleTaskAugmentedToolCall(tool, params, request, requestId, sessionId, acceptsSse, transportMode);
        }

        if (IMcpTool.TaskSupport.REQUIRED.equals(tool.getTaskSupport()))
        {
            return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS,
                    "Tool must be invoked with task augmentation: " + tool.getName(), requestId); //$NON-NLS-1$
        }

        ToolExecutionOutcome outcome = executeToolRequest(tool, request, requestId, sessionId, acceptsSse,
                transportMode, null, null, true, true);
        return toJsonRpcResponse(outcome, requestId);
    }

    private ToolExecutionContext createToolExecutionContext(JsonRpcRequest request, Object requestId, String toolName,
            String sessionId, boolean acceptsSse, String transportMode, String operationId,
            TaskCancellationToken cancellationToken)
    {
        String normalizedTransportMode = transportMode != null ? transportMode
                : (acceptsSse ? ToolExecutionContext.TRANSPORT_MODE_SSE : ToolExecutionContext.TRANSPORT_MODE_JSON);
        return new ToolExecutionContext(requestId != null ? requestId.toString() : null, toolName, sessionId,
                request != null ? request.getProgressToken() : null, acceptsSse, normalizedTransportMode,
                operationId != null ? operationId : UUID.randomUUID().toString(), cancellationToken);
    }

    private boolean shouldAutoPromoteBareCallToTask(IMcpTool tool, Map<String, String> params)
    {
        if (tool == null || params == null)
        {
            return false;
        }

        String toolName = tool.getName();
        if ("update_database".equals(toolName) || "clean_project".equals(toolName) //$NON-NLS-1$ //$NON-NLS-2$
                || "apply_extension_to_infobase".equals(toolName)) //$NON-NLS-1$
        {
            return true;
        }
        if (!"revalidate_objects".equals(toolName)) //$NON-NLS-1$
        {
            return false;
        }

        String objectsJson = params.get("objects"); //$NON-NLS-1$
        if (objectsJson == null || objectsJson.isBlank())
        {
            return true;
        }
        try
        {
            JsonElement element = JsonParser.parseString(objectsJson);
            return element.isJsonArray() && element.getAsJsonArray().size() == 0;
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    private Object normalizeRequestId(JsonRpcRequest request)
    {
        Object requestId = 1L;
        if (request == null || request.getId() == null)
        {
            return requestId;
        }

        requestId = request.getId();
        // Gson deserializes JSON numbers into Object fields as Double.
        // Normalize whole-number Doubles to Long so "id":0 serializes back as 0.
        if (requestId instanceof Double)
        {
            double d = (Double) requestId;
            if (!Double.isInfinite(d) && d == Math.floor(d)
                && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE)
            {
                return Long.valueOf(((Double) requestId).longValue());
            }
        }
        return requestId;
    }
    
    /**
     * Adds user signal to a JSON result string using Gson for proper JSON handling.
     */
    private String addUserSignalToJson(String jsonResult, UserSignal signal)
    {
        try
        {
            // Parse the original JSON
            JsonElement element = JsonParser.parseString(jsonResult);
            if (element.isJsonObject())
            {
                com.google.gson.JsonObject jsonObject = element.getAsJsonObject();
                
                // Create userSignal object
                com.google.gson.JsonObject signalObject = new com.google.gson.JsonObject();
                signalObject.addProperty("type", signal.getType().name());
                signalObject.addProperty("message", signal.getMessage());
                
                // Add to result
                jsonObject.add("userSignal", signalObject);
                
                return new com.google.gson.Gson().toJson(jsonObject);
            }
        }
        catch (Exception e)
        {
            Activator.logError("Failed to add user signal to JSON", e);
        }
        return jsonResult;
    }
    
    /**
     * Extracts tool parameters from request.
     */
    private Map<String, String> extractToolParams(JsonRpcRequest request)
    {
        Map<String, String> params = new HashMap<>();
        
        Map<String, Object> arguments = request != null ? request.getArguments() : null;
        if (arguments == null)
        {
            return params;
        }
        
        // Convert all arguments to strings
        for (Map.Entry<String, Object> entry : arguments.entrySet())
        {
            Object value = entry.getValue();
            if (value != null)
            {
                if (value instanceof List || value instanceof Map)
                {
                    // Serialize complex types back to JSON
                    params.put(entry.getKey(), GsonProvider.toJson(value));
                }
                else
                {
                    params.put(entry.getKey(), value.toString());
                }
            }
        }
        
        return params;
    }
    
    /**
     * Builds initialize response.
     * Echoes back the client's requested protocol version (per spec) if it is a
     * recognized date-format version; otherwise uses our latest version.
     */
    private String buildInitializeResponse(Object requestId, String clientVersion)
    {
        // Use the client's version if it looks like a valid MCP version date (YYYY-MM-DD),
        // otherwise fall back to our supported version.
        String version = (clientVersion != null && clientVersion.matches("\\d{4}-\\d{2}-\\d{2}")) //$NON-NLS-1$
            ? clientVersion : McpConstants.PROTOCOL_VERSION;
        InitializeResult result = new InitializeResult(
            version,
            McpConstants.SERVER_NAME,
            McpConstants.PLUGIN_VERSION,
            McpConstants.AUTHOR
        );
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, result));
    }
    
    /**
     * Builds tools/list response dynamically from registry.
     */
    private String buildToolsListResponse(Object requestId)
    {
        ToolsListResult result = new ToolsListResult();
        
        for (IMcpTool tool : toolRegistry.getAllTools())
        {
            // Parse inputSchema from JSON string to JsonElement
            JsonElement schema = JsonParser.parseString(tool.getInputSchema());
            result.addTool(tool.getName(), tool.getDescription(), schema, tool.getTaskSupport().getWireValue());
        }
        
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, result));
    }

    private String handleTaskGet(JsonRpcRequest request, Object requestId, String sessionId)
    {
        TaskRecord task = getAccessibleTask(request, sessionId);
        if (task == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS, "Task not found", requestId); //$NON-NLS-1$
        }
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, new TaskInfo(task)));
    }

    private String handleTasksList(JsonRpcRequest request, Object requestId, String sessionId)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INTERNAL, "MCP server is not available", requestId); //$NON-NLS-1$
        }

        TaskRegistry.ListPage page = server.getTaskRegistry().listTasks(sessionId, request != null ? request.getCursor() : null,
                request != null ? request.getLimit() : null);
        TasksListResult result = new TasksListResult();
        for (TaskRecord task : page.getTasks())
        {
            result.addTask(task);
        }
        result.setNextCursor(page.getNextCursor());
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, result));
    }

    private String handleTaskResult(JsonRpcRequest request, Object requestId, String sessionId)
    {
        TaskRecord task = getAccessibleTask(request, sessionId);
        if (task == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS, "Task not found", requestId); //$NON-NLS-1$
        }

        try
        {
            task.awaitTerminal();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return buildErrorResponse(McpConstants.ERROR_INTERNAL, "Interrupted while waiting for task result", requestId); //$NON-NLS-1$
        }

        TaskResultEnvelope resultEnvelope = task.getResultEnvelope();
        if (resultEnvelope == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INTERNAL, "Task completed without a stored result", requestId); //$NON-NLS-1$
        }
        if (resultEnvelope.getError() != null)
        {
            return GsonProvider.toJson(JsonRpcResponse.error(requestId, resultEnvelope.getError().getCode(),
                    resultEnvelope.getError().getMessage()));
        }

        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        JsonElement payload = attachTaskResultMeta(resultEnvelope.getResult(), task.getTaskId(), server);
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, payload));
    }

    private String handleTaskCancel(JsonRpcRequest request, Object requestId, String sessionId)
    {
        TaskRecord task = getAccessibleTask(request, sessionId);
        if (task == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS, "Task not found", requestId); //$NON-NLS-1$
        }
        if (task.getStatus().isTerminal())
        {
            return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS,
                    "Task is already in terminal state: " + task.getStatus().getWireValue(), requestId); //$NON-NLS-1$
        }

        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INTERNAL, "MCP server is not available", requestId); //$NON-NLS-1$
        }

        IMcpTool tool = toolRegistry.getTool(task.getToolName());
        TaskResultEnvelope cancelledEnvelope = tool != null
                ? TaskResultEnvelope.success(createCancelledTaskPayload(tool, task))
                : TaskResultEnvelope.error(new JsonRpcError(McpConstants.ERROR_INTERNAL, "Task was cancelled")); //$NON-NLS-1$
        TaskRecord cancelledTask = server.getTaskRegistry().cancelTask(task.getTaskId(), sessionId, cancelledEnvelope,
                "Task cancellation requested."); //$NON-NLS-1$
        if (cancelledTask == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS, "Task not found", requestId); //$NON-NLS-1$
        }
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, new TaskInfo(cancelledTask)));
    }

    private String handleTaskAugmentedToolCall(IMcpTool tool, Map<String, String> params, JsonRpcRequest request,
            Object requestId, String sessionId, boolean acceptsSse, String transportMode)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return buildErrorResponse(McpConstants.ERROR_INTERNAL, "MCP server is not available", requestId); //$NON-NLS-1$
        }

        String projectName = params.get("projectName"); //$NON-NLS-1$
        TaskSchedulingKey schedulingKey = tool.getTaskSchedulingKey(params);
        TaskRecord conflictingTask = server.getTaskRegistry().findConflictingTask(schedulingKey);
        if (conflictingTask != null)
        {
            return buildErrorResponse(McpConstants.ERROR_INVALID_PARAMS,
                    buildConflictingTaskMessage(conflictingTask), requestId);
        }

        TaskRecord task = server.getTaskRegistry().createToolTask(requestId != null ? requestId.toString() : null,
                sessionId, tool.getName(), request != null ? request.getTaskTtl() : null, projectName, schedulingKey);
        TaskCancellationToken cancellationToken = new TaskCancellationToken();
        TaskExecutionHandle executionHandle = new TaskExecutionHandle(cancellationToken);
        task.setExecutionHandle(executionHandle);

        try
        {
            executionHandle.setFuture(server.submitTask(() -> executeTaskToolCall(task, tool, request, requestId,
                    sessionId, acceptsSse, transportMode, cancellationToken)));
        }
        catch (RuntimeException e)
        {
            server.getTaskRegistry().markFailed(task.getTaskId(),
                    TaskResultEnvelope.error(new JsonRpcError(McpConstants.ERROR_INTERNAL, e.getMessage())),
                    "Task scheduling failed"); //$NON-NLS-1$
            return buildErrorResponse(McpConstants.ERROR_INTERNAL, e.getMessage(), requestId);
        }

        CreateTaskResult result = new CreateTaskResult(task);
        result.putMeta(McpConstants.META_MODEL_IMMEDIATE_RESPONSE,
                "Task accepted: " + tool.getName() + " (" + task.getTaskId() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, result));
    }

    private void executeTaskToolCall(TaskRecord task, IMcpTool tool, JsonRpcRequest request, Object requestId,
            String sessionId, boolean acceptsSse, String transportMode, TaskCancellationToken cancellationToken)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        TaskRegistry registry = server != null ? server.getTaskRegistry() : null;
        if (registry == null)
        {
            return;
        }

        registry.markWorking(task.getTaskId(), "Task is running"); //$NON-NLS-1$
        ToolExecutionOutcome outcome = executeToolRequest(tool, request, requestId, sessionId, acceptsSse,
                transportMode, task.getTaskId(), cancellationToken, false, false);

        if (task.getStatus() == com.ditrix.edt.mcp.server.tasks.TaskStatus.CANCELLED)
        {
            return;
        }

        if (outcome.isError())
        {
            registry.markFailed(task.getTaskId(), TaskResultEnvelope.error(outcome.getError()),
                    outcome.getError().getMessage());
        }
        else
        {
            String toolFailureMessage = extractToolFailureMessage(outcome.getResult());
            if (toolFailureMessage != null)
            {
                registry.markFailed(task.getTaskId(), TaskResultEnvelope.success(outcome.getResult()),
                        toolFailureMessage);
                return;
            }
            String completionMessage = task.getProgressState() != null && task.getProgressState().getMessage() != null
                    ? task.getProgressState().getMessage() : "Task completed"; //$NON-NLS-1$
            registry.markCompleted(task.getTaskId(), TaskResultEnvelope.success(outcome.getResult()), completionMessage);
        }
    }

    private TaskRecord getAccessibleTask(JsonRpcRequest request, String sessionId)
    {
        String taskId = request != null ? request.getTaskId() : null;
        if (taskId == null || taskId.isBlank())
        {
            return null;
        }
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return null;
        }
        return server.getTaskRegistry().getTask(taskId, sessionId);
    }

    private String buildConflictingTaskMessage(TaskRecord conflictingTask)
    {
        StringBuilder message = new StringBuilder("Conflicting mutable task is already running: "); //$NON-NLS-1$
        message.append(conflictingTask.getToolName());
        if (conflictingTask.getProjectName() != null && !conflictingTask.getProjectName().isBlank())
        {
            message.append(" for project ").append(conflictingTask.getProjectName()); //$NON-NLS-1$
        }
        else if (conflictingTask.getSchedulingKey().isWorkspaceWide())
        {
            message.append(" for the entire workspace"); //$NON-NLS-1$
        }
        message.append(" (taskId=").append(conflictingTask.getTaskId()).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
        return message.toString();
    }
    
    private JsonElement buildToolCallTextPayload(String result)
    {
        return GsonProvider.get().toJsonTree(ToolCallResult.text(result));
    }

    private JsonElement buildToolCallJsonPayload(String jsonResult)
    {
        JsonElement structured = JsonParser.parseString(jsonResult);
        JsonObject embeddedMeta = extractEmbeddedMeta(structured);
        JsonObject payload = GsonProvider.get().toJsonTree(ToolCallResult.json(structured)).getAsJsonObject();
        if (embeddedMeta != null && !embeddedMeta.entrySet().isEmpty())
        {
            JsonObject meta = payload.has("_meta") && payload.get("_meta").isJsonObject() //$NON-NLS-1$
                    ? payload.getAsJsonObject("_meta") : new JsonObject(); //$NON-NLS-1$
            for (Map.Entry<String, JsonElement> entry : embeddedMeta.entrySet())
            {
                meta.add(entry.getKey(), entry.getValue());
            }
            payload.add("_meta", meta); //$NON-NLS-1$
        }
        return payload;
    }

    private JsonElement buildToolCallResourcePayload(String content, String mimeType, String fileName)
    {
        return buildToolCallResourcePayload(content, mimeType, fileName, null);
    }

    private JsonElement buildToolCallResourcePayload(String content, String mimeType, String fileName,
            Object structuredContent)
    {
        return GsonProvider.get()
                .toJsonTree(ToolCallResult.resource("embedded://" + fileName, mimeType, content, structuredContent)); //$NON-NLS-1$
    }

    private JsonElement buildToolCallResourceBlobPayload(String base64Blob, String mimeType, String fileName)
    {
        return GsonProvider.get().toJsonTree(ToolCallResult.resourceBlob("embedded://" + fileName, mimeType, base64Blob)); //$NON-NLS-1$
    }

    private ToolExecutionOutcome executeToolRequest(IMcpTool tool, JsonRpcRequest request, Object requestId,
            String sessionId, boolean acceptsSse, String transportMode, String operationId,
            TaskCancellationToken cancellationToken, boolean consumeUserSignal, boolean updateCurrentTool)
    {
        Map<String, String> params = extractToolParams(request);
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (updateCurrentTool && server != null)
        {
            server.setCurrentToolName(tool.getName());
        }

        ToolExecutionContext context = createToolExecutionContext(request, requestId, tool.getName(), sessionId,
                acceptsSse, transportMode, operationId, cancellationToken);
        String result;
        try
        {
            ToolExecutionContextHolder.set(context);
            result = tool.execute(params);
        }
        catch (RuntimeException e)
        {
            Activator.logError("Error executing tool: " + tool.getName(), e); //$NON-NLS-1$
            ToolExecutionContextHolder.clear();
            return ToolExecutionOutcome.error(new JsonRpcError(McpConstants.ERROR_INTERNAL, e.getMessage()));
        }
        finally
        {
            if (updateCurrentTool && server != null)
            {
                server.setCurrentToolName(null);
            }
        }

        try
        {
            UserSignal signal = consumeUserSignal && server != null ? server.consumeUserSignal() : null;
            boolean plainTextMode = isPlainTextModeEnabled();
            JsonElement payload = buildToolCallPayload(tool, params, result, signal, plainTextMode);
            payload = attachDetachedContinuationMeta(payload, context != null ? context.getOperationId() : null, server);
            return ToolExecutionOutcome.success(payload);
        }
        finally
        {
            ToolExecutionContextHolder.clear();
        }
    }

    private boolean isPlainTextModeEnabled()
    {
        Activator activator = Activator.getDefault();
        if (activator == null)
        {
            return false;
        }
        try
        {
            return activator.getPreferenceStore().getBoolean(PreferenceConstants.PREF_PLAIN_TEXT_MODE);
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    private JsonElement buildToolCallPayload(IMcpTool tool, Map<String, String> params, String result, UserSignal signal,
            boolean plainTextMode)
    {
        switch (tool.getResponseType())
        {
            case JSON:
                if (signal != null)
                {
                    result = addUserSignalToJson(result, signal);
                }
                if (plainTextMode)
                {
                    return buildToolCallTextPayload(result);
                }
                return buildToolCallJsonPayload(result);
            case MARKDOWN:
                if (signal != null)
                {
                    result = result + "\n\n---\n**USER SIGNAL:** " + signal.getMessage(); //$NON-NLS-1$
                }
                if (plainTextMode)
                {
                    return buildToolCallTextPayload(result);
                }
                return buildToolCallResourcePayload(result, "text/markdown", tool.getResultFileName(params), //$NON-NLS-1$
                        tool.getStructuredContent(params, result));
            case IMAGE:
                if (isJsonErrorPayload(result))
                {
                    return buildToolCallJsonPayload(result);
                }
                return buildToolCallResourceBlobPayload(result, "image/png", tool.getResultFileName(params)); //$NON-NLS-1$
            case TEXT:
            default:
                if (signal != null)
                {
                    result = result + "\n\n---\nUSER SIGNAL: " + signal.getMessage(); //$NON-NLS-1$
                }
                return buildToolCallTextPayload(result);
        }
    }

    private JsonElement createCancelledToolPayload(IMcpTool tool, Map<String, String> params)
    {
        switch (tool.getResponseType())
        {
            case JSON:
            case IMAGE:
                return buildToolCallJsonPayload("{\"success\":false,\"error\":\"Task was cancelled\"}"); //$NON-NLS-1$
            case MARKDOWN:
                return buildToolCallResourcePayload("Task was cancelled", "text/markdown", tool.getResultFileName(params)); //$NON-NLS-1$ //$NON-NLS-2$
            case TEXT:
            default:
                return buildToolCallTextPayload("Task was cancelled"); //$NON-NLS-1$
        }
    }

    private String toJsonRpcResponse(ToolExecutionOutcome outcome, Object requestId)
    {
        if (outcome.isError())
        {
            return GsonProvider.toJson(JsonRpcResponse.error(requestId, outcome.getError().getCode(),
                    outcome.getError().getMessage()));
        }
        return GsonProvider.toJson(JsonRpcResponse.success(requestId, outcome.getResult()));
    }

    static JsonElement attachTaskResultMeta(JsonElement payload, String taskId, McpServer server)
    {
        JsonElement withDetachedContinuation = payload;
        if (server != null && hasText(taskId))
        {
            OperationProgressState snapshot = server.getOperationSnapshot(taskId);
            if (snapshot != null && snapshot.isDetached())
            {
                withDetachedContinuation = DetachedContinuationMeta.attach(withDetachedContinuation, taskId);
            }
        }
        return attachRelatedTaskMeta(withDetachedContinuation, taskId);
    }

    private static JsonElement attachRelatedTaskMeta(JsonElement payload, String taskId)
    {
        if (payload == null || !payload.isJsonObject())
        {
            return payload;
        }
        JsonObject copy = payload.getAsJsonObject().deepCopy();
        JsonObject meta = copy.has("_meta") && copy.get("_meta").isJsonObject() //$NON-NLS-1$
                ? copy.getAsJsonObject("_meta") : new JsonObject(); //$NON-NLS-1$
        JsonObject relatedTask = new JsonObject();
        relatedTask.addProperty("taskId", taskId); //$NON-NLS-1$
        meta.add(McpConstants.META_RELATED_TASK, relatedTask);
        copy.add("_meta", meta); //$NON-NLS-1$
        return copy;
    }

    private JsonElement attachDetachedContinuationMeta(JsonElement payload, String operationId, McpServer server)
    {
        if (server == null || !hasText(operationId))
        {
            return payload;
        }
        OperationProgressState snapshot = server.getOperationSnapshot(operationId);
        if (snapshot == null || !snapshot.isDetached())
        {
            return payload;
        }
        return DetachedContinuationMeta.attach(payload, operationId);
    }

    private JsonElement createCancelledTaskPayload(IMcpTool tool, TaskRecord task)
    {
        JsonElement payload = createCancelledToolPayload(tool, Map.of());
        if (task == null || task.getProgressState() == null)
        {
            return payload;
        }
        OperationProgressState progressState = task.getProgressState();
        if (!DetachedContinuationMeta.shouldExposeForCancellation(task.getToolName(), progressState))
        {
            return payload;
        }
        return DetachedContinuationMeta.attach(payload, task.getTaskId());
    }

    static String extractToolFailureMessage(JsonElement payload)
    {
        if (payload == null || !payload.isJsonObject())
        {
            return null;
        }

        JsonObject payloadObject = payload.getAsJsonObject();
        JsonElement structuredContent = payloadObject.get("structuredContent"); //$NON-NLS-1$
        if (structuredContent == null || !structuredContent.isJsonObject())
        {
            return null;
        }

        JsonObject structuredObject = structuredContent.getAsJsonObject();
        if (structuredObject.has("success") && structuredObject.get("success").isJsonPrimitive() //$NON-NLS-1$
                && structuredObject.get("success").getAsJsonPrimitive().isBoolean() //$NON-NLS-1$
                && !structuredObject.get("success").getAsBoolean()) //$NON-NLS-1$
        {
            String message = extractErrorMessage(structuredObject.get("error")); //$NON-NLS-1$
            return hasText(message) ? message : "Tool execution failed"; //$NON-NLS-1$
        }

        return null;
    }

    private static String extractErrorMessage(JsonElement errorElement)
    {
        if (errorElement == null || errorElement.isJsonNull())
        {
            return null;
        }
        if (errorElement.isJsonPrimitive())
        {
            return errorElement.getAsString();
        }
        if (errorElement.isJsonObject())
        {
            JsonObject errorObject = errorElement.getAsJsonObject();
            JsonElement messageElement = errorObject.get("message"); //$NON-NLS-1$
            if (messageElement != null && messageElement.isJsonPrimitive())
            {
                return messageElement.getAsString();
            }
        }
        return errorElement.toString();
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private JsonObject extractEmbeddedMeta(JsonElement structured)
    {
        if (structured == null || !structured.isJsonObject())
        {
            return null;
        }

        JsonObject structuredObject = structured.getAsJsonObject();
        if (!structuredObject.has("_meta") || !structuredObject.get("_meta").isJsonObject()) //$NON-NLS-1$ //$NON-NLS-2$
        {
            return null;
        }

        JsonObject embeddedMeta = structuredObject.getAsJsonObject("_meta").deepCopy(); //$NON-NLS-1$
        structuredObject.remove("_meta"); //$NON-NLS-1$
        return embeddedMeta;
    }

    /**
     * Checks whether tool result is a JSON error payload (ToolResult.error JSON).
     */
    private boolean isJsonErrorPayload(String result)
    {
        if (result == null)
        {
            return false;
        }

        try
        {
            JsonElement element = JsonParser.parseString(result);
            if (!element.isJsonObject())
            {
                return false;
            }

            com.google.gson.JsonObject obj = element.getAsJsonObject();
            if (obj.has("success") && obj.get("success").isJsonPrimitive()
                && obj.get("success").getAsJsonPrimitive().isBoolean()
                && !obj.get("success").getAsBoolean())
            {
                return true;
            }

            return obj.has("error");
        }
        catch (Exception e)
        {
            return false;
        }
    }
    
    /**
     * Builds error response.
     */
    private String buildErrorResponse(int code, String message, Object requestId)
    {
        return GsonProvider.toJson(JsonRpcResponse.error(requestId, code, message));
    }

    private static final class ToolExecutionOutcome
    {
        private final JsonElement result;
        private final JsonRpcError error;

        private ToolExecutionOutcome(JsonElement result, JsonRpcError error)
        {
            this.result = result;
            this.error = error;
        }

        static ToolExecutionOutcome success(JsonElement result)
        {
            return new ToolExecutionOutcome(result, null);
        }

        static ToolExecutionOutcome error(JsonRpcError error)
        {
            return new ToolExecutionOutcome(null, error);
        }

        boolean isError()
        {
            return error != null;
        }

        JsonElement getResult()
        {
            return result;
        }

        JsonRpcError getError()
        {
            return error;
        }
    }
}
