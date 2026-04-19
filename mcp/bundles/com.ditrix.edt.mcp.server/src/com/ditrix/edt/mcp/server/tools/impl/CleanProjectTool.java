/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.progress.CapturingProgressMonitor;
import com.ditrix.edt.mcp.server.progress.DerivedDataDetachedTracker;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.tasks.TaskCancellationToken;
import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.BuildUtils;
import com.ditrix.edt.mcp.server.utils.LifecycleWaiter;
import com.ditrix.edt.mcp.server.utils.LifecycleWaiter.ProjectRestartWaiter;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;

/**
 * Tool to clean EDT project and trigger full revalidation.
 * Uses Eclipse Project -> Clean command which triggers EDT full rebuild.
 */
public class CleanProjectTool implements IMcpTool
{
    public static final String NAME = "clean_project"; //$NON-NLS-1$

    private static final String STAGE_VALIDATION = "validation"; //$NON-NLS-1$
    private static final String STAGE_LIFECYCLE_PREPARE = "lifecycle_prepare"; //$NON-NLS-1$
    private static final String STAGE_CLEAN_BUILD = "clean_build"; //$NON-NLS-1$
    private static final String STAGE_LIFECYCLE_WAIT = "lifecycle_wait"; //$NON-NLS-1$
    private static final String STAGE_DERIVED_DATA = "derived_data"; //$NON-NLS-1$
    private static final String STAGE_COMPLETION = "completion"; //$NON-NLS-1$
    private static final String STAGE_FAILURE = "failure"; //$NON-NLS-1$
    
    /** Default timeout for waiting project lifecycle restart (3 minutes) */
    private static final long DEFAULT_LIFECYCLE_TIMEOUT_MS = 3 * 60 * 1000;
    
    @Override
    public String getName()
    {
        return NAME;
    }
    
    @Override
    public String getDescription()
    {
        return "Clean EDT project and trigger full revalidation. " + //$NON-NLS-1$
               "Refreshes files from disk, clears all validation markers, " + //$NON-NLS-1$
               "and waits for EDT to complete revalidation."; //$NON-NLS-1$
    }
    
    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty("projectName", "Name of the project to clean (optional, cleans all EDT projects if not specified)") //$NON-NLS-1$ //$NON-NLS-2$
            .build();
    }
    
    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public TaskSupport getTaskSupport()
    {
        return TaskSupport.OPTIONAL;
    }

    @Override
    public TaskSchedulingKey getTaskSchedulingKey(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        return hasText(projectName) ? TaskSchedulingKey.projectScoped(projectName) : TaskSchedulingKey.workspaceWide();
    }
    
    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        
        // Check if project is ready for operations
        if (projectName != null && !projectName.isEmpty())
        {
            String notReadyError = ProjectStateChecker.checkReadyOrError(projectName);
            if (notReadyError != null)
            {
                return ToolResult.error(notReadyError).toJson();
            }
        }
        
        return cleanProject(projectName);
    }
    
    /**
     * Cleans project and triggers revalidation.
     * 
     * <p>To avoid race conditions, lifecycle listeners are registered BEFORE
     * triggering the clean build operation.
     * 
     * @param projectName name of the project to clean (null for all projects)
     * @return JSON string with result
     */
    private String cleanProject(String projectName)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        ToolExecutionContext context = ToolExecutionContextHolder.get();
        OperationProgressReporter reporter = createProgressReporter(context, projectName);
        CapturingProgressMonitor monitor = new CapturingProgressMonitor(reporter);
        TaskCancellationToken cancellationToken = context != null ? context.getCancellationToken() : null;
        List<ProjectCleanInfo> projectsToClean = new ArrayList<>();
        boolean buildTriggered = false;
        boolean detachedHandoff = false;
        boolean backgroundContinuationExpected = false;
        registerActiveOperation(server, reporter, context);

        try
        {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IDtProjectManager dtProjectManager = Activator.getDefault().getDtProjectManager();
            
            List<String> projectNamesList = new ArrayList<>();
            
            if (projectName != null && !projectName.isEmpty())
            {
                // Clean specific project
                IProject project = workspace.getRoot().getProject(projectName);
                if (project == null || !project.exists())
                {
                    return ToolResult.error("Project not found: " + projectName).toJson(); //$NON-NLS-1$
                }
                
                if (!project.isOpen())
                {
                    return ToolResult.error("Project is closed: " + projectName).toJson(); //$NON-NLS-1$
                }
                
                IDtProject dtProject = dtProjectManager != null ? 
                    dtProjectManager.getDtProject(project) : null;
                
                projectsToClean.add(new ProjectCleanInfo(project, dtProject));
                projectNamesList.add(projectName);
            }
            else
            {
                // Clean all EDT projects
                if (dtProjectManager != null)
                {
                    for (IDtProject dtProject : dtProjectManager.getDtProjects())
                    {
                        IProject project = dtProject.getWorkspaceProject();
                        if (project != null && project.isOpen())
                        {
                            projectsToClean.add(new ProjectCleanInfo(project, dtProject));
                            projectNamesList.add(project.getName());
                        }
                    }
                }
            }

            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }
            
            // Phase 1: Register lifecycle listeners BEFORE triggering clean builds
            // This avoids race condition where STOPPED event could be missed
            reporter.stage(STAGE_LIFECYCLE_PREPARE, "Preparing lifecycle listeners"); //$NON-NLS-1$
            List<ProjectRestartWaiter> waiters = new ArrayList<>();
            for (ProjectCleanInfo info : projectsToClean)
            {
                if (info.dtProject != null)
                {
                    ProjectRestartWaiter waiter = LifecycleWaiter.prepareForRestart(info.dtProject);
                    if (waiter != null)
                    {
                        waiters.add(waiter);
                    }
                }
            }

            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }
            
            // Phase 2: Trigger clean build for all projects
            reporter.indeterminate(STAGE_CLEAN_BUILD, "Triggering clean build"); //$NON-NLS-1$
            for (ProjectCleanInfo info : projectsToClean)
            {
                cleanSingleProject(info.project, monitor);
            }
            buildTriggered = !projectsToClean.isEmpty();

            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }
            
            // Phase 3: Wait for lifecycle restarts (STOPPED -> STARTED)
            reporter.indeterminate(STAGE_LIFECYCLE_WAIT, "Waiting for EDT lifecycle restart"); //$NON-NLS-1$
            if (!awaitLifecycleRestarts(waiters))
            {
                backgroundContinuationExpected = buildTriggered;
                String message = buildTimeoutMessage("EDT lifecycle restart", projectNamesList); //$NON-NLS-1$
                reporter.failed(message, null);
                return ToolResult.error(message).toJson();
            }

            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }
            
            // Phase 4: Wait for derived data computations
            reporter.indeterminate(STAGE_DERIVED_DATA, "Waiting for derived data computations"); //$NON-NLS-1$
            if (!awaitDerivedData(projectsToClean))
            {
                backgroundContinuationExpected = buildTriggered;
                String message = buildTimeoutMessage("derived data computations", projectNamesList); //$NON-NLS-1$
                reporter.failed(message, null);
                return ToolResult.error(message).toJson();
            }

            String message = "Clean and revalidation completed."; //$NON-NLS-1$
            reporter.stage(STAGE_COMPLETION, message);
            reporter.completed(message);
            return ToolResult.success()
                .put("projectsCleaned", projectNamesList.size()) //$NON-NLS-1$
                .put("projects", projectNamesList) //$NON-NLS-1$
                .put("message", message) //$NON-NLS-1$
                .toJson();
        }
        catch (Exception e)
        {
            Activator.logError("Error during project clean", e); //$NON-NLS-1$
            reporter.stage(STAGE_FAILURE, "Project clean failed"); //$NON-NLS-1$
            reporter.failed("Project clean failed: " + e.getMessage(), e); //$NON-NLS-1$
            return ToolResult.error(e.getMessage()).toJson();
        }
        finally
        {
            if (!detachedHandoff && buildTriggered
                    && (isCancellationRequested(cancellationToken, monitor) || backgroundContinuationExpected))
            {
                detachedHandoff = DerivedDataDetachedTracker.handoff(server, reporter, extractProjects(projectsToClean));
            }
            if (!detachedHandoff)
            {
                clearActiveOperation(server, context);
            }
        }
    }
    
    /**
     * Cleans a single project using Eclipse CLEAN_BUILD.
     * This triggers EDT's full project rebuild including:
     * - CLEAN phase (stops project context)
     * - CLEAN_IMPORT phase (imports and rebuilds)
     * - LINKING, INITIALIZATION, CHECKING, etc.
     * 
     * @param project the project to clean
     * @param monitor progress monitor
     * @throws CoreException if build fails
     */
    private static void cleanSingleProject(IProject project, IProgressMonitor monitor) throws CoreException
    {
        Activator.logInfo("Cleaning project (CLEAN_BUILD): " + project.getName()); //$NON-NLS-1$
        
        // Step 1: Refresh from disk to detect external changes
        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        
        // Step 2: Trigger Eclipse Clean Build - this invokes EDT's clean handler
        // which stops project context, clears all data, and reimports
        project.build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
        
        Activator.logInfo("Clean build scheduled for: " + project.getName()); //$NON-NLS-1$
    }
    
    /**
     * Helper class to store project info for cleaning.
     */
    private static class ProjectCleanInfo
    {
        final IProject project;
        final IDtProject dtProject;
        
        ProjectCleanInfo(IProject project, IDtProject dtProject)
        {
            this.project = project;
            this.dtProject = dtProject;
        }
    }

    private List<IProject> extractProjects(List<ProjectCleanInfo> projectsToClean)
    {
        List<IProject> projects = new ArrayList<>();
        for (ProjectCleanInfo info : projectsToClean)
        {
            if (info != null && info.project != null)
            {
                projects.add(info.project);
            }
        }
        return projects;
    }

    private boolean awaitLifecycleRestarts(List<ProjectRestartWaiter> waiters)
    {
        for (ProjectRestartWaiter waiter : waiters)
        {
            if (waiter != null && !waiter.await(DEFAULT_LIFECYCLE_TIMEOUT_MS))
            {
                return false;
            }
        }
        return true;
    }

    private boolean awaitDerivedData(List<ProjectCleanInfo> projectsToClean)
    {
        for (ProjectCleanInfo info : projectsToClean)
        {
            if (info != null && info.project != null && !BuildUtils.waitForDerivedData(info.project))
            {
                return false;
            }
        }
        return true;
    }

    private String buildTimeoutMessage(String phase, List<String> projectNames)
    {
        StringBuilder sb = new StringBuilder("Timed out waiting for "); //$NON-NLS-1$
        sb.append(phase);
        if (projectNames != null && !projectNames.isEmpty())
        {
            sb.append(" during clean/revalidation of "); //$NON-NLS-1$
            sb.append(String.join(", ", projectNames)); //$NON-NLS-1$
        }
        sb.append(". EDT work may still continue in background; poll get_active_operation for detached status."); //$NON-NLS-1$
        return sb.toString();
    }

    private OperationProgressReporter createProgressReporter(ToolExecutionContext context, String projectName)
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        String target = hasText(projectName) ? projectName : "all EDT projects"; //$NON-NLS-1$
        reporter.start(context != null ? context.getOperationId() : null, NAME, STAGE_VALIDATION,
                "Preparing clean build for " + target, context != null ? context.getRequestId() : null, //$NON-NLS-1$
                context != null ? context.getSessionId() : null, context != null ? context.getProgressToken() : null);
        return reporter;
    }

    private void registerActiveOperation(McpServer server, OperationProgressReporter reporter, ToolExecutionContext context)
    {
        if (server == null)
        {
            return;
        }
        if (context != null && context.getOperationId() != null)
        {
            reporter.appendStateListener(state -> server.getTaskRegistry().updateProgress(context.getOperationId(), state));
        }
        server.setActiveOperation(reporter);
    }

    private void clearActiveOperation(McpServer server, ToolExecutionContext context)
    {
        if (server != null)
        {
            server.clearActiveOperation(context != null ? context.getOperationId() : null);
        }
    }

    private boolean isCancellationRequested(TaskCancellationToken cancellationToken, IProgressMonitor monitor)
    {
        boolean cancelled = cancellationToken != null && cancellationToken.isCancellationRequested();
        if (cancelled)
        {
            monitor.setCanceled(true);
        }
        return cancelled || monitor.isCanceled() || Thread.currentThread().isInterrupted();
    }

    private String cancelled(OperationProgressReporter reporter)
    {
        String message = "Project clean cancelled"; //$NON-NLS-1$
        reporter.stage(STAGE_FAILURE, message);
        reporter.cancelled(message);
        return ToolResult.error(message).toJson();
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
