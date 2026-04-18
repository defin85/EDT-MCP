/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

/**
 * MCP initialize response result.
 */
public class InitializeResult
{
    private String protocolVersion;
    private Capabilities capabilities;
    private ServerInfo serverInfo;
    
    public InitializeResult(String protocolVersion, String serverName, String serverVersion, String author)
    {
        this.protocolVersion = protocolVersion;
        this.capabilities = new Capabilities();
        this.serverInfo = new ServerInfo(serverName, serverVersion, author);
    }
    
    public String getProtocolVersion()
    {
        return protocolVersion;
    }
    
    public Capabilities getCapabilities()
    {
        return capabilities;
    }
    
    public ServerInfo getServerInfo()
    {
        return serverInfo;
    }
    
    /**
     * MCP capabilities.
     */
    public static class Capabilities
    {
        private Tools tools = new Tools();
        private Tasks tasks = new Tasks();
        
        public Tools getTools()
        {
            return tools;
        }

        public Tasks getTasks()
        {
            return tasks;
        }
    }
    
    /**
     * Tools capability (empty object signals support).
     */
    public static class Tools
    {
        // Empty - just signals that tools are supported
    }

    /**
     * Tasks capability tree.
     */
    public static class Tasks
    {
        private CapabilityFlag list = new CapabilityFlag();
        private CapabilityFlag cancel = new CapabilityFlag();
        private Requests requests = new Requests();

        public CapabilityFlag getList()
        {
            return list;
        }

        public CapabilityFlag getCancel()
        {
            return cancel;
        }

        public Requests getRequests()
        {
            return requests;
        }
    }

    public static class Requests
    {
        private ToolRequests tools = new ToolRequests();

        public ToolRequests getTools()
        {
            return tools;
        }
    }

    public static class ToolRequests
    {
        private CapabilityFlag call = new CapabilityFlag();

        public CapabilityFlag getCall()
        {
            return call;
        }
    }

    public static class CapabilityFlag
    {
        // Empty object signals support
    }
    
    /**
     * Server info.
     */
    public static class ServerInfo
    {
        private String name;
        private String version;
        private String author;
        
        public ServerInfo(String name, String version, String author)
        {
            this.name = name;
            this.version = version;
            this.author = author;
        }
        
        public String getName()
        {
            return name;
        }
        
        public String getVersion()
        {
            return version;
        }
        
        public String getAuthor()
        {
            return author;
        }
    }
}
