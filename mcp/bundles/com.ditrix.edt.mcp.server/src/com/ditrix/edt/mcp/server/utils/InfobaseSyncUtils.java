/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.ecore.EObject;

import com._1c.g5.designer.ssh.client.operation.IDbStructureChange;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseConfigurationChange;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseUpdateCallback;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseUpdateConflictResolver;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseConflictResolutionResult;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationException;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Helpers for working with 2024.x infobase synchronization APIs.
 */
public final class InfobaseSyncUtils
{
    private InfobaseSyncUtils()
    {
        // Utility class
    }

    /**
     * Returns infobase application adapter or null if the application is not an infobase.
     *
     * @param application the application to inspect
     * @return infobase application or null
     */
    public static IInfobaseApplication asInfobaseApplication(IApplication application)
    {
        if (application instanceof IInfobaseApplication infobaseApplication)
        {
            return infobaseApplication;
        }
        return null;
    }

    /**
     * Creates default callback for non-interactive synchronization.
     *
     * @param autoConfirmRestructure whether database structure changes should be accepted automatically
     * @return synchronization callback
     */
    public static IInfobaseUpdateCallback createUpdateCallback(boolean autoConfirmRestructure)
    {
        return createUpdateCallback(autoConfirmRestructure, null);
    }

    /**
     * Creates callback for non-interactive synchronization with optional progress reporting.
     *
     * @param autoConfirmRestructure whether database structure changes should be accepted automatically
     * @param reporter progress reporter for domain-specific synchronization phases
     * @return synchronization callback
     */
    public static IInfobaseUpdateCallback createUpdateCallback(boolean autoConfirmRestructure,
            OperationProgressReporter reporter)
    {
        return new IInfobaseUpdateCallback()
        {
            @Override
            public boolean onConfirm(IProject project, InfobaseReference infobase, List<IDbStructureChange> changes,
                    IProgressMonitor monitor)
            {
                report(reporter, "db_structure_confirmation", //$NON-NLS-1$
                        "Database structure confirmation requested"); //$NON-NLS-1$
                if (!autoConfirmRestructure)
                {
                    report(reporter, "db_structure_confirmation", //$NON-NLS-1$
                            "Database structure changes require manual confirmation"); //$NON-NLS-1$
                    Activator.logInfo("Database structure changes require confirmation for project: " //$NON-NLS-1$
                            + project.getName());
                }
                else
                {
                    report(reporter, "db_structure_confirmation", //$NON-NLS-1$
                            "Database structure changes auto-confirmed"); //$NON-NLS-1$
                }
                return autoConfirmRestructure;
            }

            @Override
            public InfobaseConflictResolutionResult onInfobaseChanges(IProject project, InfobaseReference infobase,
                    Set<EObject> changedObjects, IInfobaseConfigurationChange configurationChange,
                    IInfobaseUpdateConflictResolver conflictResolver,
                    IInfobaseUpdateConflictResolver.IConflictResolveAssist conflictResolveAssist,
                    IProgressMonitor monitor) throws InfobaseSynchronizationException
            {
                report(reporter, "conflict_override", //$NON-NLS-1$
                        "Overriding infobase changes with project state"); //$NON-NLS-1$
                Activator.logInfo("Overriding infobase changes with project state for project: " //$NON-NLS-1$
                        + project.getName());
                InfobaseConflictResolutionResult result = conflictResolver.overrideConflict(project, infobase,
                        changedObjects, configurationChange,
                        conflictResolveAssist, monitor);
                report(reporter, "conflict_override", //$NON-NLS-1$
                        "Infobase conflict override completed"); //$NON-NLS-1$
                return result;
            }
        };
    }

    private static void report(OperationProgressReporter reporter, String stage, String message)
    {
        if (reporter != null)
        {
            reporter.indeterminate(stage, message);
        }
    }

    /**
     * Derives a user-facing update state from synchronization state.
     *
     * @param synchronizationState current synchronization state
     * @param equalityState current equality state
     * @return derived update state string compatible with existing tool output
     */
    public static String deriveUpdateState(InfobaseSynchronizationState synchronizationState,
            InfobaseEqualityState equalityState)
    {
        if (synchronizationState == null)
        {
            return "UNKNOWN"; //$NON-NLS-1$
        }
        if (synchronizationState == InfobaseSynchronizationState.SYNCHRONIZING
                || synchronizationState == InfobaseSynchronizationState.MERGING)
        {
            return "BEING_UPDATED"; //$NON-NLS-1$
        }
        if (synchronizationState == InfobaseSynchronizationState.NOT_CONNECTED)
        {
            return "FULL_UPDATE_REQUIRED"; //$NON-NLS-1$
        }
        if (synchronizationState == InfobaseSynchronizationState.SYNCHRONIZED
                && equalityState == InfobaseEqualityState.EQUAL)
        {
            return "UPDATED"; //$NON-NLS-1$
        }
        return "INCREMENTAL_UPDATE_REQUIRED"; //$NON-NLS-1$
    }

    /**
     * Returns a human-readable description for derived update state.
     *
     * @param updateState derived update state
     * @return description
     */
    public static String describeUpdateState(String updateState)
    {
        switch (updateState)
        {
            case "FULL_UPDATE_REQUIRED": //$NON-NLS-1$
                return "Initial synchronization required"; //$NON-NLS-1$
            case "INCREMENTAL_UPDATE_REQUIRED": //$NON-NLS-1$
                return "Synchronization required"; //$NON-NLS-1$
            case "UPDATED": //$NON-NLS-1$
                return "Up to date"; //$NON-NLS-1$
            case "BEING_UPDATED": //$NON-NLS-1$
                return "Currently synchronizing"; //$NON-NLS-1$
            default:
                return updateState;
        }
    }
}
