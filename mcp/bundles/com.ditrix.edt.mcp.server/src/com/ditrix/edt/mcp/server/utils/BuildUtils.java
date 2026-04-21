/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;

import com._1c.g5.v8.derived.IDerivedDataManager;
import com._1c.g5.v8.dt.core.platform.IDerivedDataManagerProvider;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com.ditrix.edt.mcp.server.Activator;

/**
 * Utility class for build-related operations.
 */
public final class BuildUtils
{
    /** Default timeout for waiting derived data computations (5 minutes) */
    private static final long DEFAULT_DD_TIMEOUT_MS = 5 * 60 * 1000;
    
    private BuildUtils()
    {
        // Utility class
    }
    
    /**
     * Waits for all build jobs to complete.
     * Joins both auto-build and manual-build job families.
     * 
     * @param monitor progress monitor
     */
    public static void waitForBuildJobs(IProgressMonitor monitor)
    {
        waitForBuildJobs(monitor, null);
    }

    public static void waitForBuildJobs(IProgressMonitor monitor, String diagnosticLabel)
    {
        long startedAt = System.nanoTime();
        try
        {
            logDiagnostic(diagnosticLabel, "waiting for build jobs"); //$NON-NLS-1$
            IJobManager jobManager = Job.getJobManager();
            jobManager.join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
            jobManager.join(ResourcesPlugin.FAMILY_MANUAL_BUILD, monitor);
            logDiagnostic(diagnosticLabel, "build jobs completed in " + elapsedMillis(startedAt) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            logDiagnostic(diagnosticLabel,
                    "build jobs wait interrupted after " + elapsedMillis(startedAt) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
            Activator.logError("Wait for build jobs interrupted", e); //$NON-NLS-1$
        }
    }
    
    /**
     * Waits for build jobs and derived data computations to complete.
     * 
     * <p>Note: This method does NOT wait for lifecycle events. If you need to wait for
     * project restart during clean build, use {@link LifecycleWaiter#prepareForRestart(IDtProject)}
     * BEFORE triggering the build, then call {@link LifecycleWaiter.ProjectRestartWaiter#await(long)}.
     * 
     * @param project the IProject to wait for
     * @param monitor progress monitor
     */
    public static void waitForBuildAndDerivedData(IProject project, IProgressMonitor monitor)
    {
        waitForBuildAndDerivedData(project, DEFAULT_DD_TIMEOUT_MS, monitor, null);
    }

    public static void waitForBuildAndDerivedData(IProject project, IProgressMonitor monitor, String diagnosticLabel)
    {
        waitForBuildAndDerivedData(project, DEFAULT_DD_TIMEOUT_MS, monitor, diagnosticLabel);
    }
    
    /**
     * Waits for build jobs and derived data computations to complete.
     * 
     * <p>Note: This method does NOT wait for lifecycle events. If you need to wait for
     * project restart during clean build, use {@link LifecycleWaiter#prepareForRestart(IDtProject)}
     * BEFORE triggering the build, then call {@link LifecycleWaiter.ProjectRestartWaiter#await(long)}.
     * 
     * @param project the IProject to wait for
     * @param timeoutMs timeout in milliseconds for derived data wait
     * @param monitor progress monitor
     */
    public static void waitForBuildAndDerivedData(IProject project, long timeoutMs, IProgressMonitor monitor)
    {
        waitForBuildAndDerivedData(project, timeoutMs, monitor, null);
    }

    public static void waitForBuildAndDerivedData(IProject project, long timeoutMs, IProgressMonitor monitor,
            String diagnosticLabel)
    {
        long startedAt = System.nanoTime();
        // Step 1: Wait for standard build jobs to complete scheduling
        waitForBuildJobs(monitor, diagnosticLabel);
        
        // Step 2: Wait for derived data computations (validation, form dd, etc.)
        if (project != null)
        {
            waitForDerivedData(project, timeoutMs, diagnosticLabel);
        }
        logDiagnostic(diagnosticLabel, "build and derived-data wait completed in " + elapsedMillis(startedAt) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    /**
     * Waits for derived data computations to complete for a project.
     * Uses default timeout of 5 minutes.
     * 
     * @param project the IProject to wait for
     */
    public static boolean waitForDerivedData(IProject project)
    {
        return waitForDerivedData(project, DEFAULT_DD_TIMEOUT_MS, null);
    }
    
    /**
     * Waits for derived data computations to complete for a project.
     * This includes validation, managed form computations, and other EDT-specific processing.
     * 
     * @param project the IProject to wait for
     * @param timeoutMs timeout in milliseconds
     */
    public static boolean waitForDerivedData(IProject project, long timeoutMs)
    {
        return waitForDerivedData(project, timeoutMs, null);
    }

    public static boolean waitForDerivedData(IProject project, long timeoutMs, String diagnosticLabel)
    {
        long startedAt = System.nanoTime();
        try
        {
            IDerivedDataManagerProvider ddProvider = Activator.getDefault().getDerivedDataManagerProvider();
            if (ddProvider == null)
            {
                Activator.logInfo("IDerivedDataManagerProvider not available, skipping DD wait"); //$NON-NLS-1$
                return true;
            }
            
            // Get DtProject for the IProject
            IDtProjectManager dtProjectManager = Activator.getDefault().getDtProjectManager();
            if (dtProjectManager == null)
            {
                Activator.logInfo("IDtProjectManager not available, skipping DD wait"); //$NON-NLS-1$
                return true;
            }
            
            IDtProject dtProject = dtProjectManager.getDtProject(project);
            if (dtProject == null)
            {
                Activator.logInfo("Not a DtProject, skipping DD wait: " + project.getName()); //$NON-NLS-1$
                return true;
            }
            
            // Get DerivedDataManager for the project
            IDerivedDataManager ddManager = ddProvider.get(dtProject);
            if (ddManager == null)
            {
                Activator.logInfo("IDerivedDataManager not available for project: " + project.getName()); //$NON-NLS-1$
                return true;
            }
            
            // Wait for all derived data computations
            Activator.logInfo("Waiting for derived data computations for: " + project.getName()); //$NON-NLS-1$
            logDiagnostic(diagnosticLabel,
                    "waiting for derived data for " + project.getName() + " timeoutMs=" + timeoutMs); //$NON-NLS-1$ //$NON-NLS-2$
            boolean completed = ddManager.waitAllComputations(timeoutMs);
            
            if (completed)
            {
                Activator.logInfo("Derived data computations completed for: " + project.getName()); //$NON-NLS-1$
                logDiagnostic(diagnosticLabel,
                        "derived data completed for " + project.getName() + " in " + elapsedMillis(startedAt) //$NON-NLS-1$ //$NON-NLS-2$
                                + "ms"); //$NON-NLS-1$
            }
            else
            {
                Activator.logInfo("Derived data wait timed out for: " + project.getName()); //$NON-NLS-1$
                logDiagnostic(diagnosticLabel,
                        "derived data wait timed out for " + project.getName() + " after " //$NON-NLS-1$ //$NON-NLS-2$
                                + elapsedMillis(startedAt) + "ms"); //$NON-NLS-1$
            }
            return completed;
        }
        catch (Exception e)
        {
            logDiagnostic(diagnosticLabel,
                    "derived data wait failed for " + (project != null ? project.getName() : "<null>") + " after " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + elapsedMillis(startedAt) + "ms"); //$NON-NLS-1$
            Activator.logError("Error waiting for derived data", e); //$NON-NLS-1$
            return false;
        }
    }

    private static void logDiagnostic(String diagnosticLabel, String message)
    {
        if (diagnosticLabel == null || diagnosticLabel.isBlank())
        {
            return;
        }
        Activator.logInfo("[diag] " + diagnosticLabel + " :: " + message); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static long elapsedMillis(long startedAt)
    {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
