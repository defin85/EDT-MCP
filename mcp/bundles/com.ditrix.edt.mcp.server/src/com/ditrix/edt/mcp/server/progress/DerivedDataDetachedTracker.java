/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.progress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.derived.DerivedDataStatus;
import com._1c.g5.v8.derived.IDerivedDataManager;
import com._1c.g5.v8.derived.IDerivedDataStatusListener;
import com._1c.g5.v8.dt.core.platform.IDerivedDataManagerProvider;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;

/**
 * Detached visibility bridge for rebuild / derived-data continuation.
 */
public final class DerivedDataDetachedTracker implements OperationTrackerHandle
{
    private static final long DEFAULT_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final long WATCHDOG_SLEEP_MS = 1000L;

    private final McpServer server;
    private final String operationId;
    private final OperationProgressReporter reporter;
    private final List<ProjectWatch> projectWatches;
    private final long deadlineAt;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final Thread watchdogThread;

    private DerivedDataDetachedTracker(McpServer server, String operationId, OperationProgressReporter reporter,
            List<ProjectWatch> projectWatches)
    {
        this.server = Objects.requireNonNull(server);
        this.operationId = Objects.requireNonNull(operationId);
        this.reporter = Objects.requireNonNull(reporter);
        this.projectWatches = List.copyOf(projectWatches);
        this.deadlineAt = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
        this.watchdogThread = new Thread(this::watchdogLoop, "MCP-DD-Detached-" + operationId); //$NON-NLS-1$
        this.watchdogThread.setDaemon(true);
    }

    public static boolean handoff(McpServer server, OperationProgressReporter sourceReporter, List<IProject> projects)
    {
        if (server == null || sourceReporter == null || projects == null || projects.isEmpty())
        {
            return false;
        }

        OperationProgressState sourceState = sourceReporter.snapshot();
        if (sourceState == null || !hasText(sourceState.getOperationId()))
        {
            return false;
        }

        IDtProjectManager dtProjectManager = Activator.getDefault() != null ? Activator.getDefault().getDtProjectManager() : null;
        IDerivedDataManagerProvider derivedDataProvider = Activator.getDefault() != null
                ? Activator.getDefault().getDerivedDataManagerProvider() : null;
        if (dtProjectManager == null || derivedDataProvider == null)
        {
            return false;
        }

        List<ProjectWatch> watches = new ArrayList<>();
        for (IProject project : projects)
        {
            if (project == null || !project.exists() || !project.isOpen())
            {
                continue;
            }
            IDtProject dtProject = dtProjectManager.getDtProject(project);
            if (dtProject == null)
            {
                continue;
            }
            IDerivedDataManager manager = derivedDataProvider.get(dtProject);
            if (manager == null)
            {
                continue;
            }
            watches.add(new ProjectWatch(project, manager));
        }
        if (watches.isEmpty())
        {
            return false;
        }

        OperationProgressReporter detachedReporter = sourceReporter.detachedCopy("derived_data", //$NON-NLS-1$
                "Detached derived-data processing continues", Map.of("trackingType", "derived_data")); //$NON-NLS-1$ //$NON-NLS-2$
        DerivedDataDetachedTracker tracker = new DerivedDataDetachedTracker(server, sourceState.getOperationId(),
                detachedReporter, watches);
        tracker.initializeListeners();
        if (!tracker.refreshSnapshot())
        {
            tracker.dispose();
            return false;
        }

        server.setActiveOperation(detachedReporter, tracker);
        tracker.watchdogThread.start();
        Activator.logInfo("Detached derived-data tracker started for operation " + sourceState.getOperationId()); //$NON-NLS-1$
        return true;
    }

    @Override
    public void dispose()
    {
        if (!disposed.compareAndSet(false, true))
        {
            return;
        }
        for (ProjectWatch watch : projectWatches)
        {
            if (watch.listener != null)
            {
                try
                {
                    watch.manager.removeStatusListener(watch.listener);
                }
                catch (RuntimeException e)
                {
                    Activator.logError("Failed to remove derived-data status listener", e); //$NON-NLS-1$
                }
            }
        }
        watchdogThread.interrupt();
    }

    private void initializeListeners()
    {
        for (ProjectWatch watch : projectWatches)
        {
            watch.refresh();
            watch.listener = status -> {
                watch.lastStatus = status;
                refreshSnapshot();
            };
            watch.manager.addStatusListener(watch.listener);
        }
    }

    private void watchdogLoop()
    {
        while (!disposed.get())
        {
            if (System.currentTimeMillis() >= deadlineAt)
            {
                Activator.logInfo("Detached derived-data tracker timed out for operation " + operationId); //$NON-NLS-1$
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

        int runningCount = 0;
        ProjectWatch primary = null;
        Instant primaryUpdate = null;
        List<Map<String, Object>> projects = new ArrayList<>();

        for (ProjectWatch watch : projectWatches)
        {
            watch.refresh();
            projects.add(watch.toDetails());
            if (watch.isRunning())
            {
                runningCount++;
                Instant candidateUpdate = watch.lastObservedAt != null ? watch.lastObservedAt : Instant.now();
                if (primary == null || candidateUpdate.isAfter(primaryUpdate))
                {
                    primary = watch;
                    primaryUpdate = candidateUpdate;
                }
            }
        }

        if (runningCount == 0)
        {
            return false;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("trackingType", "derived_data"); //$NON-NLS-1$ //$NON-NLS-2$
        details.put("projects", projects); //$NON-NLS-1$
        details.put("runningProjects", Integer.valueOf(runningCount)); //$NON-NLS-1$
        if (primary != null)
        {
            details.put("projectName", primary.project.getName()); //$NON-NLS-1$
            if (hasText(primary.activeStage()))
            {
                details.put("activeStage", primary.activeStage()); //$NON-NLS-1$
            }
            if (hasText(primary.pipelineStatus()))
            {
                details.put("pipelineStatus", primary.pipelineStatus()); //$NON-NLS-1$
            }
            if (!primary.activeSegments().isEmpty())
            {
                details.put("activeSegments", primary.activeSegments()); //$NON-NLS-1$
            }
            if (!primary.readySegments().isEmpty())
            {
                details.put("readySegments", primary.readySegments()); //$NON-NLS-1$
            }
            details.put("modelSyncActive", Boolean.valueOf(primary.modelSyncActive())); //$NON-NLS-1$
        }

        String stage = primary != null && hasText(primary.activeStage()) ? primary.activeStage() : "derived_data"; //$NON-NLS-1$
        String message;
        if (primary != null && runningCount == 1)
        {
            message = "Detached derived-data processing continues for " + primary.project.getName(); //$NON-NLS-1$
        }
        else
        {
            message = "Detached derived-data processing continues for " + runningCount + " projects"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        reporter.detachedUpdate(stage, message, details);
        return true;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    private static final class ProjectWatch
    {
        private final IProject project;
        private final IDerivedDataManager manager;

        private volatile IDerivedDataStatusListener listener;
        private volatile DerivedDataStatus lastStatus;
        private volatile boolean idle;
        private volatile boolean allComputed;
        private volatile Instant lastObservedAt;

        private ProjectWatch(IProject project, IDerivedDataManager manager)
        {
            this.project = project;
            this.manager = manager;
        }

        private void refresh()
        {
            this.lastStatus = manager.getDerivedDataStatus();
            this.idle = manager.isIdle();
            this.allComputed = manager.isAllComputed();
            this.lastObservedAt = Instant.now();
        }

        private boolean isRunning()
        {
            return !idle || !allComputed;
        }

        private String activeStage()
        {
            return lastStatus != null && lastStatus.getActiveStage() != null ? String.valueOf(lastStatus.getActiveStage()) : null;
        }

        private String pipelineStatus()
        {
            return lastStatus != null && lastStatus.getPipelineStatus() != null
                    ? String.valueOf(lastStatus.getPipelineStatus()) : null;
        }

        private boolean modelSyncActive()
        {
            return lastStatus != null && lastStatus.isModelSyncActive();
        }

        private Map<String, String> activeSegments()
        {
            if (lastStatus == null || lastStatus.getActivePipelineSegments() == null
                    || lastStatus.getActivePipelineSegments().isEmpty())
            {
                return Map.of();
            }

            Map<String, String> segments = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : lastStatus.getActivePipelineSegments().entrySet())
            {
                segments.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return segments;
        }

        private List<String> readySegments()
        {
            if (lastStatus == null)
            {
                return List.of();
            }
            Set<String> ready = lastStatus.getReadySegments();
            return ready != null && !ready.isEmpty() ? new ArrayList<>(ready) : List.of();
        }

        private Map<String, Object> toDetails()
        {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("projectName", project.getName()); //$NON-NLS-1$
            details.put("idle", Boolean.valueOf(idle)); //$NON-NLS-1$
            details.put("allComputed", Boolean.valueOf(allComputed)); //$NON-NLS-1$
            if (hasText(activeStage()))
            {
                details.put("activeStage", activeStage()); //$NON-NLS-1$
            }
            if (hasText(pipelineStatus()))
            {
                details.put("pipelineStatus", pipelineStatus()); //$NON-NLS-1$
            }
            if (!activeSegments().isEmpty())
            {
                details.put("activeSegments", activeSegments()); //$NON-NLS-1$
            }
            if (!readySegments().isEmpty())
            {
                details.put("readySegments", readySegments()); //$NON-NLS-1$
            }
            details.put("modelSyncActive", Boolean.valueOf(modelSyncActive())); //$NON-NLS-1$
            return details;
        }
    }
}
