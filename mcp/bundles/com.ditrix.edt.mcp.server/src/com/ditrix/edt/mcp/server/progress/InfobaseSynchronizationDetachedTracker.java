/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationListener;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.utils.InfobaseSyncUtils;

/**
 * Detached visibility bridge for infobase synchronization continuation.
 */
public final class InfobaseSynchronizationDetachedTracker implements OperationTrackerHandle
{
    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long WATCHDOG_SLEEP_MS = 1000L;

    private final McpServer server;
    private final String operationId;
    private final OperationProgressReporter reporter;
    private final IProject project;
    private final InfobaseReference infobase;
    private final IInfobaseSynchronizationManager synchronizationManager;
    private final String applicationId;
    private final String applicationName;
    private final long deadlineAt;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final IInfobaseSynchronizationListener listener;
    private final Thread watchdogThread;

    private volatile InfobaseSynchronizationState synchronizationState;
    private volatile InfobaseEqualityState equalityState;

    private InfobaseSynchronizationDetachedTracker(McpServer server, String operationId,
            OperationProgressReporter reporter, IProject project, InfobaseReference infobase,
            IInfobaseSynchronizationManager synchronizationManager, String applicationId, String applicationName)
    {
        this.server = Objects.requireNonNull(server);
        this.operationId = Objects.requireNonNull(operationId);
        this.reporter = Objects.requireNonNull(reporter);
        this.project = Objects.requireNonNull(project);
        this.infobase = Objects.requireNonNull(infobase);
        this.synchronizationManager = Objects.requireNonNull(synchronizationManager);
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.deadlineAt = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
        this.listener = new Listener();
        this.watchdogThread = new Thread(this::watchdogLoop, "MCP-IB-Detached-" + operationId); //$NON-NLS-1$
        this.watchdogThread.setDaemon(true);
    }

    public static boolean handoff(McpServer server, OperationProgressReporter sourceReporter, IProject project,
            InfobaseReference infobase, IInfobaseSynchronizationManager synchronizationManager, String applicationId,
            String applicationName)
    {
        if (server == null || sourceReporter == null || project == null || infobase == null
                || synchronizationManager == null)
        {
            return false;
        }

        OperationProgressState sourceState = sourceReporter.snapshot();
        if (sourceState == null || !hasText(sourceState.getOperationId()))
        {
            return false;
        }

        OperationProgressReporter detachedReporter = sourceReporter.detachedCopy("infobase_sync", //$NON-NLS-1$
                "Detached infobase synchronization continues", Map.of("trackingType", "infobase_sync")); //$NON-NLS-1$ //$NON-NLS-2$
        InfobaseSynchronizationDetachedTracker tracker = new InfobaseSynchronizationDetachedTracker(server,
                sourceState.getOperationId(), detachedReporter, project, infobase, synchronizationManager,
                applicationId, applicationName);
        tracker.synchronizationManager.addInfobaseSynchronizationListener(tracker.listener);
        if (!tracker.refreshSnapshot())
        {
            tracker.dispose();
            return false;
        }

        server.setActiveOperation(detachedReporter, tracker);
        tracker.watchdogThread.start();
        Activator.logInfo("Detached infobase synchronization tracker started for operation " //$NON-NLS-1$
                + sourceState.getOperationId());
        return true;
    }

    @Override
    public void dispose()
    {
        if (!disposed.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            synchronizationManager.removeInfobaseSynchronizationListener(listener);
        }
        catch (RuntimeException e)
        {
            Activator.logError("Failed to remove infobase synchronization listener", e); //$NON-NLS-1$
        }
        watchdogThread.interrupt();
    }

    private void watchdogLoop()
    {
        while (!disposed.get())
        {
            if (System.currentTimeMillis() >= deadlineAt)
            {
                Activator.logInfo("Detached infobase synchronization tracker timed out for operation " //$NON-NLS-1$
                        + operationId);
                server.clearActiveOperation(operationId);
                return;
            }

            if (!refreshSnapshot())
            {
                server.clearActiveOperation(operationId);
                return;
            }

            try
            {
                Thread.sleep(WATCHDOG_SLEEP_MS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private synchronized boolean refreshSnapshot()
    {
        if (disposed.get())
        {
            return false;
        }

        synchronizationState = synchronizationManager.getSynchronizationState(project, infobase);
        equalityState = synchronizationManager.getEqualityState(project, infobase);
        String updateState = InfobaseSyncUtils.deriveUpdateState(synchronizationState, equalityState);
        if (!"BEING_UPDATED".equals(updateState)) //$NON-NLS-1$
        {
            return false;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("trackingType", "infobase_sync"); //$NON-NLS-1$ //$NON-NLS-2$
        details.put("projectName", project.getName()); //$NON-NLS-1$
        details.put("applicationId", applicationId); //$NON-NLS-1$
        if (hasText(applicationName))
        {
            details.put("applicationName", applicationName); //$NON-NLS-1$
        }
        if (synchronizationState != null)
        {
            details.put("synchronizationState", synchronizationState.name()); //$NON-NLS-1$
        }
        if (equalityState != null)
        {
            details.put("equalityState", equalityState.name()); //$NON-NLS-1$
        }
        details.put("derivedUpdateState", updateState); //$NON-NLS-1$

        String stage = synchronizationState != null
                ? "infobase_sync_" + synchronizationState.name().toLowerCase(java.util.Locale.ROOT) //$NON-NLS-1$
                : "infobase_sync"; //$NON-NLS-1$
        String message = "Detached infobase synchronization continues for " + project.getName(); //$NON-NLS-1$
        if (hasText(applicationName))
        {
            message += " / " + applicationName; //$NON-NLS-1$
        }
        reporter.detachedUpdate(stage, message, details);
        return true;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private final class Listener implements IInfobaseSynchronizationListener
    {
        @Override
        public void synchronizationStateChanged(InfobaseReference changedInfobase, InfobaseSynchronizationState state)
        {
            if (!infobase.equals(changedInfobase))
            {
                return;
            }
            synchronizationState = state;
            refreshSnapshot();
        }

        @Override
        public void equalityStateChanged(InfobaseReference changedInfobase, InfobaseEqualityState state)
        {
            if (!infobase.equals(changedInfobase))
            {
                return;
            }
            equalityState = state;
            refreshSnapshot();
        }
    }
}
