package com.ditrix.edt.mcp.server.utils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Resolves shared project-kind and capability hints for EDT workspace projects.
 */
public final class ProjectContextResolver
{
    public static final String CONFIGURATION_NATURE = "com._1c.g5.v8.dt.core.V8ConfigurationNature"; //$NON-NLS-1$
    public static final String EXTENSION_NATURE = "com._1c.g5.v8.dt.core.V8ExtensionNature"; //$NON-NLS-1$

    private ProjectContextResolver()
    {
    }

    public static IProject findProject(String projectName)
    {
        if (projectName == null || projectName.isEmpty())
        {
            return null;
        }
        return ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
    }

    public static ResolvedProjectContext resolve(String projectName)
    {
        return resolve(findProject(projectName));
    }

    public static ResolvedProjectContext resolve(IProject project)
    {
        if (project == null || !project.exists())
        {
            return null;
        }

        boolean edtProject = false;
        boolean configurationProject = false;
        boolean extensionProject = false;
        List<String> natureIds = new ArrayList<>();

        if (project.isOpen())
        {
            try
            {
                String[] rawNatureIds = project.getDescription().getNatureIds();
                for (String natureId : rawNatureIds)
                {
                    natureIds.add(natureId);
                }
                configurationProject = project.hasNature(CONFIGURATION_NATURE);
                extensionProject = project.hasNature(EXTENSION_NATURE);
                edtProject = configurationProject || extensionProject;
            }
            catch (Exception e)
            {
                // Keep fail-closed defaults when nature lookup fails.
            }
        }

        ProjectKind projectKind = ProjectKind.UNKNOWN;
        if (configurationProject)
        {
            projectKind = ProjectKind.CONFIGURATION;
        }
        else if (extensionProject)
        {
            projectKind = ProjectKind.EXTENSION;
        }

        EnumSet<ProjectCapability> capabilities = EnumSet.noneOf(ProjectCapability.class);
        if (projectKind == ProjectKind.CONFIGURATION)
        {
            capabilities.add(ProjectCapability.METADATA_READ);
            capabilities.add(ProjectCapability.MODULE_READ);
            capabilities.add(ProjectCapability.MUTATION_REFACTOR);
            capabilities.add(ProjectCapability.RUNTIME_APPLICATION);
        }
        else if (projectKind == ProjectKind.EXTENSION)
        {
            capabilities.add(ProjectCapability.METADATA_READ);
            capabilities.add(ProjectCapability.MODULE_READ);
        }

        String extensionName = projectKind == ProjectKind.EXTENSION ? project.getName() : null;
        return new ResolvedProjectContext(project, projectKind, edtProject, capabilities, natureIds, extensionName);
    }
}
