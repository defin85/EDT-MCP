package com.ditrix.edt.mcp.server.utils;

import org.eclipse.core.resources.IProject;

/**
 * Shared resolved runtime context for configuration-project unit-test execution.
 */
public final class ResolvedConfigurationRuntimeContext
{
    private final ResolvedProjectContext projectContext;
    private final IProject project;

    public ResolvedConfigurationRuntimeContext(ResolvedProjectContext projectContext, IProject project)
    {
        this.projectContext = projectContext;
        this.project = project;
    }

    public ResolvedProjectContext getProjectContext()
    {
        return projectContext;
    }

    public IProject getProject()
    {
        return project;
    }

    public String getProjectName()
    {
        return projectContext != null ? projectContext.getProjectName() : null;
    }
}
