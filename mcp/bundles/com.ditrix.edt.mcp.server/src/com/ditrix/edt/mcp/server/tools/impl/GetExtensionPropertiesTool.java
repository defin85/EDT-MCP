/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import org.eclipse.swt.widgets.Display;

import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.ConfigurationPropertiesSupport;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.ditrix.edt.mcp.server.utils.ResolvedExtensionRuntimeContext;

/**
 * Tool to read extension-project root configuration properties.
 */
public class GetExtensionPropertiesTool implements IMcpTool
{
    public static final String NAME = "get_extension_properties"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Get extension project properties using the extension-aware EDT project model."; //$NON-NLS-1$
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

        final String[] result = new String[1];
        Display display = Display.getDefault();
        if (display.getThread() == Thread.currentThread())
        {
            result[0] = getExtensionPropertiesInternal(projectName);
        }
        else
        {
            display.syncExec(() -> result[0] = getExtensionPropertiesInternal(projectName));
        }
        return result[0];
    }

    private String getExtensionPropertiesInternal(String projectName)
    {
        ExtensionRuntimeContextResolver.Resolution resolution = ExtensionRuntimeContextResolver.resolve(NAME, projectName);
        if (!resolution.isResolved())
        {
            return resolution.getFailureResult().toJson();
        }

        ResolvedExtensionRuntimeContext context = resolution.getContext();
        Configuration configuration = context.getConfiguration();
        ToolResult result = ConfigurationPropertiesSupport.putCommonConfigurationProperties(ToolResult.success(),
                configuration)
                .put("projectName", context.getProjectContext().getProjectName()) //$NON-NLS-1$
                .put("projectKind", context.getProjectContext().getProjectKind().getWireValue()) //$NON-NLS-1$
                .put("hasParentProject", context.getParentProject() != null); //$NON-NLS-1$

        if (context.getRuntimeExtensionName() != null)
        {
            result.put("extensionName", context.getRuntimeExtensionName()); //$NON-NLS-1$
        }
        if (context.getParentProjectName() != null)
        {
            result.put("parentProjectName", context.getParentProjectName()); //$NON-NLS-1$
        }
        if (configuration.getNamePrefix() != null)
        {
            result.put("namePrefix", configuration.getNamePrefix()); //$NON-NLS-1$
        }
        if (configuration.getConfigurationExtensionCompatibilityMode() != null)
        {
            result.put("configurationExtensionCompatibilityMode",
                    configuration.getConfigurationExtensionCompatibilityMode().toString()); //$NON-NLS-1$
        }
        if (configuration.getConfigurationExtensionPurpose() != null)
        {
            result.put("configurationExtensionPurpose",
                    configuration.getConfigurationExtensionPurpose().toString()); //$NON-NLS-1$
        }
        result.put("keepMappingToExtendedConfigurationObjectsByIDs",
                configuration.isKeepMappingToExtendedConfigurationObjectsByIDs()); //$NON-NLS-1$

        return result.toJson();
    }
}
