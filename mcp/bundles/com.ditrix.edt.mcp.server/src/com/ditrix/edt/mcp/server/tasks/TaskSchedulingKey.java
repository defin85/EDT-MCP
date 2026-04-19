/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

/**
 * Scheduling scope for mutable task-backed execution.
 */
public final class TaskSchedulingKey
{
    private static final TaskSchedulingKey NONE = new TaskSchedulingKey(false, false, null);

    private final boolean mutable;
    private final boolean workspaceWide;
    private final String projectName;

    private TaskSchedulingKey(boolean mutable, boolean workspaceWide, String projectName)
    {
        this.mutable = mutable;
        this.workspaceWide = workspaceWide;
        this.projectName = hasText(projectName) ? projectName : null;
    }

    public static TaskSchedulingKey none()
    {
        return NONE;
    }

    public static TaskSchedulingKey workspaceWide()
    {
        return new TaskSchedulingKey(true, true, null);
    }

    public static TaskSchedulingKey projectScoped(String projectName)
    {
        if (!hasText(projectName))
        {
            return none();
        }
        return new TaskSchedulingKey(true, false, projectName);
    }

    public boolean isMutable()
    {
        return mutable;
    }

    public boolean isWorkspaceWide()
    {
        return workspaceWide;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public boolean conflictsWith(TaskSchedulingKey other)
    {
        if (other == null || !mutable || !other.mutable)
        {
            return false;
        }
        if (workspaceWide || other.workspaceWide)
        {
            return true;
        }
        return hasText(projectName) && projectName.equals(other.projectName);
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
