/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

import java.util.ArrayList;
import java.util.List;

import com.ditrix.edt.mcp.server.tasks.TaskRecord;

/**
 * Result payload for tasks/list.
 */
public class TasksListResult
{
    private List<TaskInfo> tasks = new ArrayList<>();
    private String nextCursor;

    public void addTask(TaskRecord task)
    {
        tasks.add(new TaskInfo(task));
    }

    public List<TaskInfo> getTasks()
    {
        return tasks;
    }

    public String getNextCursor()
    {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor)
    {
        this.nextCursor = nextCursor;
    }
}
