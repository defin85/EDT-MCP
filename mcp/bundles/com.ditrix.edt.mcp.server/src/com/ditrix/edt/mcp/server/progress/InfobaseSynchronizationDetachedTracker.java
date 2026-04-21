/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.util.LinkedHashMap;
import java.util.Locale;
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
    private static final long PENDING_SYNC_GRACE_MS = 10000L;
    private static final String STAGE_UPDATE_START = "update_start"; //$NON-NLS-1$
    private static final String STAGE_WAITING_FOR_EDT = "waiting_for_edt"; //$NON-NLS-1$
    private static final String STAGE_FINAL_STATE_CHECK = "final_state_check"; //$NON-NLS-1$
    private static final String STAGE_VALIDATION = "validation"; //$NON-NLS-1$
    private static final String STAGE_SYNC_STATE_CHECK = "sync_state_check"; //$NON-NLS-1$
    private static final String STAGE_COMPLETION = "completion"; //$NON-NLS-1$
    private static final String STAGE_FAILURE = "failure"; //$NON-NLS-1$

    private final McpServer server;
    private final String operationId;
    private final OperationProgressReporter reporter;
    private final IProject project;
    private final InfobaseReference infobase;
    private final IInfobaseSynchronizationManager synchronizationManager;
    private final String applicationId;
    private final String applicationName;
    private final long deadlineAt;
    private final long pendingSyncDeadlineAt;
    private final OperationProgressState handoffState;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final IInfobaseSynchronizationListener listener;
    private final Thread watchdogThread;

    private volatile InfobaseSynchronizationState synchronizationState;
    private volatile InfobaseEqualityState equalityState;
    private volatile boolean authoritativeSyncObserved;

    private InfobaseSynchronizationDetachedTracker(McpServer server, String operationId,
            OperationProgressReporter reporter, IProject project, InfobaseReference infobase,
            IInfobaseSynchronizationManager synchronizationManager, String applicationId, String applicationName,
            OperationProgressState handoffState)
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
        this.pendingSyncDeadlineAt = System.currentTimeMillis() + PENDING_SYNC_GRACE_MS;
        this.handoffState = handoffState;
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
                applicationId, applicationName, sourceState);
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
        DetachedProjection projection = createProjection(handoffState, project.getName(), applicationId,
                applicationName, synchronizationState, equalityState, authoritativeSyncObserved,
                System.currentTimeMillis() <= pendingSyncDeadlineAt);
        if (projection == null)
        {
            return false;
        }
        authoritativeSyncObserved = projection.isAuthoritative();
        reporter.detachedUpdate(projection.getStage(), projection.getMessage(), projection.getDetails());
        return true;
    }

    static DetachedProjection createProjection(OperationProgressState handoffState, String projectName,
            String applicationId, String applicationName, InfobaseSynchronizationState synchronizationState,
            InfobaseEqualityState equalityState, boolean authoritativeSyncObserved, boolean withinPendingGrace)
    {
        String updateState = InfobaseSyncUtils.deriveUpdateState(synchronizationState, equalityState);
        if ("BEING_UPDATED".equals(updateState)) //$NON-NLS-1$
        {
            Map<String, Object> details = createDetails(projectName, applicationId, applicationName,
                    synchronizationState, equalityState, updateState);
            String stage = synchronizationState != null
                    ? "infobase_sync_" + synchronizationState.name().toLowerCase(Locale.ROOT) //$NON-NLS-1$
                    : "infobase_sync"; //$NON-NLS-1$
            return new DetachedProjection(stage, buildMessage(projectName, applicationName), details, true);
        }
        if (authoritativeSyncObserved || !withinPendingGrace || !allowsPendingProjection(handoffState))
        {
            return null;
        }

        Map<String, Object> details = createDetails(projectName, applicationId, applicationName,
                synchronizationState, equalityState, updateState);
        details.put("pendingSyncState", Boolean.TRUE); //$NON-NLS-1$
        if (handoffState != null && hasText(handoffState.getStage()))
        {
            details.put("handoffStage", handoffState.getStage()); //$NON-NLS-1$
        }
        if (handoffState != null && hasText(handoffState.getMessage()))
        {
            details.put("handoffMessage", handoffState.getMessage()); //$NON-NLS-1$
        }
        return new DetachedProjection("infobase_sync_pending", //$NON-NLS-1$
                buildMessage(projectName, applicationName) + " while EDT synchronization state becomes observable", //$NON-NLS-1$
                details, false);
    }

    private static Map<String, Object> createDetails(String projectName, String applicationId, String applicationName,
            InfobaseSynchronizationState synchronizationState, InfobaseEqualityState equalityState, String updateState)
    {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("trackingType", "infobase_sync"); //$NON-NLS-1$ //$NON-NLS-2$
        details.put("projectName", projectName); //$NON-NLS-1$
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
        return details;
    }

    private static String buildMessage(String projectName, String applicationName)
    {
        String message = "Detached infobase synchronization continues for " + projectName; //$NON-NLS-1$
        if (hasText(applicationName))
        {
            message += " / " + applicationName; //$NON-NLS-1$
        }
        return message;
    }

    private static boolean allowsPendingProjection(OperationProgressState handoffState)
    {
        if (handoffState == null)
        {
            return false;
        }
        if (isInfobaseSyncContinuationStage(handoffState.getStage()))
        {
            return true;
        }
        if (hasText(handoffState.getStage()) && !isInfobaseSyncNonContinuationStage(handoffState.getStage()))
        {
            return true;
        }
        for (ProgressEvent event : handoffState.getRecentEvents())
        {
            if (event == null)
            {
                continue;
            }
            String stage = event.getStage();
            if (isInfobaseSyncContinuationStage(stage))
            {
                return true;
            }
            if (hasText(stage) && !isInfobaseSyncNonContinuationStage(stage))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isInfobaseSyncContinuationStage(String stage)
    {
        return STAGE_UPDATE_START.equals(stage) || STAGE_WAITING_FOR_EDT.equals(stage)
                || STAGE_FINAL_STATE_CHECK.equals(stage);
    }

    private static boolean isInfobaseSyncNonContinuationStage(String stage)
    {
        return !hasText(stage) || STAGE_VALIDATION.equals(stage) || STAGE_SYNC_STATE_CHECK.equals(stage)
                || STAGE_COMPLETION.equals(stage) || STAGE_FAILURE.equals(stage);
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    static final class DetachedProjection
    {
        private final String stage;
        private final String message;
        private final Map<String, Object> details;
        private final boolean authoritative;

        DetachedProjection(String stage, String message, Map<String, Object> details, boolean authoritative)
        {
            this.stage = stage;
            this.message = message;
            this.details = details != null ? Map.copyOf(details) : Map.of();
            this.authoritative = authoritative;
        }

        String getStage()
        {
            return stage;
        }

        String getMessage()
        {
            return message;
        }

        Map<String, Object> getDetails()
        {
            return details;
        }

        boolean isAuthoritative()
        {
            return authoritative;
        }
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
