/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools;

/**
 * Optional MCP tool annotations serialized in tools/list.
 */
public class ToolAnnotations
{
    private String title;
    private Boolean readOnlyHint;
    private Boolean destructiveHint;
    private Boolean idempotentHint;
    private Boolean openWorldHint;

    private ToolAnnotations()
    {
        // Use builder factory methods.
    }

    public static Builder builder(String title)
    {
        return new Builder(title);
    }

    public static ToolAnnotations readOnly(String title)
    {
        return builder(title)
                .readOnlyHint(true)
                .destructiveHint(false)
                .idempotentHint(true)
                .openWorldHint(false)
                .build();
    }

    public String getTitle()
    {
        return title;
    }

    public Boolean getReadOnlyHint()
    {
        return readOnlyHint;
    }

    public Boolean getDestructiveHint()
    {
        return destructiveHint;
    }

    public Boolean getIdempotentHint()
    {
        return idempotentHint;
    }

    public Boolean getOpenWorldHint()
    {
        return openWorldHint;
    }

    public static final class Builder
    {
        private final ToolAnnotations annotations = new ToolAnnotations();

        private Builder(String title)
        {
            annotations.title = title;
        }

        public Builder readOnlyHint(boolean value)
        {
            annotations.readOnlyHint = Boolean.valueOf(value);
            return this;
        }

        public Builder destructiveHint(boolean value)
        {
            annotations.destructiveHint = Boolean.valueOf(value);
            return this;
        }

        public Builder idempotentHint(boolean value)
        {
            annotations.idempotentHint = Boolean.valueOf(value);
            return this;
        }

        public Builder openWorldHint(boolean value)
        {
            annotations.openWorldHint = Boolean.valueOf(value);
            return this;
        }

        public ToolAnnotations build()
        {
            return annotations;
        }
    }
}
