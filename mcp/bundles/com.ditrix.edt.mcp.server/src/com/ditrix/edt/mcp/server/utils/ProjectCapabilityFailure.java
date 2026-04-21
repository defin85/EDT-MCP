package com.ditrix.edt.mcp.server.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.ToolResult;

/**
 * Stable machine-readable project-kind and capability failure surface.
 */
public final class ProjectCapabilityFailure
{
    public static final String CATEGORY_CONFIGURATION_ONLY = "configuration_only"; //$NON-NLS-1$
    public static final String CATEGORY_UNSUPPORTED_EXTENSION_OPERATION = "unsupported_extension_operation"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_MODEL_UNAVAILABLE = "extension_model_unavailable"; //$NON-NLS-1$

    private final String category;
    private final String message;
    private final String projectName;
    private final String projectKind;
    private final String toolName;
    private final String requiredCapability;

    private ProjectCapabilityFailure(String category, String message, ResolvedProjectContext context, String toolName,
            ProjectCapability requiredCapability)
    {
        this.category = category;
        this.message = message;
        this.projectName = context != null ? context.getProjectName() : null;
        this.projectKind = context != null ? context.getProjectKind().getWireValue() : null;
        this.toolName = toolName;
        this.requiredCapability = requiredCapability != null ? requiredCapability.getWireValue() : null;
    }

    public static ProjectCapabilityFailure configurationOnly(String toolName, ResolvedProjectContext context,
            String detail)
    {
        StringBuilder message = new StringBuilder();
        message.append("Tool '").append(toolName).append("' is configuration-only in this rollout."); //$NON-NLS-1$ //$NON-NLS-2$
        if (context != null && context.getProjectName() != null)
        {
            message.append(" Project '").append(context.getProjectName()).append("' is an extension project."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (detail != null && !detail.isBlank())
        {
            message.append(" ").append(detail); //$NON-NLS-1$
        }
        return new ProjectCapabilityFailure(CATEGORY_CONFIGURATION_ONLY, message.toString(), context, toolName,
                ProjectCapability.RUNTIME_APPLICATION);
    }

    public static ProjectCapabilityFailure unsupportedExtensionOperation(String toolName, ResolvedProjectContext context,
            ProjectCapability requiredCapability, String detail)
    {
        StringBuilder message = new StringBuilder();
        message.append("Tool '").append(toolName).append("' is not verified for extension projects in this rollout."); //$NON-NLS-1$ //$NON-NLS-2$
        if (context != null && context.getProjectName() != null)
        {
            message.append(" Project '").append(context.getProjectName()).append("' is an extension project."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (detail != null && !detail.isBlank())
        {
            message.append(" ").append(detail); //$NON-NLS-1$
        }
        return new ProjectCapabilityFailure(CATEGORY_UNSUPPORTED_EXTENSION_OPERATION, message.toString(), context,
                toolName, requiredCapability);
    }

    public static ProjectCapabilityFailure extensionModelUnavailable(String toolName, ResolvedProjectContext context,
            String detail)
    {
        StringBuilder message = new StringBuilder();
        message.append("Tool '").append(toolName).append("' could not access a compatible extension model."); //$NON-NLS-1$ //$NON-NLS-2$
        if (context != null && context.getProjectName() != null)
        {
            message.append(" Project '").append(context.getProjectName()).append("' is an extension project."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (detail != null && !detail.isBlank())
        {
            message.append(" ").append(detail); //$NON-NLS-1$
        }
        return new ProjectCapabilityFailure(CATEGORY_EXTENSION_MODEL_UNAVAILABLE, message.toString(), context, toolName,
                ProjectCapability.METADATA_READ);
    }

    public static ValidationResult requireConfigurationProject(String projectName, String toolName, String detail)
    {
        return requireConfigurationProject(ProjectContextResolver.resolve(projectName), toolName, detail);
    }

    public static ValidationResult requireConfigurationProject(ResolvedProjectContext context, String toolName,
            String detail)
    {
        if (context != null && context.isExtensionProject())
        {
            return new ValidationResult(context, configurationOnly(toolName, context, detail));
        }
        return new ValidationResult(context, null);
    }

    public static ValidationResult requireVerifiedExtensionSupport(String projectName, String toolName,
            ProjectCapability requiredCapability, String detail)
    {
        return requireVerifiedExtensionSupport(ProjectContextResolver.resolve(projectName), toolName,
                requiredCapability, detail);
    }

    public static ValidationResult requireVerifiedExtensionSupport(ResolvedProjectContext context, String toolName,
            ProjectCapability requiredCapability, String detail)
    {
        if (context != null && context.isExtensionProject())
        {
            return new ValidationResult(context,
                    unsupportedExtensionOperation(toolName, context, requiredCapability, detail));
        }
        return new ValidationResult(context, null);
    }

    public String toJson()
    {
        ToolResult result = ToolResult.error(message)
                .put("category", category); //$NON-NLS-1$
        if (projectName != null)
        {
            result.put("projectName", projectName); //$NON-NLS-1$
        }
        if (projectKind != null)
        {
            result.put("projectKind", projectKind); //$NON-NLS-1$
        }
        if (toolName != null)
        {
            result.put("tool", toolName); //$NON-NLS-1$
        }
        if (requiredCapability != null)
        {
            result.put("requiredCapability", requiredCapability); //$NON-NLS-1$
        }
        return result.toJson();
    }

    public String toMarkdown()
    {
        return "**Error:** " + message + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    public Map<String, Object> toStructuredContent()
    {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message); //$NON-NLS-1$
        error.put("category", category); //$NON-NLS-1$

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("success", Boolean.FALSE); //$NON-NLS-1$
        structured.put("error", error); //$NON-NLS-1$
        if (projectName != null)
        {
            structured.put("projectName", projectName); //$NON-NLS-1$
        }
        if (projectKind != null)
        {
            structured.put("projectKind", projectKind); //$NON-NLS-1$
        }
        if (toolName != null)
        {
            structured.put("tool", toolName); //$NON-NLS-1$
        }
        if (requiredCapability != null)
        {
            structured.put("requiredCapability", requiredCapability); //$NON-NLS-1$
        }
        return structured;
    }

    public static final class ValidationResult
    {
        private final ResolvedProjectContext context;
        private final ProjectCapabilityFailure failure;

        private ValidationResult(ResolvedProjectContext context, ProjectCapabilityFailure failure)
        {
            this.context = context;
            this.failure = failure;
        }

        public ResolvedProjectContext getContext()
        {
            return context;
        }

        public ProjectCapabilityFailure getFailure()
        {
            return failure;
        }

        public boolean hasFailure()
        {
            return failure != null;
        }
    }
}
