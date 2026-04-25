/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol.jsonrpc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ditrix.edt.mcp.server.progress.OperationProgressState;
import com.ditrix.edt.mcp.server.tasks.TaskRecord;
import com.ditrix.edt.mcp.server.tasks.TaskResultEnvelope;

/**
 * Normalized lifecycle envelope for task-backed tool outcomes.
 */
public class TaskLifecycleEnvelope
{
    private String taskId;
    private String operationId;
    private String state;
    private String status;
    private String statusMessage;
    private String outcome;
    private String startedAt;
    private String updatedAt;
    private String finishedAt;
    private String stage;
    private Double progress;
    private Double total;
    private Boolean indeterminate;
    private Boolean terminal;
    private Boolean resultAvailable;
    private Map<String, Object> result;
    private String preferredFollowUpTool;
    private Map<String, Object> sourceTool;
    private List<String> warnings = new ArrayList<>();

    public TaskLifecycleEnvelope(TaskRecord task)
    {
        this(task, null, null);
    }

    public TaskLifecycleEnvelope(TaskRecord task, String outcome, String preferredFollowUpTool)
    {
        OperationProgressState progressState = task.getProgressState();
        this.taskId = task.getTaskId();
        this.operationId = task.getTaskId();
        this.state = task.getStatus().getWireValue();
        this.status = task.getStatus().getWireValue();
        this.statusMessage = task.getStatusMessage();
        this.outcome = outcome != null ? outcome : (task.getStatus().isTerminal() ? "terminal" : "active"); //$NON-NLS-1$ //$NON-NLS-2$
        this.startedAt = task.getCreatedAt().toString();
        this.updatedAt = task.getLastUpdatedAt().toString();
        this.finishedAt = task.getStatus().isTerminal() ? task.getLastUpdatedAt().toString() : null;
        this.stage = progressState != null ? progressState.getStage() : null;
        this.progress = progressState != null ? progressState.getProgress() : null;
        this.total = progressState != null ? progressState.getTotal() : null;
        this.indeterminate = progressState != null ? Boolean.valueOf(progressState.isIndeterminate()) : null;
        this.terminal = Boolean.valueOf(task.getStatus().isTerminal());
        this.resultAvailable = Boolean.valueOf(task.getResultEnvelope() != null);
        this.result = buildResultMetadata(task);
        this.preferredFollowUpTool = preferredFollowUpTool;

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("toolName", task.getToolName()); //$NON-NLS-1$
        source.put("projectName", task.getProjectName()); //$NON-NLS-1$
        this.sourceTool = source;
    }

    public String getTaskId()
    {
        return taskId;
    }

    public String getOperationId()
    {
        return operationId;
    }

    public String getState()
    {
        return state;
    }

    public String getStatus()
    {
        return status;
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    public String getOutcome()
    {
        return outcome;
    }

    public String getStartedAt()
    {
        return startedAt;
    }

    public String getUpdatedAt()
    {
        return updatedAt;
    }

    public String getFinishedAt()
    {
        return finishedAt;
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

    public Boolean getTerminal()
    {
        return terminal;
    }

    public Boolean getResultAvailable()
    {
        return resultAvailable;
    }

    public Map<String, Object> getResult()
    {
        return result;
    }

    public String getPreferredFollowUpTool()
    {
        return preferredFollowUpTool;
    }

    public Map<String, Object> getSourceTool()
    {
        return sourceTool;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    private static Map<String, Object> buildResultMetadata(TaskRecord task)
    {
        Map<String, Object> metadata = new LinkedHashMap<>();
        TaskResultEnvelope envelope = task.getResultEnvelope();
        metadata.put("available", Boolean.valueOf(envelope != null)); //$NON-NLS-1$
        if (envelope == null)
        {
            return metadata;
        }

        JsonRpcError error = envelope.getError();
        if (error != null)
        {
            Map<String, Object> errorMetadata = new LinkedHashMap<>();
            errorMetadata.put("code", Integer.valueOf(error.getCode())); //$NON-NLS-1$
            errorMetadata.put("message", error.getMessage()); //$NON-NLS-1$
            metadata.put("kind", "jsonRpcError"); //$NON-NLS-1$ //$NON-NLS-2$
            metadata.put("error", errorMetadata); //$NON-NLS-1$
            return metadata;
        }

        metadata.put("kind", "toolPayload"); //$NON-NLS-1$ //$NON-NLS-2$
        return metadata;
    }
}
