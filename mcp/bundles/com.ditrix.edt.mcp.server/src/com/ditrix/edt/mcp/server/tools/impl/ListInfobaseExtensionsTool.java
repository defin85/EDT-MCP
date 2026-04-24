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

import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.utils.ExtensionLifecycleFailure;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.ditrix.edt.mcp.server.utils.ResolvedExtensionRuntimeContext;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Tool to list installed configuration extensions for a selected runtime target.
 */
public class ListInfobaseExtensionsTool implements IMcpTool
{
    public static final String NAME = "list_infobase_extensions"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: extension lifecycle discovery. List extensions installed in the selected target; use applicationId from get_extension_runtime_targets before apply_extension_to_infobase."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("List installed infobase extensions") //$NON-NLS-1$
                .readOnlyHint(true)
                .destructiveHint(false)
                .idempotentHint(true)
                .openWorldHint(true)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Extension project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_extension_runtime_targets (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
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

        ExtensionRuntimeContextResolver.ThickClientResolution thickClientResolution = ExtensionRuntimeContextResolver
                .resolveThickClient(NAME, context, applicationResolution.getInfobaseApplication(), applicationId);
        if (!thickClientResolution.isResolved())
        {
            return thickClientResolution.getFailureResult().toJson();
        }

        ToolResult accessSettingsFailure = ExtensionRuntimeBridgeSupport.preflightAccessSettings(NAME, context,
                applicationResolution.getInfobaseApplication(), applicationId);
        if (accessSettingsFailure != null)
        {
            return accessSettingsFailure.toJson();
        }

        IApplication application = applicationResolution.getApplication();
        IInfobaseApplication infobaseApplication = applicationResolution.getInfobaseApplication();
        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();
        arguments.setDisableStartupMessages(true);
        if (context.getRuntimeExtensionName() != null)
        {
            arguments.setExtensionName(context.getRuntimeExtensionName());
        }

        ExtensionRuntimeBridgeSupport.InvocationResult<List<String>> invocation = ExtensionRuntimeBridgeSupport
                .invokeWithGuard(NAME, context, applicationId,
                        () -> thickClientResolution.getLauncher().listConfigurationExtensions(
                                thickClientResolution.getComponent(),
                                infobaseApplication.getInfobase(), arguments));
        if (!invocation.isSuccess())
        {
            return invocation.getFailureResult().toJson();
        }

        List<String> installedExtensions = invocation.getValue();
        if (installedExtensions == null)
        {
            installedExtensions = new ArrayList<>();
        }

        boolean workspaceExtensionPresent = context.getRuntimeExtensionName() != null
                && installedExtensions.contains(context.getRuntimeExtensionName());

        return ToolResult.success()
                .put("projectName", context.getProjectContext().getProjectName()) //$NON-NLS-1$
                .put("extensionName", context.getRuntimeExtensionName()) //$NON-NLS-1$
                .put("parentProjectName", context.getParentProjectName()) //$NON-NLS-1$
                .put("applicationId", application.getId()) //$NON-NLS-1$
                .put("applicationName", application.getName()) //$NON-NLS-1$
                .put("installedExtensions", installedExtensions) //$NON-NLS-1$
                .put("installedCount", installedExtensions.size()) //$NON-NLS-1$
                .put("workspaceExtensionPresent", workspaceExtensionPresent) //$NON-NLS-1$
                .toJson();
    }
}
