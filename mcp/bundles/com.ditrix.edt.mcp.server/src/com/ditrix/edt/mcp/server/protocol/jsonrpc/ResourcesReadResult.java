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
 * MCP resources/read response result.
 */
public class ResourcesReadResult
{
    private List<ResourceContent> contents = new ArrayList<>();

    public ResourcesReadResult(McpResource resource)
    {
        contents.add(new ResourceContent(resource));
    }

    public List<ResourceContent> getContents()
    {
        return contents;
    }

    public static class ResourceContent
    {
        private String uri;
        private String mimeType;
        private String text;

        ResourceContent(McpResource resource)
        {
            this.uri = resource.getUri();
            this.mimeType = resource.getMimeType();
            this.text = resource.getContent();
        }

        public String getUri()
        {
            return uri;
        }

        public String getMimeType()
        {
            return mimeType;
        }

        public String getText()
        {
            return text;
        }
    }
}
