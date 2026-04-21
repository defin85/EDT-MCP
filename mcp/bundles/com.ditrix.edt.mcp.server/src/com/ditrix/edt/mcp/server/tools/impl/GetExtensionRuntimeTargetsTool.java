/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.ExtensionLifecycleFailure;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.InfobaseSyncUtils;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.ditrix.edt.mcp.server.utils.ResolvedExtensionRuntimeContext;
import com.e1c.g5.dt.applications.ApplicationException;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tool to resolve runtime targets for an extension project through its parent configuration project.
 */
public class GetExtensionRuntimeTargetsTool implements IMcpTool
{
    public static final String NAME = "get_extension_runtime_targets"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Resolve parent configuration project and available infobase applications for an extension project."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Extension project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
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
        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
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

        IApplicationManager applicationManager = Activator.getDefault() != null ? Activator.getDefault().getApplicationManager()
                : null;
        if (applicationManager == null)
        {
            return ExtensionLifecycleFailure.runtimeServiceUnavailable(NAME, context.getProjectContext(),
                    context.getParentProjectName(), "IApplicationManager service is not available.").toJson(); //$NON-NLS-1$
        }

        try
        {
            List<IApplication> applications = applicationManager.getApplications(parentProject);
            JsonArray appsArray = new JsonArray();
            IInfobaseSynchronizationManager synchronizationManager = Activator.getDefault()
                    .getInfobaseSynchronizationManager();
            if (applications != null)
            {
                for (IApplication application : applications)
                {
                    JsonObject appObj = new JsonObject();
                    appObj.addProperty("id", application.getId()); //$NON-NLS-1$
                    appObj.addProperty("name", application.getName()); //$NON-NLS-1$
                    if (application.getType() != null)
                    {
                        appObj.addProperty("type", application.getType().getId()); //$NON-NLS-1$
                    }

                    IInfobaseApplication infobaseApplication = InfobaseSyncUtils.asInfobaseApplication(application);
                    appObj.addProperty("isInfobase", infobaseApplication != null); //$NON-NLS-1$
                    if (synchronizationManager != null && infobaseApplication != null)
                    {
                        InfobaseSynchronizationState synchronizationState = synchronizationManager
                                .getSynchronizationState(parentProject, infobaseApplication.getInfobase());
                        InfobaseEqualityState equalityState = synchronizationManager
                                .getEqualityState(parentProject, infobaseApplication.getInfobase());
                        String updateState = InfobaseSyncUtils.deriveUpdateState(synchronizationState, equalityState);
                        appObj.addProperty("syncState", synchronizationState.name()); //$NON-NLS-1$
                        appObj.addProperty("equalityState", equalityState.name()); //$NON-NLS-1$
                        appObj.addProperty("updateState", updateState); //$NON-NLS-1$
                        appObj.addProperty("updateStateDescription",
                                InfobaseSyncUtils.describeUpdateState(updateState)); //$NON-NLS-1$
                    }
                    appsArray.add(appObj);
                }
            }

            ToolResult result = ToolResult.success()
                    .put("projectName", context.getProjectContext().getProjectName()) //$NON-NLS-1$
                    .put("projectKind", context.getProjectContext().getProjectKind().getWireValue()) //$NON-NLS-1$
                    .put("extensionName", context.getRuntimeExtensionName()) //$NON-NLS-1$
                    .put("parentProjectName", context.getParentProjectName()) //$NON-NLS-1$
                    .put("applications", appsArray) //$NON-NLS-1$
                    .put("count", applications != null ? applications.size() : 0); //$NON-NLS-1$

            IApplication defaultApplication = applicationManager.getDefaultApplication(parentProject).orElse(null);
            if (defaultApplication != null)
            {
                result.put("defaultApplicationId", defaultApplication.getId()); //$NON-NLS-1$
            }

            if (applications == null || applications.isEmpty())
            {
                result.put("message", "No applications found for the parent configuration project"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return result.toJson();
        }
        catch (ApplicationException e)
        {
            Activator.logError("Failed to resolve extension runtime targets for project: " + projectName, e); //$NON-NLS-1$
            return ExtensionLifecycleFailure.runtimeCheckFailed(NAME, context.getProjectContext(),
                    context.getParentProjectName(), null, e.getMessage()).toJson();
        }
    }
}
