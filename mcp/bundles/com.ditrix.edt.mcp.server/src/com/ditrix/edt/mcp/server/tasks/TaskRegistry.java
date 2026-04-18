/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.ditrix.edt.mcp.server.progress.OperationProgressState;

/**
 * In-memory task registry with bounded retention.
 */
public final class TaskRegistry
{
    private static final long DEFAULT_TTL_MS = 15 * 60 * 1000L;
    private static final int DEFAULT_POLL_INTERVAL_MS = 5000;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    public TaskRecord createToolTask(String requestId, String sessionId, String toolName, Long requestedTtl,
            String projectName)
    {
        cleanupExpired();
        long ttl = requestedTtl != null && requestedTtl.longValue() > 0 ? requestedTtl.longValue() : DEFAULT_TTL_MS;
        TaskRecord record = new TaskRecord(UUID.randomUUID().toString(), requestId, sessionId, toolName, projectName,
                Long.valueOf(ttl), DEFAULT_POLL_INTERVAL_MS);
        tasks.put(record.getTaskId(), record);
        return record;
    }

    public TaskRecord getTask(String taskId, String sessionId)
    {
        cleanupExpired();
        TaskRecord record = tasks.get(taskId);
        if (record == null || !record.belongsToSession(sessionId))
        {
            return null;
        }
        return record;
    }

    public ListPage listTasks(String sessionId, String cursor, Integer limit)
    {
        cleanupExpired();
        int offset = parseCursor(cursor);
        int pageSize = normalizeLimit(limit);

        List<TaskRecord> visibleTasks = new ArrayList<>();
        for (TaskRecord task : tasks.values())
        {
            if (task.belongsToSession(sessionId))
            {
                visibleTasks.add(task);
            }
        }
        visibleTasks.sort(Comparator.comparing(TaskRecord::getCreatedAt).reversed());

        int fromIndex = Math.min(offset, visibleTasks.size());
        int toIndex = Math.min(fromIndex + pageSize, visibleTasks.size());
        String nextCursor = toIndex < visibleTasks.size() ? Integer.toString(toIndex) : null;
        return new ListPage(visibleTasks.subList(fromIndex, toIndex), nextCursor);
    }

    public boolean hasRunningTask(String toolName, String projectName)
    {
        cleanupExpired();
        for (TaskRecord task : tasks.values())
        {
            if (toolName.equals(task.getToolName())
                    && equalsNullable(projectName, task.getProjectName())
                    && !task.getStatus().isTerminal())
            {
                return true;
            }
        }
        return false;
    }

    public void attachExecutionHandle(String taskId, TaskExecutionHandle handle)
    {
        TaskRecord task = tasks.get(taskId);
        if (task != null)
        {
            task.setExecutionHandle(handle);
        }
    }

    public void updateProgress(String taskId, OperationProgressState state)
    {
        TaskRecord task = tasks.get(taskId);
        if (task != null)
        {
            task.updateProgress(state);
        }
    }

    public void markWorking(String taskId, String statusMessage)
    {
        TaskRecord task = tasks.get(taskId);
        if (task != null)
        {
            task.markWorking(statusMessage);
        }
    }

    public void markCompleted(String taskId, TaskResultEnvelope resultEnvelope, String statusMessage)
    {
        TaskRecord task = tasks.get(taskId);
        if (task != null)
        {
            task.markCompleted(resultEnvelope, statusMessage);
        }
    }

    public void markFailed(String taskId, TaskResultEnvelope resultEnvelope, String statusMessage)
    {
        TaskRecord task = tasks.get(taskId);
        if (task != null)
        {
            task.markFailed(resultEnvelope, statusMessage);
        }
    }

    public TaskRecord cancelTask(String taskId, String sessionId, TaskResultEnvelope cancelledResult,
            String statusMessage)
    {
        cleanupExpired();
        TaskRecord task = getTask(taskId, sessionId);
        if (task == null)
        {
            return null;
        }
        if (!task.markCancelled(cancelledResult, statusMessage))
        {
            return task;
        }

        TaskExecutionHandle handle = task.getExecutionHandle();
        if (handle != null)
        {
            handle.cancelExecution();
        }
        return task;
    }

    public void clear()
    {
        tasks.clear();
    }

    public void cleanupExpired()
    {
        Instant now = Instant.now();
        for (TaskRecord task : tasks.values())
        {
            if (task.isExpired(now))
            {
                tasks.remove(task.getTaskId(), task);
            }
        }
    }

    private int parseCursor(String cursor)
    {
        if (cursor == null || cursor.isBlank())
        {
            return 0;
        }
        try
        {
            return Math.max(0, Integer.parseInt(cursor));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private int normalizeLimit(Integer limit)
    {
        if (limit == null || limit.intValue() <= 0)
        {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(MAX_PAGE_SIZE, limit.intValue());
    }

    private boolean equalsNullable(String left, String right)
    {
        if (left == null)
        {
            return right == null;
        }
        return left.equals(right);
    }

    public static final class ListPage
    {
        private final List<TaskRecord> tasks;
        private final String nextCursor;

        public ListPage(List<TaskRecord> tasks, String nextCursor)
        {
            this.tasks = List.copyOf(tasks);
            this.nextCursor = nextCursor;
        }

        public List<TaskRecord> getTasks()
        {
            return tasks;
        }

        public String getNextCursor()
        {
            return nextCursor;
        }
    }
}
