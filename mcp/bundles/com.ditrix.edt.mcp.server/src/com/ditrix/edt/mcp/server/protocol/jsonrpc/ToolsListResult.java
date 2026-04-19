/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

import java.util.ArrayList;
import java.util.List;

import com.ditrix.edt.mcp.server.tools.IMcpTool;

/**
 * MCP tools/list response result.
 */
public class ToolsListResult
{
    private List<ToolInfo> tools = new ArrayList<>();

    public void addTool(String name, String description, Object inputSchema)
    {
        addTool(name, description, inputSchema, IMcpTool.TaskSupport.FORBIDDEN.getWireValue());
    }
    
    public void addTool(String name, String description, Object inputSchema, String taskSupport)
    {
        tools.add(new ToolInfo(name, description, inputSchema, taskSupport));
    }
    
    public List<ToolInfo> getTools()
    {
        return tools;
    }
    
    /**
     * Tool info for tools/list response.
     */
    public static class ToolInfo
    {
        private String name;
        private String description;
        private Object inputSchema;
        private Execution execution;
        
        public ToolInfo(String name, String description, Object inputSchema, String taskSupport)
        {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.execution = new Execution(taskSupport);
        }
        
        public String getName()
        {
            return name;
        }
        
        public String getDescription()
        {
            return description;
        }
        
        public Object getInputSchema()
        {
            return inputSchema;
        }

        public Execution getExecution()
        {
            return execution;
        }
    }

    public static class Execution
    {
        private String taskSupport;

        public Execution(String taskSupport)
        {
            this.taskSupport = taskSupport;
        }

        public String getTaskSupport()
        {
            return taskSupport;
        }
    }
}
