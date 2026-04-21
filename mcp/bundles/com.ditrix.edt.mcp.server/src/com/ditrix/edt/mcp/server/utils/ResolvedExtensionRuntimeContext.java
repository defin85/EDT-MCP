package com.ditrix.edt.mcp.server.utils;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.core.platform.IConfigurationProject;
import com._1c.g5.v8.dt.core.platform.IExtensionProject;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;

/**
 * Shared resolved context for extension lifecycle tools.
 */
public final class ResolvedExtensionRuntimeContext
{
    private final ResolvedProjectContext projectContext;
    private final IExtensionProject extensionProject;
    private final Configuration configuration;
    private final String runtimeExtensionName;
    private final IConfigurationProject parentConfigurationProject;
    private final IProject parentProject;

    public ResolvedExtensionRuntimeContext(ResolvedProjectContext projectContext, IExtensionProject extensionProject,
            Configuration configuration, String runtimeExtensionName,
            IConfigurationProject parentConfigurationProject, IProject parentProject)
    {
        this.projectContext = projectContext;
        this.extensionProject = extensionProject;
        this.configuration = configuration;
        this.runtimeExtensionName = runtimeExtensionName;
        this.parentConfigurationProject = parentConfigurationProject;
        this.parentProject = parentProject;
    }

    public ResolvedProjectContext getProjectContext()
    {
        return projectContext;
    }

    public IExtensionProject getExtensionProject()
    {
        return extensionProject;
    }

    public Configuration getConfiguration()
    {
        return configuration;
    }

    public String getRuntimeExtensionName()
    {
        return runtimeExtensionName;
    }

    public IConfigurationProject getParentConfigurationProject()
    {
        return parentConfigurationProject;
    }

    public IProject getParentProject()
    {
        return parentProject;
    }

    public String getParentProjectName()
    {
        return parentProject != null ? parentProject.getName() : null;
    }
}
