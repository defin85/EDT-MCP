/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;

import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.utils.ExtensionLifecycleFailure;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.InfobaseSyncUtils;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.ditrix.edt.mcp.server.utils.ResolvedExtensionRuntimeContext;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Developer-oriented live probe for the internal EDT synchronization bridge on extension projects.
 */
public class ProbeExtensionSyncBridgeTool implements IMcpTool
{
    public static final String NAME = "probe_extension_sync_bridge"; //$NON-NLS-1$

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 300;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Developer-oriented extension lifecycle probe: invoke the same internal EDT synchronization bridge used by apply_extension_to_infobase; use for diagnostics/proof, not normal automation."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Probe extension sync bridge") //$NON-NLS-1$
                .readOnlyHint(false)
                .destructiveHint(true)
                .openWorldHint(true)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Extension project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_extension_runtime_targets (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("fullReload", "Use reloadInfobase instead of incremental updateInfobase (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("autoConfirmRestructure", "Automatically confirm database restructurization if EDT asks for it (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("allowDrift", "Allow probing when the extension target is not already UPDATED/EQUAL (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Guard timeout in seconds for the sync probe (default: 30, max: 300)") //$NON-NLS-1$ //$NON-NLS-2$
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
        boolean fullReload = JsonUtils.extractBooleanArgument(params, "fullReload", false); //$NON-NLS-1$
        boolean autoConfirmRestructure = JsonUtils.extractBooleanArgument(params, "autoConfirmRestructure", false); //$NON-NLS-1$
        boolean allowDrift = JsonUtils.extractBooleanArgument(params, "allowDrift", false); //$NON-NLS-1$
        int timeoutSeconds = Math.max(1, Math.min(MAX_TIMEOUT_SECONDS,
                JsonUtils.extractIntArgument(params, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS))); //$NON-NLS-1$
        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (applicationId == null || applicationId.isEmpty())
        {
            return ToolResult.error("applicationId is required").toJson(); //$NON-NLS-1$
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

        ResolvedExtensionRuntimeContext context = resolution.getContext();
        IProject extensionProject = context.getProjectContext().getProject();
        IProject parentProject = context.getParentProject();
        if (parentProject == null)
        {
            return ExtensionLifecycleFailure.parentMissing(NAME, context.getProjectContext(),
                    "The extension project has no linked parent configuration project.").toJson(); //$NON-NLS-1$
        }

        ToolResult parentNotReadyResult = ProjectStateChecker.checkReadyOrErrorResult(parentProject);
        if (parentNotReadyResult != null)
        {
            return parentNotReadyResult.toJson();
        }

        ExtensionRuntimeContextResolver.ApplicationResolution applicationResolution = ExtensionRuntimeContextResolver
                .resolveApplication(NAME, context, applicationId);
        if (!applicationResolution.isResolved())
        {
            return applicationResolution.getFailureResult().toJson();
        }

        ToolResult accessSettingsFailure = ExtensionRuntimeBridgeSupport.preflightAccessSettings(NAME, context,
                applicationResolution.getInfobaseApplication(), applicationId);
        if (accessSettingsFailure != null)
        {
            return accessSettingsFailure.toJson();
        }

        IInfobaseSynchronizationManager synchronizationManager = Activator.getDefault() != null
                ? Activator.getDefault().getInfobaseSynchronizationManager()
                : null;
        if (synchronizationManager == null)
        {
            return ExtensionLifecycleFailure.runtimeServiceUnavailable(NAME, context.getProjectContext(),
                    context.getParentProjectName(),
                    "IInfobaseSynchronizationManager service is not available.").toJson(); //$NON-NLS-1$
        }

        IApplication application = applicationResolution.getApplication();
        IInfobaseApplication infobaseApplication = applicationResolution.getInfobaseApplication();
        try
        {
            InfobaseSynchronizationState syncStateBefore = synchronizationManager.getSynchronizationState(extensionProject,
                    infobaseApplication.getInfobase());
            InfobaseEqualityState equalityStateBefore = synchronizationManager.getEqualityState(extensionProject,
                    infobaseApplication.getInfobase());
            String updateStateBefore = InfobaseSyncUtils.deriveUpdateState(syncStateBefore, equalityStateBefore);

            if (!allowDrift && !"UPDATED".equals(updateStateBefore)) //$NON-NLS-1$
            {
                return ToolResult.error("Probe is fail-closed for non-synchronized extension targets. " //$NON-NLS-1$
                        + "Current state is " + updateStateBefore + ". Retry only if you explicitly want mutation with allowDrift=true.") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("projectName", projectName) //$NON-NLS-1$
                        .put("applicationId", applicationId) //$NON-NLS-1$
                        .put("applicationName", application.getName()) //$NON-NLS-1$
                        .put("syncStateBefore", syncStateBefore != null ? syncStateBefore.name() : null) //$NON-NLS-1$
                        .put("equalityStateBefore", equalityStateBefore != null ? equalityStateBefore.name() : null) //$NON-NLS-1$
                        .put("updateStateBefore", updateStateBefore) //$NON-NLS-1$
                        .toJson();
            }

            long timeoutMs = timeoutSeconds * 1000L;
            ExtensionRuntimeBridgeSupport.InvocationResult<Boolean> invocation = ExtensionRuntimeBridgeSupport
                    .invokeWithGuard(NAME, context, applicationId, timeoutMs,
                            () -> fullReload
                                    ? synchronizationManager.reloadInfobase(extensionProject,
                                            infobaseApplication.getInfobase(),
                                            InfobaseSyncUtils.createUpdateCallback(autoConfirmRestructure), true,
                                            new NullProgressMonitor())
                                    : synchronizationManager.updateInfobase(extensionProject,
                                            infobaseApplication.getInfobase(),
                                            InfobaseSyncUtils.createUpdateCallback(autoConfirmRestructure), true,
                                            new NullProgressMonitor()));
            if (!invocation.isSuccess())
            {
                return invocation.getFailureResult().toJson();
            }

            Boolean updated = invocation.getValue();
            InfobaseSynchronizationState syncStateAfter = synchronizationManager.getSynchronizationState(extensionProject,
                    infobaseApplication.getInfobase());
            InfobaseEqualityState equalityStateAfter = synchronizationManager.getEqualityState(extensionProject,
                    infobaseApplication.getInfobase());
            String updateStateAfter = InfobaseSyncUtils.deriveUpdateState(syncStateAfter, equalityStateAfter);

            return ToolResult.success()
                    .put("projectName", projectName) //$NON-NLS-1$
                    .put("extensionName", context.getRuntimeExtensionName()) //$NON-NLS-1$
                    .put("parentProjectName", context.getParentProjectName()) //$NON-NLS-1$
                    .put("applicationId", applicationId) //$NON-NLS-1$
                    .put("applicationName", application.getName()) //$NON-NLS-1$
                    .put("probeMode", fullReload ? "reloadInfobase" : "updateInfobase") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .put("autoConfirmRestructure", autoConfirmRestructure) //$NON-NLS-1$
                    .put("allowDrift", allowDrift) //$NON-NLS-1$
                    .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                    .put("syncStateBefore", syncStateBefore != null ? syncStateBefore.name() : null) //$NON-NLS-1$
                    .put("equalityStateBefore", equalityStateBefore != null ? equalityStateBefore.name() : null) //$NON-NLS-1$
                    .put("updateStateBefore", updateStateBefore) //$NON-NLS-1$
                    .put("syncStateAfter", syncStateAfter != null ? syncStateAfter.name() : null) //$NON-NLS-1$
                    .put("equalityStateAfter", equalityStateAfter != null ? equalityStateAfter.name() : null) //$NON-NLS-1$
                    .put("updateStateAfter", updateStateAfter) //$NON-NLS-1$
                    .put("updated", updated) //$NON-NLS-1$
                    .toJson();
        }
        catch (RuntimeException e)
        {
            Activator.logError("Extension sync bridge probe failed for application: " + applicationId, e); //$NON-NLS-1$
            return ExtensionLifecycleFailure.runtimeCheckFailed(NAME, context.getProjectContext(),
                    context.getParentProjectName(), applicationId, e.getMessage()).toJson();
        }
    }
}
