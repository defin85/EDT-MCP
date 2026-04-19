/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.MarkdownUtils;
import com.ditrix.edt.mcp.server.utils.ProjectContextResolver;
import com.ditrix.edt.mcp.server.utils.ProjectKind;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker.ProjectStateResult;
import com.ditrix.edt.mcp.server.utils.ResolvedProjectContext;

/**
 * Tool to list all workspace projects.
 */
public class ListProjectsTool implements IMcpTool
{
    public static final String NAME = "list_projects"; //$NON-NLS-1$
    private static final ThreadLocal<Map<String, Object>> LAST_STRUCTURED_CONTENT = new ThreadLocal<>();
    
    @Override
    public String getName()
    {
        return NAME;
    }
    
    @Override
    public String getDescription()
    {
        return "List all workspace projects with properties, project kind, capability hints, and natures"; //$NON-NLS-1$
    }
    
    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object().build();
    }
    
    @Override
    public String execute(Map<String, String> params)
    {
        ProjectListing listing = collectProjects();
        LAST_STRUCTURED_CONTENT.set(buildStructuredContent(listing));
        return renderMarkdown(listing);
    }

    @Override
    public Object getStructuredContent(Map<String, String> params, String result)
    {
        try
        {
            return LAST_STRUCTURED_CONTENT.get();
        }
        finally
        {
            LAST_STRUCTURED_CONTENT.remove();
        }
    }
    
    /**
     * Returns list of workspace projects with their properties.
     * 
     * @return Markdown string with project list
     */
    public static String listProjects()
    {
        return renderMarkdown(collectProjects());
    }

    private static ProjectListing collectProjects()
    {
        try
        {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IProject[] projects = workspace.getRoot().getProjects();

            List<ProjectRow> rows = new ArrayList<>();
            for (IProject project : projects)
            {
                ProjectStateResult stateResult = ProjectStateChecker.checkProjectState(project);
                ResolvedProjectContext context = ProjectContextResolver.resolve(project);
                String path = project.getLocation() != null ? project.getLocation().toOSString() : ""; //$NON-NLS-1$
                rows.add(new ProjectRow(project.getName(), stateResult.getStateValue(), path, project.isOpen(),
                        context));
            }
            return new ProjectListing(projects.length, rows);
        }
        catch (Exception e)
        {
            Activator.logError("Failed to list projects", e); //$NON-NLS-1$
            return ProjectListing.error(e.getMessage());
        }
    }

    private static String renderMarkdown(ProjectListing listing)
    {
        StringBuilder md = new StringBuilder();
        md.append("## Workspace Projects\n\n"); //$NON-NLS-1$
        md.append("**Total:** ").append(listing.total).append(" projects\n\n"); //$NON-NLS-1$ //$NON-NLS-2$

        if (listing.errorMessage != null)
        {
            md.append("**Error:** ").append(listing.errorMessage).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
            return md.toString();
        }

        if (listing.rows.isEmpty())
        {
            md.append("*No projects found.*\n"); //$NON-NLS-1$
            return md.toString();
        }

        md.append("| Name | State | Path | Open | EDT Project | Project Kind | Capabilities | Extension | Natures |\n"); //$NON-NLS-1$
        md.append("|------|-------|------|------|-------------|--------------|--------------|-----------|---------|\n"); //$NON-NLS-1$

        for (ProjectRow row : listing.rows)
        {
            md.append("| ").append(MarkdownUtils.escapeForTable(row.name)); //$NON-NLS-1$
            md.append(" | ").append(row.state); //$NON-NLS-1$
            md.append(" | ").append(MarkdownUtils.escapeForTable(row.path)); //$NON-NLS-1$
            md.append(" | ").append(row.open ? "Yes" : "No"); //$NON-NLS-1$ //$NON-NLS-2$
            md.append(" | ").append(row.edtProject ? "Yes" : "No"); //$NON-NLS-1$ //$NON-NLS-2$
            md.append(" | ").append(MarkdownUtils.escapeForTable(row.projectKind)); //$NON-NLS-1$
            md.append(" | ").append(MarkdownUtils.escapeForTable(row.capabilitySummary)); //$NON-NLS-1$
            md.append(" | ").append(MarkdownUtils.escapeForTable(row.extensionSummary)); //$NON-NLS-1$
            md.append(" | ").append(MarkdownUtils.escapeForTable(row.naturesSummary)).append(" |\n"); //$NON-NLS-1$
        }
        return md.toString();
    }

    private static Map<String, Object> buildStructuredContent(ProjectListing listing)
    {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("success", listing.errorMessage == null); //$NON-NLS-1$
        structured.put("projectCount", Integer.valueOf(listing.total)); //$NON-NLS-1$
        if (listing.errorMessage != null)
        {
            structured.put("error", Map.of("message", listing.errorMessage)); //$NON-NLS-1$ //$NON-NLS-2$
            return structured;
        }

        List<Map<String, Object>> projects = new ArrayList<>();
        for (ProjectRow row : listing.rows)
        {
            Map<String, Object> capabilities = new LinkedHashMap<>();
            capabilities.put("metadataRead", Boolean.valueOf(row.context.supports(com.ditrix.edt.mcp.server.utils.ProjectCapability.METADATA_READ))); //$NON-NLS-1$
            capabilities.put("moduleRead", Boolean.valueOf(row.context.supports(com.ditrix.edt.mcp.server.utils.ProjectCapability.MODULE_READ))); //$NON-NLS-1$
            capabilities.put("mutationRefactor", Boolean.valueOf(row.context.supports(com.ditrix.edt.mcp.server.utils.ProjectCapability.MUTATION_REFACTOR))); //$NON-NLS-1$
            capabilities.put("runtimeApplication", Boolean.valueOf(row.context.supports(com.ditrix.edt.mcp.server.utils.ProjectCapability.RUNTIME_APPLICATION))); //$NON-NLS-1$

            Map<String, Object> project = new LinkedHashMap<>();
            project.put("name", row.name); //$NON-NLS-1$
            project.put("state", row.state); //$NON-NLS-1$
            project.put("path", row.path); //$NON-NLS-1$
            project.put("open", Boolean.valueOf(row.open)); //$NON-NLS-1$
            project.put("edtProject", Boolean.valueOf(row.edtProject)); //$NON-NLS-1$
            project.put("projectKind", row.projectKind); //$NON-NLS-1$
            project.put("capabilityCategories", row.context.getCapabilityWireValues()); //$NON-NLS-1$
            project.put("capabilities", capabilities); //$NON-NLS-1$
            project.put("natures", row.context.getNatureIds()); //$NON-NLS-1$
            if (row.context.getProjectKind() == ProjectKind.EXTENSION)
            {
                project.put("extension", Map.of("name", row.context.getExtensionName())); //$NON-NLS-1$ //$NON-NLS-2$
            }
            projects.add(project);
        }
        structured.put("projects", projects); //$NON-NLS-1$
        return structured;
    }

    private static final class ProjectListing
    {
        private final int total;
        private final List<ProjectRow> rows;
        private final String errorMessage;

        private ProjectListing(int total, List<ProjectRow> rows)
        {
            this.total = total;
            this.rows = rows;
            this.errorMessage = null;
        }

        private ProjectListing(int total, List<ProjectRow> rows, String errorMessage)
        {
            this.total = total;
            this.rows = rows;
            this.errorMessage = errorMessage;
        }

        private static ProjectListing error(String errorMessage)
        {
            return new ProjectListing(0, new ArrayList<>(), errorMessage);
        }
    }

    private static final class ProjectRow
    {
        private final String name;
        private final String state;
        private final String path;
        private final boolean open;
        private final boolean edtProject;
        private final String projectKind;
        private final String capabilitySummary;
        private final String extensionSummary;
        private final String naturesSummary;
        private final ResolvedProjectContext context;

        private ProjectRow(String name, String state, String path, boolean open, ResolvedProjectContext context)
        {
            this.name = name;
            this.state = state;
            this.path = path;
            this.open = open;
            this.context = context != null ? context : new ResolvedProjectContext(null, ProjectKind.UNKNOWN, false,
                    java.util.EnumSet.noneOf(com.ditrix.edt.mcp.server.utils.ProjectCapability.class),
                    java.util.Collections.emptyList(), null);
            this.edtProject = this.context.isEdtProject();
            this.projectKind = this.context.getProjectKind().getWireValue();
            this.capabilitySummary = joinOrDash(this.context.getCapabilityWireValues());
            this.extensionSummary = this.context.getExtensionName() != null ? this.context.getExtensionName() : "-"; //$NON-NLS-1$
            this.naturesSummary = abbreviateNatures(this.context.getNatureIds());
        }

        private static String abbreviateNatures(List<String> natureIds)
        {
            if (natureIds == null || natureIds.isEmpty())
            {
                return "-"; //$NON-NLS-1$
            }
            StringBuilder builder = new StringBuilder();
            int count = Math.min(natureIds.size(), 3);
            for (int i = 0; i < count; i++)
            {
                if (i > 0)
                {
                    builder.append(", "); //$NON-NLS-1$
                }
                String nature = natureIds.get(i);
                int lastDot = nature.lastIndexOf('.');
                builder.append(lastDot > 0 ? nature.substring(lastDot + 1) : nature);
            }
            if (natureIds.size() > 3)
            {
                builder.append("...+").append(natureIds.size() - 3); //$NON-NLS-1$
            }
            return builder.toString();
        }

        private static String joinOrDash(List<String> values)
        {
            if (values == null || values.isEmpty())
            {
                return "-"; //$NON-NLS-1$
            }
            return String.join(", ", values); //$NON-NLS-1$
        }
    }
}
