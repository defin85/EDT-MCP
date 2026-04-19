/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

import com.ditrix.edt.mcp.server.progress.OperationProgressState;
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
    private String toolName;
    private String projectName;
    private String stage;
    private Double progress;
    private Double total;
    private Boolean indeterminate;
    private Boolean resultAvailable;
    private Long ttl;
    private Integer pollInterval;

    public TaskInfo(TaskRecord task)
    {
        OperationProgressState progressState = task.getProgressState();
        this.taskId = task.getTaskId();
        this.status = task.getStatus().getWireValue();
        this.statusMessage = task.getStatusMessage();
        this.createdAt = task.getCreatedAt().toString();
        this.lastUpdatedAt = task.getLastUpdatedAt().toString();
        this.toolName = task.getToolName();
        this.projectName = task.getProjectName();
        this.stage = progressState != null ? progressState.getStage() : null;
        this.progress = progressState != null ? progressState.getProgress() : null;
        this.total = progressState != null ? progressState.getTotal() : null;
        this.indeterminate = progressState != null ? Boolean.valueOf(progressState.isIndeterminate()) : null;
        this.resultAvailable = Boolean.valueOf(task.getResultEnvelope() != null);
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

    public String getToolName()
    {
        return toolName;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public String getStage()
    {
        return stage;
    }

    public Double getProgress()
    {
        return progress;
    }

    public Double getTotal()
    {
        return total;
    }

    public Boolean getIndeterminate()
    {
        return indeterminate;
    }

    public Boolean getResultAvailable()
    {
        return resultAvailable;
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
