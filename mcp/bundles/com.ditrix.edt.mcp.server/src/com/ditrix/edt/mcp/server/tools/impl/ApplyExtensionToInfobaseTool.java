/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationException;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.CapturingProgressMonitor;
import com.ditrix.edt.mcp.server.progress.InfobaseSynchronizationDetachedTracker;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tasks.TaskCancellationToken;
import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.BlockingOperationDiagnostics;
import com.ditrix.edt.mcp.server.utils.ExtensionLifecycleFailure;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.InfobaseSyncUtils;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.ditrix.edt.mcp.server.utils.ResolvedExtensionRuntimeContext;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Tool to apply an extension project to a selected infobase target through the EDT synchronization bridge.
 */
public class ApplyExtensionToInfobaseTool implements IMcpTool
{
    public static final String NAME = "apply_extension_to_infobase"; //$NON-NLS-1$

    private static final String STAGE_VALIDATION = "validation"; //$NON-NLS-1$
    private static final String STAGE_SYNC_STATE_CHECK = "sync_state_check"; //$NON-NLS-1$
    private static final String STAGE_APPLY_START = "apply_start"; //$NON-NLS-1$
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
        return "Apply an extension project to a selected infobase target. Async-first at runtime: bare calls auto-promote into task-backed execution, and the final result is retrieved via tasks/result in the same MCP session."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Extension project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_extension_runtime_targets (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("fullReload", "If true - full reload, if false - incremental sync (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("autoRestructure", "Automatically apply restructurization if needed (default: true)") //$NON-NLS-1$ //$NON-NLS-2$
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
        if (projectName == null || projectName.isBlank())
        {
            return TaskSchedulingKey.none();
        }

        ExtensionRuntimeContextResolver.Resolution resolution = ExtensionRuntimeContextResolver.resolve(NAME,
                projectName);
        if (resolution.isResolved() && resolution.getContext().getParentProjectName() != null)
        {
            return TaskSchedulingKey.projectScoped(resolution.getContext().getParentProjectName());
        }
        return TaskSchedulingKey.projectScoped(projectName);
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        boolean fullReload = JsonUtils.extractBooleanArgument(params, "fullReload", false); //$NON-NLS-1$
        boolean autoRestructure = JsonUtils.extractBooleanArgument(params, "autoRestructure", true); //$NON-NLS-1$

        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (applicationId == null || applicationId.isEmpty())
        {
            return ToolResult.error("applicationId is required. Use get_extension_runtime_targets first.").toJson(); //$NON-NLS-1$
        }

        ToolResult notReadyResult = ProjectStateChecker.checkReadyOrErrorResult(projectName);
        if (notReadyResult != null)
        {
            return notReadyResult.toJson();
        }

        ExtensionRuntimeContextResolver.Resolution resolution = ExtensionRuntimeContextResolver.resolve(NAME, projectName);
        if (!resolution.isResolved())
        {
            return resolution.getFailureResult().toJson();
        }

        ResolvedExtensionRuntimeContext extensionContext = resolution.getContext();
        IProject parentProject = extensionContext.getParentProject();
        if (parentProject == null)
        {
            return ExtensionLifecycleFailure.parentMissing(NAME, extensionContext.getProjectContext(),
                    "The extension project has no linked parent configuration project.").toJson(); //$NON-NLS-1$
        }

        ToolResult parentNotReadyResult = ProjectStateChecker.checkReadyOrErrorResult(parentProject);
        if (parentNotReadyResult != null)
        {
            return parentNotReadyResult.toJson();
        }

        return applyExtensionToInfobase(extensionContext, applicationId, fullReload, autoRestructure);
    }

    private String applyExtensionToInfobase(ResolvedExtensionRuntimeContext extensionContext, String applicationId,
            boolean fullReload, boolean autoRestructure)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        ToolExecutionContext toolContext = ToolExecutionContextHolder.get();
        OperationProgressReporter reporter = createProgressReporter(toolContext, extensionContext, applicationId,
                fullReload, autoRestructure);
        CapturingProgressMonitor monitor = new CapturingProgressMonitor(reporter);
        TaskCancellationToken cancellationToken = toolContext != null ? toolContext.getCancellationToken() : null;
        IProject extensionProject = extensionContext.getProjectContext().getProject();
        IInfobaseApplication infobaseApplication = null;
        IInfobaseSynchronizationManager synchronizationManager = null;
        String applicationName = null;
        boolean syncTriggered = false;
        boolean detachedHandoff = false;

        if (isCancellationRequested(cancellationToken, monitor))
        {
            return cancelled(reporter);
        }
        registerActiveOperation(server, reporter);

        try
        {
            reporter.stage(STAGE_VALIDATION, "Validating extension project and runtime target"); //$NON-NLS-1$
            if (extensionProject == null || !extensionProject.exists())
            {
                return fail(reporter,
                        ToolResult.error("Project not found: " + extensionContext.getProjectContext().getProjectName()), //$NON-NLS-1$
                        "Extension project not found"); //$NON-NLS-1$
            }
            if (!extensionProject.isOpen())
            {
                return fail(reporter,
                        ToolResult.error("Project is closed: " + extensionContext.getProjectContext().getProjectName()), //$NON-NLS-1$
                        "Extension project is closed"); //$NON-NLS-1$
            }
            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }

            ExtensionRuntimeContextResolver.ApplicationResolution applicationResolution = ExtensionRuntimeContextResolver
                    .resolveApplication(NAME, extensionContext, applicationId);
            if (!applicationResolution.isResolved())
            {
                return fail(reporter, applicationResolution.getFailureResult(), "Failed to resolve runtime target"); //$NON-NLS-1$
            }

            ToolResult accessSettingsFailure = ExtensionRuntimeBridgeSupport.preflightAccessSettings(NAME,
                    extensionContext, applicationResolution.getInfobaseApplication(), applicationId);
            if (accessSettingsFailure != null)
            {
                return fail(reporter, accessSettingsFailure, "Infobase access settings are not ready"); //$NON-NLS-1$
            }

            synchronizationManager = Activator.getDefault() != null
                    ? Activator.getDefault().getInfobaseSynchronizationManager()
                    : null;
            if (synchronizationManager == null)
            {
                return fail(reporter,
                        ExtensionLifecycleFailure.runtimeServiceUnavailable(NAME,
                                extensionContext.getProjectContext(), extensionContext.getParentProjectName(),
                                "IInfobaseSynchronizationManager service is not available."), //$NON-NLS-1$
                        "Synchronization manager service is not available"); //$NON-NLS-1$
            }

            IApplication application = applicationResolution.getApplication();
            applicationName = application.getName();
            infobaseApplication = applicationResolution.getInfobaseApplication();
            reporter.updateDetails(buildOperationDetails(extensionContext, applicationId, applicationName));
            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }

            reporter.stage(STAGE_SYNC_STATE_CHECK, "Checking synchronization state before extension apply"); //$NON-NLS-1$
            InfobaseSynchronizationState synchronizationStateBefore = synchronizationManager
                    .getSynchronizationState(extensionProject, infobaseApplication.getInfobase());
            InfobaseEqualityState equalityStateBefore = synchronizationManager
                    .getEqualityState(extensionProject, infobaseApplication.getInfobase());
            String updateStateBefore = InfobaseSyncUtils.deriveUpdateState(synchronizationStateBefore,
                    equalityStateBefore);
            if ("BEING_UPDATED".equals(updateStateBefore)) //$NON-NLS-1$
            {
                return applicationSyncBusy(server, reporter, extensionContext, applicationId, applicationName);
            }
            String invalidModeMessage = InfobaseSyncUtils.validateRequestedUpdateMode(fullReload, updateStateBefore);
            if (invalidModeMessage != null)
            {
                return fail(reporter, ToolResult.error(invalidModeMessage), invalidModeMessage);
            }

            String applyMode = fullReload ? "FULL" : "INCREMENTAL"; //$NON-NLS-1$ //$NON-NLS-2$
            Activator.logInfo("Apply extension to infobase: project=" //$NON-NLS-1$
                    + extensionContext.getProjectContext().getProjectName() + ", parentProject=" //$NON-NLS-1$
                    + extensionContext.getParentProjectName() + ", application=" + applicationId + ", mode=" //$NON-NLS-1$ //$NON-NLS-2$
                    + applyMode + ", autoRestructure=" + autoRestructure); //$NON-NLS-1$
            String completionMessage;
            reporter.stage(STAGE_APPLY_START,
                    "Starting " + applyMode.toLowerCase(Locale.ROOT) + " extension apply"); //$NON-NLS-1$ //$NON-NLS-2$
            reporter.indeterminate(STAGE_WAITING_FOR_EDT, "Waiting for EDT synchronization to finish"); //$NON-NLS-1$
            syncTriggered = true;

            boolean updated = fullReload
                    ? synchronizationManager.reloadInfobase(extensionProject, infobaseApplication.getInfobase(),
                            InfobaseSyncUtils.createUpdateCallback(autoRestructure, reporter), true, monitor)
                    : synchronizationManager.updateInfobase(extensionProject, infobaseApplication.getInfobase(),
                            InfobaseSyncUtils.createUpdateCallback(autoRestructure, reporter), true, monitor);
            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }

            reporter.stage(STAGE_FINAL_STATE, "Reading final synchronization state"); //$NON-NLS-1$
            InfobaseSynchronizationState synchronizationStateAfter = synchronizationManager
                    .getSynchronizationState(extensionProject, infobaseApplication.getInfobase());
            InfobaseEqualityState equalityStateAfter = synchronizationManager
                    .getEqualityState(extensionProject, infobaseApplication.getInfobase());
            String updateStateAfter = InfobaseSyncUtils.deriveUpdateState(synchronizationStateAfter,
                    equalityStateAfter);

            ToolResult result = ToolResult.success()
                    .put("projectName", extensionContext.getProjectContext().getProjectName()) //$NON-NLS-1$
                    .put("extensionName", extensionContext.getRuntimeExtensionName()) //$NON-NLS-1$
                    .put("parentProjectName", extensionContext.getParentProjectName()) //$NON-NLS-1$
                    .put("applicationId", applicationId) //$NON-NLS-1$
                    .put("applicationName", applicationName) //$NON-NLS-1$
                    .put("applyMode", applyMode) //$NON-NLS-1$
                    .put("updated", updated) //$NON-NLS-1$
                    .put("updateStateBefore", updateStateBefore) //$NON-NLS-1$
                    .put("updateStateAfter", updateStateAfter) //$NON-NLS-1$
                    .put("syncStateBefore", synchronizationStateBefore.name()) //$NON-NLS-1$
                    .put("syncStateAfter", synchronizationStateAfter.name()) //$NON-NLS-1$
                    .put("equalityStateBefore", equalityStateBefore.name()) //$NON-NLS-1$
                    .put("equalityStateAfter", equalityStateAfter.name()) //$NON-NLS-1$
                    .put("autoRestructure", autoRestructure) //$NON-NLS-1$
                    .put("fullReload", fullReload); //$NON-NLS-1$

            if (updated)
            {
                completionMessage = "Extension applied successfully"; //$NON-NLS-1$
            }
            else
            {
                completionMessage = "Extension apply was aborted or requires confirmation"; //$NON-NLS-1$
            }
            result.put("message", completionMessage); //$NON-NLS-1$
            reporter.stage(STAGE_COMPLETION, completionMessage);
            reporter.completed(completionMessage);
            return result.toJson();
        }
        catch (InfobaseSynchronizationException e)
        {
            Activator.logError("Error applying extension to infobase for application: " + applicationId, e); //$NON-NLS-1$
            reporter.stage(STAGE_FAILURE, "Extension apply failed"); //$NON-NLS-1$
            reporter.failed("Extension apply failed: " + e.getMessage(), e); //$NON-NLS-1$
            return ExtensionLifecycleFailure.applyFailed(NAME, extensionContext.getProjectContext(),
                    extensionContext.getParentProjectName(), applicationId, e.getMessage()).toJson();
        }
        catch (Exception e)
        {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Activator.logError("Unexpected error during extension apply", cause); //$NON-NLS-1$
            reporter.stage(STAGE_FAILURE, "Unexpected extension apply error"); //$NON-NLS-1$
            reporter.failed("Unexpected error: " + cause.getMessage(), cause); //$NON-NLS-1$
            return ExtensionLifecycleFailure.applyFailed(NAME, extensionContext.getProjectContext(),
                    extensionContext.getParentProjectName(), applicationId, cause.getMessage()).toJson();
        }
        finally
        {
            if (!detachedHandoff && syncTriggered && isCancellationRequested(cancellationToken, monitor)
                    && extensionProject != null && infobaseApplication != null && synchronizationManager != null)
            {
                detachedHandoff = InfobaseSynchronizationDetachedTracker.handoff(server, reporter, extensionProject,
                        infobaseApplication.getInfobase(), synchronizationManager, applicationId, applicationName);
            }
            if (!detachedHandoff)
            {
                clearActiveOperation(server, toolContext);
            }
        }
    }

    private OperationProgressReporter createProgressReporter(ToolExecutionContext toolContext,
            ResolvedExtensionRuntimeContext extensionContext, String applicationId, boolean fullReload,
            boolean autoRestructure)
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        String applyMode = fullReload ? "full" : "incremental"; //$NON-NLS-1$ //$NON-NLS-2$
        reporter.start(toolContext != null ? toolContext.getOperationId() : null, NAME, STAGE_VALIDATION,
                "Preparing " + applyMode + " extension apply; autoRestructure=" + autoRestructure, //$NON-NLS-1$ //$NON-NLS-2$
                toolContext != null ? toolContext.getRequestId() : null,
                toolContext != null ? toolContext.getSessionId() : null,
                toolContext != null ? toolContext.getProgressToken() : null,
                buildOperationDetails(extensionContext, applicationId, null));
        return reporter;
    }

    private Map<String, Object> buildOperationDetails(ResolvedExtensionRuntimeContext extensionContext,
            String applicationId, String applicationName)
    {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        String projectName = extensionContext.getParentProjectName() != null
                ? extensionContext.getParentProjectName()
                : extensionContext.getProjectContext().getProjectName();
        details.put("projectName", projectName); //$NON-NLS-1$
        details.put("extensionProjectName", extensionContext.getProjectContext().getProjectName()); //$NON-NLS-1$
        if (extensionContext.getParentProjectName() != null)
        {
            details.put("parentProjectName", extensionContext.getParentProjectName()); //$NON-NLS-1$
        }
        if (extensionContext.getRuntimeExtensionName() != null)
        {
            details.put("extensionName", extensionContext.getRuntimeExtensionName()); //$NON-NLS-1$
        }
        details.put("applicationId", applicationId); //$NON-NLS-1$
        if (applicationName != null && !applicationName.isBlank())
        {
            details.put("applicationName", applicationName); //$NON-NLS-1$
        }
        return details;
    }

    private void registerActiveOperation(McpServer server, OperationProgressReporter reporter)
    {
        if (server != null)
        {
            ToolExecutionContext context = ToolExecutionContextHolder.get();
            if (context != null && context.getOperationId() != null)
            {
                reporter.appendStateListener(
                        state -> server.getTaskRegistry().updateProgress(context.getOperationId(), state));
            }
            server.setActiveOperation(reporter);
        }
    }

    private void clearActiveOperation(McpServer server, ToolExecutionContext context)
    {
        if (server != null)
        {
            server.clearActiveOperation(context != null ? context.getOperationId() : null);
        }
    }

    private String fail(OperationProgressReporter reporter, ToolResult failureResult, String message)
    {
        reporter.stage(STAGE_FAILURE, message);
        reporter.failed(message, null);
        return failureResult.toJson();
    }

    private String applicationSyncBusy(McpServer server, OperationProgressReporter reporter,
            ResolvedExtensionRuntimeContext extensionContext, String applicationId, String applicationName)
    {
        String message = "Application synchronization is already in progress. Please wait and retry."; //$NON-NLS-1$
        reporter.stage(STAGE_FAILURE, message);
        reporter.failed(message, null);
        ToolResult blocked = BlockingOperationDiagnostics.applicationSyncBlocked(server,
                extensionContext.getParentProjectName() != null ? extensionContext.getParentProjectName()
                        : extensionContext.getProjectContext().getProjectName(),
                applicationId, applicationName, message);
        blocked.put("projectName", extensionContext.getProjectContext().getProjectName()); //$NON-NLS-1$
        blocked.put("parentProjectName", extensionContext.getParentProjectName()); //$NON-NLS-1$
        blocked.put("extensionName", extensionContext.getRuntimeExtensionName()); //$NON-NLS-1$
        return blocked.toJson();
    }

    private boolean isCancellationRequested(TaskCancellationToken cancellationToken, CapturingProgressMonitor monitor)
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
        String message = "Extension apply cancelled"; //$NON-NLS-1$
        reporter.stage(STAGE_FAILURE, message);
        reporter.cancelled(message);
        return ToolResult.error(message).toJson();
    }
}
