/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;

import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationException;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.progress.CapturingProgressMonitor;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.InfobaseSyncUtils;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.e1c.g5.dt.applications.ApplicationException;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Tool to update database (infobase) for an application.
 */
public class UpdateDatabaseTool implements IMcpTool
{
    public static final String NAME = "update_database"; //$NON-NLS-1$

    private static final String STAGE_VALIDATION = "validation"; //$NON-NLS-1$
    private static final String STAGE_SYNC_STATE_CHECK = "sync_state_check"; //$NON-NLS-1$
    private static final String STAGE_UPDATE_START = "update_start"; //$NON-NLS-1$
    private static final String STAGE_WAITING_FOR_EDT = "waiting_for_edt"; //$NON-NLS-1$
    private static final String STAGE_FINAL_STATE = "final_state_check"; //$NON-NLS-1$
    private static final String STAGE_COMPLETION = "completion"; //$NON-NLS-1$
    private static final String STAGE_FAILURE = "failure"; //$NON-NLS-1$
    
    @Override
    public String getName()
    {
        return NAME;
    }
    
    @Override
    public String getDescription()
    {
        return "Update database (infobase) for an application. " + //$NON-NLS-1$
               "Requires application ID from get_applications tool. " + //$NON-NLS-1$
               "Supports full update (complete reload) and incremental update (changes only)."; //$NON-NLS-1$
    }
    
    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("fullUpdate", "If true - full reload, if false - incremental update (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("autoRestructure", "Automatically apply restructurization if needed (default: true)") //$NON-NLS-1$ //$NON-NLS-2$
            .build();
    }
    
    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }
    
    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        boolean fullUpdate = JsonUtils.extractBooleanArgument(params, "fullUpdate", false); //$NON-NLS-1$
        boolean autoRestructure = JsonUtils.extractBooleanArgument(params, "autoRestructure", true); //$NON-NLS-1$
        
        // Validate required parameters
        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        
        if (applicationId == null || applicationId.isEmpty())
        {
            return ToolResult.error("applicationId is required. Use get_applications to get application list.").toJson(); //$NON-NLS-1$
        }
        
        // Check if project is ready for operations
        String notReadyError = ProjectStateChecker.checkReadyOrError(projectName);
        if (notReadyError != null)
        {
            return ToolResult.error(notReadyError).toJson();
        }
        
        return updateDatabase(projectName, applicationId, fullUpdate, autoRestructure);
    }
    
    /**
     * Updates the database for the specified application.
     * 
     * @param projectName name of the project
     * @param applicationId ID of the application
     * @param fullUpdate true for full update, false for incremental
     * @param autoRestructure whether to auto-apply restructurization
     * @return JSON string with result
     */
    private String updateDatabase(String projectName, String applicationId, 
            boolean fullUpdate, boolean autoRestructure)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        ToolExecutionContext context = ToolExecutionContextHolder.get();
        OperationProgressReporter reporter = createProgressReporter(context, fullUpdate, autoRestructure);
        CapturingProgressMonitor monitor = new CapturingProgressMonitor(reporter);
        registerActiveOperation(server, reporter);

        try
        {
            reporter.stage(STAGE_VALIDATION, "Validating project and application state"); //$NON-NLS-1$
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IProject project = workspace.getRoot().getProject(projectName);
            
            if (project == null || !project.exists())
            {
                return fail(reporter, "Project not found: " + projectName); //$NON-NLS-1$
            }
            
            if (!project.isOpen())
            {
                return fail(reporter, "Project is closed: " + projectName); //$NON-NLS-1$
            }
            
            // Get application manager
            IApplicationManager appManager = Activator.getDefault().getApplicationManager();
            if (appManager == null)
            {
                return fail(reporter, "IApplicationManager service is not available"); //$NON-NLS-1$
            }

            IInfobaseSynchronizationManager synchronizationManager = Activator.getDefault()
                    .getInfobaseSynchronizationManager();
            if (synchronizationManager == null)
            {
                return fail(reporter, "IInfobaseSynchronizationManager service is not available"); //$NON-NLS-1$
            }
            
            // Find application by ID
            Optional<IApplication> appOpt = appManager.getApplication(project, applicationId);
            if (!appOpt.isPresent())
            {
                return fail(reporter, "Application not found: " + applicationId + //$NON-NLS-1$
                        ". Use get_applications to get valid application IDs."); //$NON-NLS-1$
            }
            
            IApplication application = appOpt.get();

            IInfobaseApplication infobaseApplication = InfobaseSyncUtils.asInfobaseApplication(application);
            if (infobaseApplication == null)
            {
                return fail(reporter, "Application is not an infobase application: " + applicationId); //$NON-NLS-1$
            }

            reporter.stage(STAGE_SYNC_STATE_CHECK, "Checking synchronization state before update"); //$NON-NLS-1$
            InfobaseSynchronizationState synchronizationStateBefore = synchronizationManager
                    .getSynchronizationState(project, infobaseApplication.getInfobase());
            InfobaseEqualityState equalityStateBefore = synchronizationManager
                    .getEqualityState(project, infobaseApplication.getInfobase());
            String updateStateBefore = InfobaseSyncUtils.deriveUpdateState(synchronizationStateBefore,
                    equalityStateBefore);
            if ("BEING_UPDATED".equals(updateStateBefore)) //$NON-NLS-1$
            {
                return fail(reporter, "Application is currently being synchronized. Please wait."); //$NON-NLS-1$
            }

            String updateType = fullUpdate ? "FULL" : "INCREMENTAL"; //$NON-NLS-1$ //$NON-NLS-2$
            Activator.logInfo("Update database: project=" + projectName +  //$NON-NLS-1$
                    ", application=" + applicationId +  //$NON-NLS-1$
                    ", type=" + updateType +  //$NON-NLS-1$
                    ", autoRestructure=" + autoRestructure); //$NON-NLS-1$
            String completionMessage;
            reporter.stage(STAGE_UPDATE_START,
                    "Starting " + updateType.toLowerCase(Locale.ROOT) + " database update"); //$NON-NLS-1$ //$NON-NLS-2$
            reporter.indeterminate(STAGE_WAITING_FOR_EDT, "Waiting for EDT synchronization to finish"); //$NON-NLS-1$

            boolean updated = fullUpdate
                    ? synchronizationManager.reloadInfobase(project, infobaseApplication.getInfobase(),
                            InfobaseSyncUtils.createUpdateCallback(autoRestructure, reporter), true,
                            monitor)
                    : synchronizationManager.updateInfobase(project, infobaseApplication.getInfobase(),
                            InfobaseSyncUtils.createUpdateCallback(autoRestructure, reporter), true,
                            monitor);

            reporter.stage(STAGE_FINAL_STATE, "Reading final synchronization state"); //$NON-NLS-1$
            InfobaseSynchronizationState synchronizationStateAfter = synchronizationManager
                    .getSynchronizationState(project, infobaseApplication.getInfobase());
            InfobaseEqualityState equalityStateAfter = synchronizationManager
                    .getEqualityState(project, infobaseApplication.getInfobase());
            String updateStateAfter = InfobaseSyncUtils.deriveUpdateState(synchronizationStateAfter,
                    equalityStateAfter);

            ToolResult result = ToolResult.success()
                .put("project", projectName) //$NON-NLS-1$
                .put("applicationId", applicationId) //$NON-NLS-1$
                .put("applicationName", application.getName()) //$NON-NLS-1$
                .put("updateType", updateType) //$NON-NLS-1$
                .put("updated", updated) //$NON-NLS-1$
                .put("stateBefore", updateStateBefore) //$NON-NLS-1$
                .put("stateAfter", updateStateAfter) //$NON-NLS-1$
                .put("syncStateBefore", synchronizationStateBefore.name()) //$NON-NLS-1$
                .put("syncStateAfter", synchronizationStateAfter.name()) //$NON-NLS-1$
                .put("equalityStateBefore", equalityStateBefore.name()) //$NON-NLS-1$
                .put("equalityStateAfter", equalityStateAfter.name()); //$NON-NLS-1$

            if (updated)
            {
                completionMessage = "Database updated successfully"; //$NON-NLS-1$
            }
            else
            {
                completionMessage = "Database update was aborted or requires confirmation"; //$NON-NLS-1$
            }
            result.put("message", completionMessage); //$NON-NLS-1$
            reporter.stage(STAGE_COMPLETION, completionMessage);
            reporter.completed(completionMessage);
            return result.toJson();
        }
        catch (ApplicationException e)
        {
            Activator.logError("Error updating database for application: " + applicationId, e); //$NON-NLS-1$
            reporter.stage(STAGE_FAILURE, "Database update failed"); //$NON-NLS-1$
            reporter.failed("Database update failed: " + e.getMessage(), e); //$NON-NLS-1$
            
            // Return detailed error information
            ToolResult errorResult = ToolResult.error("Database update failed: " + e.getMessage()); //$NON-NLS-1$
            errorResult.put("applicationId", applicationId); //$NON-NLS-1$
            errorResult.put("projectName", projectName); //$NON-NLS-1$
            
            // Try to get additional error details
            if (e.getCause() != null)
            {
                errorResult.put("causeMessage", e.getCause().getMessage()); //$NON-NLS-1$
                errorResult.put("causeType", e.getCause().getClass().getSimpleName()); //$NON-NLS-1$
            }
            
            return errorResult.toJson();
        }
        catch (InfobaseSynchronizationException e)
        {
            Activator.logError("Error synchronizing infobase for application: " + applicationId, e); //$NON-NLS-1$
            reporter.stage(STAGE_FAILURE, "Database synchronization failed"); //$NON-NLS-1$
            reporter.failed("Database synchronization failed: " + e.getMessage(), e); //$NON-NLS-1$
            return ToolResult.error("Database synchronization failed: " + e.getMessage()).toJson(); //$NON-NLS-1$
        }
        catch (Exception e)
        {
            Activator.logError("Unexpected error during database update", e); //$NON-NLS-1$
            reporter.stage(STAGE_FAILURE, "Unexpected update error"); //$NON-NLS-1$
            reporter.failed("Unexpected error: " + e.getMessage(), e); //$NON-NLS-1$
            return ToolResult.error("Unexpected error: " + e.getMessage()).toJson(); //$NON-NLS-1$
        }
        finally
        {
            clearActiveOperation(server);
        }
    }

    private OperationProgressReporter createProgressReporter(ToolExecutionContext context, boolean fullUpdate,
            boolean autoRestructure)
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        String updateType = fullUpdate ? "full" : "incremental"; //$NON-NLS-1$ //$NON-NLS-2$
        reporter.start(context != null ? context.getOperationId() : null, NAME, STAGE_VALIDATION,
                "Preparing " + updateType + " database update; autoRestructure=" + autoRestructure, //$NON-NLS-1$ //$NON-NLS-2$
                context != null ? context.getRequestId() : null, context != null ? context.getSessionId() : null,
                context != null ? context.getProgressToken() : null);
        return reporter;
    }

    private void registerActiveOperation(McpServer server, OperationProgressReporter reporter)
    {
        if (server != null)
        {
            server.setActiveOperation(reporter);
        }
    }

    private void clearActiveOperation(McpServer server)
    {
        if (server != null)
        {
            server.clearActiveOperation();
        }
    }

    private String fail(OperationProgressReporter reporter, String message)
    {
        reporter.stage(STAGE_FAILURE, message);
        reporter.failed(message, null);
        return ToolResult.error(message).toJson();
    }
}
