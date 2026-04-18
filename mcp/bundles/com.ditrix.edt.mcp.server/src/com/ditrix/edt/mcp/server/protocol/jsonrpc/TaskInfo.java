/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

import com.ditrix.edt.mcp.server.tasks.TaskRecord;

/**
 * Serializable MCP task info payload.
 */
public class TaskInfo
{
    private String taskId;
    private String status;
    private String statusMessage;
    private String createdAt;
    private String lastUpdatedAt;
    private Long ttl;
    private Integer pollInterval;

    public TaskInfo(TaskRecord task)
    {
        this.taskId = task.getTaskId();
        this.status = task.getStatus().getWireValue();
        this.statusMessage = task.getStatusMessage();
        this.createdAt = task.getCreatedAt().toString();
        this.lastUpdatedAt = task.getLastUpdatedAt().toString();
        this.ttl = task.getTtl();
        this.pollInterval = Integer.valueOf(task.getPollInterval());
    }

    public String getTaskId()
    {
        return taskId;
    }

    public String getStatus()
    {
        return status;
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    public String getCreatedAt()
    {
        return createdAt;
    }

    public String getLastUpdatedAt()
    {
        return lastUpdatedAt;
    }

    public Long getTtl()
    {
        return ttl;
    }

    public Integer getPollInterval()
    {
        return pollInterval;
    }
}
