/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ditrix.edt.mcp.server.tasks.TaskRecord;

/**
 * Result payload for task-augmented request creation.
 */
public class CreateTaskResult
{
    private TaskInfo task;
    private Map<String, Object> _meta;

    public CreateTaskResult(TaskRecord task)
    {
        this.task = new TaskInfo(task);
    }

    public TaskInfo getTask()
    {
        return task;
    }

    public Map<String, Object> get_meta()
    {
        return _meta;
    }

    public void putMeta(String key, Object value)
    {
        if (_meta == null)
        {
            _meta = new LinkedHashMap<>();
        }
        _meta.put(key, value);
    }
}
