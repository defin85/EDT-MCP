/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.utils.MetadataPathResolver;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Guarded read-only probe for form command availability.
 */
public class ProbeFormCommandAvailabilityTool implements IMcpTool
{
    public static final String NAME = "probe_form_command_availability"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Read-only live-evidence probe for form command availability. Returns explicit " //$NON-NLS-1$
                + "unsupported/unknown outcomes when safe runtime command state is not exposed."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("formPath", //$NON-NLS-1$
                        "Metadata form path, e.g. 'Catalog.Products.Forms.ItemForm' or 'CommonForm.MyForm'", true) //$NON-NLS-1$
                .stringProperty("commandName", "Form command name to inspect (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Bounded probe timeout request (default 10, max 60)") //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("Probe form command availability"); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        String formPath = JsonUtils.extractStringArgument(params, "formPath"); //$NON-NLS-1$
        String commandName = JsonUtils.extractStringArgument(params, "commandName"); //$NON-NLS-1$
        int timeoutSeconds = clamp(JsonUtils.extractIntArgument(params, "timeoutSeconds", 10), 1, 60); //$NON-NLS-1$

        if (!hasText(projectName))
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(applicationId))
        {
            return ToolResult.error("applicationId is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(formPath))
        {
            return ToolResult.error("formPath is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(commandName))
        {
            return ToolResult.error("commandName is required").toJson(); //$NON-NLS-1$
        }

        String formFilePath = MetadataPathResolver.resolveFormFilePath(formPath);
        if (!hasText(formFilePath))
        {
            return ToolResult.error("Unsupported formPath: " + formPath).toJson(); //$NON-NLS-1$
        }

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists())
        {
            return ToolResult.error("Project not found: " + projectName).toJson(); //$NON-NLS-1$
        }
        if (!project.isOpen())
        {
            return ToolResult.error("Project is closed: " + projectName).toJson(); //$NON-NLS-1$
        }

        boolean metadataCommandFound = false;
        try
        {
            IFile formFile = project.getFile(new Path(formFilePath));
            if (formFile != null && formFile.exists())
            {
                metadataCommandFound = containsCommandName(BslModuleUtils.readFileLines(formFile), commandName);
            }
        }
        catch (Exception e)
        {
            Activator.logWarning("Unable to inspect form command metadata: " + e.getMessage()); //$NON-NLS-1$
        }

        return buildUnsupportedResult(projectName, applicationId, formPath, formFilePath, commandName,
                timeoutSeconds, metadataCommandFound).toString();
    }

    static JsonObject buildUnsupportedResult(String projectName, String applicationId, String formPath,
            String formFilePath, String commandName, int timeoutSeconds, boolean metadataCommandFound)
    {
        JsonObject result = new JsonObject();
        result.addProperty("success", true); //$NON-NLS-1$
        result.addProperty("status", "unsupported"); //$NON-NLS-1$ //$NON-NLS-2$
        result.addProperty("readOnly", true); //$NON-NLS-1$
        result.addProperty("timeoutSeconds", timeoutSeconds); //$NON-NLS-1$
        result.addProperty("timeout", false); //$NON-NLS-1$

        JsonObject application = new JsonObject();
        application.addProperty("projectName", projectName); //$NON-NLS-1$
        application.addProperty("applicationId", applicationId); //$NON-NLS-1$
        result.add("application", application); //$NON-NLS-1$

        JsonObject target = new JsonObject();
        target.addProperty("type", "form_command"); //$NON-NLS-1$ //$NON-NLS-2$
        target.addProperty("formPath", formPath); //$NON-NLS-1$
        target.addProperty("formFilePath", formFilePath); //$NON-NLS-1$
        target.addProperty("commandName", commandName); //$NON-NLS-1$
        result.add("target", target); //$NON-NLS-1$

        JsonObject evidence = new JsonObject();
        evidence.addProperty("metadataCommandFound", metadataCommandFound); //$NON-NLS-1$
        evidence.addProperty("runtimeAvailability", "unknown"); //$NON-NLS-1$ //$NON-NLS-2$
        result.add("evidence", evidence); //$NON-NLS-1$

        JsonArray limitations = new JsonArray();
        JsonObject limitation = new JsonObject();
        limitation.addProperty("id", "runtime_form_command_api_unavailable"); //$NON-NLS-1$ //$NON-NLS-2$
        limitation.addProperty("severity", "blocker"); //$NON-NLS-1$ //$NON-NLS-2$
        limitation.addProperty("message", //$NON-NLS-1$
                "No headless-safe EDT/runtime API is proven for live form command availability in this rollout."); //$NON-NLS-1$
        limitation.addProperty("recommendation", //$NON-NLS-1$
                "Use this result as fail-closed evidence and verify command availability manually or through a later proven runtime probe."); //$NON-NLS-1$
        limitations.add(limitation);
        result.add("limitations", limitations); //$NON-NLS-1$
        return result;
    }

    private static boolean containsCommandName(List<String> lines, String commandName)
    {
        if (lines == null || !hasText(commandName))
        {
            return false;
        }
        String needle = commandName.toLowerCase(Locale.ROOT);
        for (String line : lines)
        {
            if (line != null && line.toLowerCase(Locale.ROOT).contains(needle))
            {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
