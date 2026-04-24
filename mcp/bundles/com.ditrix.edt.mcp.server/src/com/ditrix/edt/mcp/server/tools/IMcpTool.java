/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools;

import java.util.Map;

import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;

/**
 * Interface for MCP tool implementations.
 * Each tool provides a specific capability to MCP clients.
 */
public interface IMcpTool
{
    /**
     * Tool-level support for task-augmented execution.
     */
    enum TaskSupport
    {
        FORBIDDEN("forbidden"), //$NON-NLS-1$
        OPTIONAL("optional"), //$NON-NLS-1$
        REQUIRED("required"); //$NON-NLS-1$

        private final String wireValue;

        TaskSupport(String wireValue)
        {
            this.wireValue = wireValue;
        }

        public String getWireValue()
        {
            return wireValue;
        }
    }

    /**
     * Response content type for tool results.
     */
    enum ResponseType
    {
        /** Plain text response */
        TEXT,
        /** JSON response with structuredContent */
        JSON,
        /** Markdown response returned as EmbeddedResource with mimeType */
        MARKDOWN,
        /** Image response returned as EmbeddedResource with image/* mimeType */
        IMAGE
    }
    
    /**
     * Returns the unique name of the tool.
     * This name is used in MCP protocol to identify the tool.
     * 
     * @return tool name (e.g., "get_edt_version", "list_projects")
     */
    String getName();
    
    /**
     * Returns a human-readable description of the tool.
     * This description is sent to MCP clients in tools/list response.
     * 
     * @return tool description
     */
    String getDescription();
    
    /**
     * Returns the JSON Schema for input parameters.
     * Used by MCP clients to validate input before calling the tool.
     * 
     * @return input schema as JSON string
     */
    String getInputSchema();
    
    /**
     * Executes the tool with the given parameters.
     * 
     * @param params map of parameter name to value
     * @return result string (format depends on getResponseType())
     */
    String execute(Map<String, String> params);
    
    /**
     * Returns the response content type for this tool.
     * Default is MARKDOWN for better context efficiency.
     * 
     * @return response type
     */
    default ResponseType getResponseType()
    {
        return ResponseType.MARKDOWN;
    }

    /**
     * Returns the task-augmentation support level for this tool.
     */
    default TaskSupport getTaskSupport()
    {
        return TaskSupport.FORBIDDEN;
    }

    /**
     * Validates whether the current request may use task augmentation.
     *
     * @param params request arguments
     * @return null when task augmentation is allowed, otherwise an actionable error message
     */
    default String validateTaskRequest(Map<String, String> params)
    {
        return null;
    }

    /**
     * Returns the mutable scheduling scope for task-backed execution.
     *
     * <p>Only mutable long-running tools should return a non-empty scope. The scheduler uses this
     * scope to reject unsafe overlapping execution.
     *
     * @param params request arguments
     * @return scheduling key for this task request
     */
    default TaskSchedulingKey getTaskSchedulingKey(Map<String, String> params)
    {
        return TaskSchedulingKey.none();
    }

    /**
     * Returns optional MCP tool annotations for discovery.
     *
     * @return tool annotations or {@code null} when no reliable annotations are available
     */
    default ToolAnnotations getAnnotations()
    {
        return null;
    }
    
    /**
     * Returns the result file name for EmbeddedResource URI.
     * Used when response type is MARKDOWN.
     * Default returns tool name with .md extension.
     * Override to provide dynamic file name based on parameters.
     * 
     * @param params the execution parameters
     * @return file name with extension (e.g., "begin-transaction.md")
     */
    default String getResultFileName(Map<String, String> params)
    {
        return getName() + ".md"; //$NON-NLS-1$
    }

    /**
     * Returns optional additive structuredContent for tools whose primary response is not JSON.
     *
     * <p>This enables markdown-first tools to expose deterministic machine-readable records
     * alongside human-readable embedded resources.
     *
     * @param params execution parameters
     * @param result textual or resource payload returned by {@link #execute(Map)}
     * @return structured content object for MCP payload, or {@code null} when not used
     */
    default Object getStructuredContent(Map<String, String> params, String result)
    {
        return null;
    }
}
