/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Bridges EDT progress monitor callbacks into {@link OperationProgressReporter}.
 */
public class CapturingProgressMonitor implements IProgressMonitor
{
    private final OperationProgressReporter reporter;

    private volatile boolean canceled;
    private String taskName;
    private String subTaskName;
    private Double totalWork;
    private double worked;

    public CapturingProgressMonitor(OperationProgressReporter reporter)
    {
        this.reporter = reporter;
    }

    @Override
    public void beginTask(String name, int totalWork)
    {
        this.taskName = normalize(name);
        this.subTaskName = null;
        this.worked = 0;
        this.totalWork = totalWork == IProgressMonitor.UNKNOWN || totalWork <= 0 ? null : Double.valueOf(totalWork);
        reporter.stage(currentStage(), currentMessage());

        if (this.totalWork != null)
        {
            reporter.progress(0, this.totalWork, currentMessage());
        }
        else
        {
            reporter.indeterminate(currentStage(), currentMessage());
        }
    }

    @Override
    public void done()
    {
        if (totalWork != null && worked < totalWork.doubleValue())
        {
            worked = totalWork.doubleValue();
            reporter.progress(worked, totalWork, currentMessage());
        }
        else
        {
            reporter.stage(currentStage(), currentMessage());
        }
    }

    @Override
    public void internalWorked(double work)
    {
        if (work <= 0)
        {
            return;
        }
        worked += work;
        reporter.progress(worked, totalWork, currentMessage());
    }

    @Override
    public boolean isCanceled()
    {
        return canceled;
    }

    @Override
    public void setCanceled(boolean value)
    {
        this.canceled = value;
    }

    @Override
    public void setTaskName(String name)
    {
        this.taskName = normalize(name);
        reporter.stage(currentStage(), currentMessage());
    }

    @Override
    public void subTask(String name)
    {
        this.subTaskName = normalize(name);
        reporter.stage(currentStage(), currentMessage());
    }

    @Override
    public void worked(int work)
    {
        internalWorked(work);
    }

    private String currentStage()
    {
        if (hasText(taskName))
        {
            return taskName;
        }
        return subTaskName;
    }

    private String currentMessage()
    {
        if (hasText(subTaskName))
        {
            return subTaskName;
        }
        return taskName;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value)
    {
        if (!hasText(value))
        {
            return null;
        }
        return value;
    }
}
