/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ditrix.edt.mcp.server.Activator;

/**
 * Emits a one-shot diagnostic thread dump if a guarded operation does not finish within a timeout.
 */
public final class DiagnosticThreadDumpWatchdog implements AutoCloseable
{
    private static final int MAX_FRAMES = 40;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final String label;
    private final String stage;
    private final long timeoutMs;
    private final Thread targetThread;
    private final Thread watchdogThread;

    private DiagnosticThreadDumpWatchdog(String label, String stage, long timeoutMs, Thread targetThread)
    {
        this.label = label;
        this.stage = stage;
        this.timeoutMs = timeoutMs;
        this.targetThread = targetThread;
        this.watchdogThread = new Thread(this::watchdogLoop,
                "MCP-DiagWatchdog-" + Integer.toHexString((label + "|" + stage).hashCode())); //$NON-NLS-1$ //$NON-NLS-2$
        this.watchdogThread.setDaemon(true);
    }

    public static DiagnosticThreadDumpWatchdog start(String label, String stage, long timeoutMs)
    {
        DiagnosticThreadDumpWatchdog watchdog = new DiagnosticThreadDumpWatchdog(label, stage, timeoutMs,
                Thread.currentThread());
        watchdog.watchdogThread.start();
        return watchdog;
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        watchdogThread.interrupt();
    }

    private void watchdogLoop()
    {
        try
        {
            Thread.sleep(timeoutMs);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return;
        }

        if (closed.get())
        {
            return;
        }

        Activator.logWarning(buildWarningMessage());
        Activator.logWarning(buildThreadDumpMessage());
    }

    String buildWarningMessage()
    {
        return "[diag] watchdog fired for " + label + " stage=" + stage + " after " + timeoutMs //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "ms; targetThread=" + targetThread.getName() + "#" + targetThread.getId() + " state=" //$NON-NLS-1$ //$NON-NLS-2$
                + targetThread.getState();
    }

    String buildThreadDumpMessage()
    {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = bean.dumpAllThreads(true, true);
        StringBuilder builder = new StringBuilder(8192);
        builder.append("[diag] thread dump for ").append(label).append(" stage=").append(stage).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        for (ThreadInfo info : infos)
        {
            if (info == null)
            {
                continue;
            }
            if (!isInteresting(info))
            {
                continue;
            }
            appendThread(builder, info);
            builder.append('\n');
        }
        return builder.toString();
    }

    private boolean isInteresting(ThreadInfo info)
    {
        if (info.getThreadId() == targetThread.getId())
        {
            return true;
        }
        String name = info.getThreadName();
        return contains(name, "MCP") || contains(name, "Worker") || contains(name, "Builder") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || contains(name, "Job") || contains(name, "Check") || contains(name, "Derived") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || contains(name, "Validation") || contains(name, "Display") || contains(name, "main"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static boolean contains(String value, String token)
    {
        return value != null && value.contains(token);
    }

    private void appendThread(StringBuilder builder, ThreadInfo info)
    {
        builder.append('"').append(info.getThreadName()).append('"') //$NON-NLS-1$
                .append(" id=").append(info.getThreadId()) //$NON-NLS-1$
                .append(" state=").append(info.getThreadState()); //$NON-NLS-1$
        if (info.getLockName() != null)
        {
            builder.append(" lock=").append(info.getLockName()); //$NON-NLS-1$
        }
        if (info.getLockOwnerName() != null)
        {
            builder.append(" owner=").append(info.getLockOwnerName()) //$NON-NLS-1$
                    .append('#').append(info.getLockOwnerId());
        }
        builder.append('\n');
        StackTraceElement[] stack = info.getStackTrace();
        int limit = Math.min(stack.length, MAX_FRAMES);
        for (int i = 0; i < limit; i++)
        {
            builder.append("\tat ").append(stack[i]).append('\n'); //$NON-NLS-1$
        }
        if (stack.length > limit)
        {
            builder.append("\t... ").append(stack.length - limit).append(" more\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }
}
