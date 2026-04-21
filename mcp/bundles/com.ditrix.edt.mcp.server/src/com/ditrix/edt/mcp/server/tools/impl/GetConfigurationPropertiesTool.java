/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.widgets.Display;

import com._1c.g5.v8.dt.core.platform.IConfigurationProject;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.ConfigurationPropertiesSupport;
import com.ditrix.edt.mcp.server.utils.ProjectCapabilityFailure;

/**
 * Tool to get 1C:Enterprise configuration properties.
 */
public class GetConfigurationPropertiesTool implements IMcpTool
{
    public static final String NAME = "get_configuration_properties"; //$NON-NLS-1$
    
    @Override
    public String getName()
    {
        return NAME;
    }
    
    @Override
    public String getDescription()
    {
        return "Get 1C:Enterprise configuration properties (name, synonym, comment, script variant, compatibility mode, etc.)"; //$NON-NLS-1$
    }
    
    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty("projectName", "Project name (optional, if not specified returns first configuration project)") //$NON-NLS-1$ //$NON-NLS-2$
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
        String projectName = params != null ? params.get("projectName") : null; //$NON-NLS-1$
        return getConfigurationProperties(projectName);
    }
    
    /**
     * Returns configuration properties for the specified project.
     * This method executes in the UI thread to ensure proper access to EDT services.
     * 
     * @param projectName the name of the project (optional)
     * @return JSON string with configuration properties
     */
    public static String getConfigurationProperties(String projectName)
    {
        Activator.logInfo("getConfigurationProperties: Starting..."); //$NON-NLS-1$

        if (projectName != null && !projectName.isEmpty())
        {
            ProjectCapabilityFailure.ValidationResult validation = ProjectCapabilityFailure
                    .requireConfigurationProject(projectName, NAME,
                            "Extension-specific property semantics are not part of the configuration-properties contract yet."); //$NON-NLS-1$
            if (validation.hasFailure())
            {
                return validation.getFailure().toJson();
            }
        }
        
        // Execute in UI thread to avoid blocking
        final String[] result = new String[1];
        Display display = Display.getDefault();
        
        if (display.getThread() == Thread.currentThread())
        {
            // Already in UI thread
            result[0] = getConfigurationPropertiesInternal(projectName);
        }
        else
        {
            // Execute in UI thread
            Activator.logInfo("getConfigurationProperties: Switching to UI thread..."); //$NON-NLS-1$
            display.syncExec(() -> {
                result[0] = getConfigurationPropertiesInternal(projectName);
            });
        }
        
        return result[0];
    }
    
    /**
     * Internal implementation of getConfigurationProperties.
     * Must be called from the UI thread.
     */
    private static String getConfigurationPropertiesInternal(String projectName)
    {
        Activator.logInfo("getConfigurationPropertiesInternal: Starting..."); //$NON-NLS-1$
        
        try
        {
            IDtProjectManager dtProjectManager = Activator.getDefault().getDtProjectManager();
            IV8ProjectManager v8ProjectManager = Activator.getDefault().getV8ProjectManager();
            
            if (dtProjectManager == null || v8ProjectManager == null)
            {
                Activator.logInfo("getConfigurationProperties: Project managers not available"); //$NON-NLS-1$
                return ToolResult.error("Project manager not available").toJson(); //$NON-NLS-1$
            }

            IConfigurationProject configProject = null;
            
            // Find project by name or get first configuration project
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IProject[] projects = workspace.getRoot().getProjects();
            
            for (IProject project : projects)
            {
                if (!project.isOpen())
                {
                    continue;
                }
                
                IDtProject dtProject = dtProjectManager.getDtProject(project);
                if (dtProject == null)
                {
                    continue;
                }
                
                IV8Project v8Project = v8ProjectManager.getProject(dtProject);
                if (v8Project instanceof IConfigurationProject)
                {
                    if (projectName == null || projectName.isEmpty() || 
                        project.getName().equals(projectName))
                    {
                        configProject = (IConfigurationProject) v8Project;
                        break;
                    }
                }
            }
            
            if (configProject == null)
            {
                String errorMsg = "No configuration project found"; //$NON-NLS-1$
                if (projectName != null && !projectName.isEmpty())
                {
                    errorMsg += " with name: " + projectName; //$NON-NLS-1$
                }
                return ToolResult.error(errorMsg).toJson();
            }

            // Get configuration object
            Configuration configuration = configProject.getConfiguration();
            if (configuration == null)
            {
                return ToolResult.error("Configuration object not available").toJson(); //$NON-NLS-1$
            }

            ToolResult result = ConfigurationPropertiesSupport
                    .putCommonConfigurationProperties(ToolResult.success(), configuration)
                    .put("projectName", configProject.getProject().getName()); //$NON-NLS-1$
            
            return result.toJson();
        }
        catch (Exception e)
        {
            Activator.logError("Failed to get configuration properties", e); //$NON-NLS-1$
            return ToolResult.error(e.getMessage()).toJson();
        }
    }
}
