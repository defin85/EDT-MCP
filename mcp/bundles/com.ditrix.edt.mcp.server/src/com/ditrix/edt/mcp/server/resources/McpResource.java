/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.resources;

/**
 * Static MCP resource descriptor and text content.
 */
public class McpResource
{
    private final String uri;
    private final String name;
    private final String title;
    private final String description;
    private final String mimeType;
    private final String content;

    public McpResource(String uri, String name, String title, String description, String mimeType, String content)
    {
        this.uri = uri;
        this.name = name;
        this.title = title;
        this.description = description;
        this.mimeType = mimeType;
        this.content = content;
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

    public String getContent()
    {
        return content;
    }
}
