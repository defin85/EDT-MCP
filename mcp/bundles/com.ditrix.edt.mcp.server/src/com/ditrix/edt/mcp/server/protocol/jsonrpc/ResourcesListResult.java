/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

import java.util.ArrayList;
import java.util.List;

import com.ditrix.edt.mcp.server.resources.McpResource;

/**
 * MCP resources/list response result.
 */
public class ResourcesListResult
{
    private List<ResourceInfo> resources = new ArrayList<>();
    private String nextCursor;

    public void addResource(McpResource resource)
    {
        resources.add(new ResourceInfo(resource));
    }

    public List<ResourceInfo> getResources()
    {
        return resources;
    }

    public String getNextCursor()
    {
        return nextCursor;
    }

    public static class ResourceInfo
    {
        private String uri;
        private String name;
        private String title;
        private String description;
        private String mimeType;
        private Long size;

        ResourceInfo(McpResource resource)
        {
            this.uri = resource.getUri();
            this.name = resource.getName();
            this.title = resource.getTitle();
            this.description = resource.getDescription();
            this.mimeType = resource.getMimeType();
            this.size = Long.valueOf(resource.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }

        public String getUri()
        {
            return uri;
        }

        public String getName()
        {
            return name;
        }

        public String getTitle()
        {
            return title;
        }

        public String getDescription()
        {
            return description;
        }

        public String getMimeType()
        {
            return mimeType;
        }

        public Long getSize()
        {
            return size;
        }
    }
}
