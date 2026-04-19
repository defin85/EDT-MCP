package com.ditrix.edt.mcp.server.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.core.resources.IProject;

/**
 * Shared resolved project context with stable kind and capability hints.
 */
public final class ResolvedProjectContext
{
    private final IProject project;
    private final ProjectKind projectKind;
    private final boolean edtProject;
    private final EnumSet<ProjectCapability> capabilities;
    private final List<String> natureIds;
    private final String extensionName;

    public ResolvedProjectContext(IProject project, ProjectKind projectKind, boolean edtProject,
            EnumSet<ProjectCapability> capabilities, List<String> natureIds, String extensionName)
    {
        this.project = project;
        this.projectKind = projectKind;
        this.edtProject = edtProject;
        this.capabilities = capabilities != null ? capabilities.clone() : EnumSet.noneOf(ProjectCapability.class);
        this.natureIds = natureIds != null ? Collections.unmodifiableList(new ArrayList<>(natureIds))
                : Collections.emptyList();
        this.extensionName = extensionName;
    }

    public IProject getProject()
    {
        return project;
    }

    public String getProjectName()
    {
        return project != null ? project.getName() : null;
    }

    public ProjectKind getProjectKind()
    {
        return projectKind;
    }

    public boolean isEdtProject()
    {
        return edtProject;
    }

    public boolean isConfigurationProject()
    {
        return projectKind == ProjectKind.CONFIGURATION;
    }

    public boolean isExtensionProject()
    {
        return projectKind == ProjectKind.EXTENSION;
    }

    public boolean supports(ProjectCapability capability)
    {
        return capability != null && capabilities.contains(capability);
    }

    public EnumSet<ProjectCapability> getCapabilities()
    {
        return capabilities.clone();
    }

    public List<String> getCapabilityWireValues()
    {
        List<String> values = new ArrayList<>();
        for (ProjectCapability capability : capabilities)
        {
            values.add(capability.getWireValue());
        }
        return values;
    }

    public List<String> getNatureIds()
    {
        return natureIds;
    }

    public String getExtensionName()
    {
        return extensionName;
    }
}
