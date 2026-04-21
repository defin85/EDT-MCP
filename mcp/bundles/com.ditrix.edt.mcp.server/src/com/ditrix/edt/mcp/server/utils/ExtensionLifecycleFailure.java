package com.ditrix.edt.mcp.server.utils;

import com.ditrix.edt.mcp.server.protocol.ToolResult;

/**
 * Stable machine-readable failure surface for extension lifecycle tools.
 */
public final class ExtensionLifecycleFailure
{
    public static final String CATEGORY_EXTENSION_PROJECT_REQUIRED = "extension_project_required"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_PARENT_MISSING = "extension_parent_missing"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_TARGET_NOT_FOUND = "extension_target_not_found"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_RUNTIME_SERVICE_UNAVAILABLE = "extension_runtime_service_unavailable"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_RUNTIME_CHECK_FAILED = "extension_runtime_check_failed"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_RUNTIME_ACCESS_SETTINGS_REQUIRED = "extension_runtime_access_settings_required"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_RUNTIME_BRIDGE_BUSY = "extension_runtime_bridge_busy"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_RUNTIME_HEADLESS_UNSAFE = "extension_runtime_headless_unsafe"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_APPLY_FAILED = "extension_apply_failed"; //$NON-NLS-1$
    public static final String CATEGORY_EXTENSION_MODEL_UNAVAILABLE = ProjectCapabilityFailure.CATEGORY_EXTENSION_MODEL_UNAVAILABLE;

    private ExtensionLifecycleFailure()
    {
    }

    public static ToolResult extensionProjectRequired(String toolName, ResolvedProjectContext context)
    {
        return error(CATEGORY_EXTENSION_PROJECT_REQUIRED,
                "Tool '" + toolName + "' requires an extension project.", toolName, context, null, null); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static ToolResult parentMissing(String toolName, ResolvedProjectContext context, String detail)
    {
        return error(CATEGORY_EXTENSION_PARENT_MISSING,
                buildMessage("Tool '" + toolName + "' could not resolve a parent configuration project.", detail), //$NON-NLS-1$ //$NON-NLS-2$
                toolName, context, null, null);
    }

    public static ToolResult targetNotFound(String toolName, ResolvedProjectContext context, String parentProjectName,
            String applicationId, String detail)
    {
        return error(CATEGORY_EXTENSION_TARGET_NOT_FOUND,
                buildMessage("Tool '" + toolName + "' could not resolve the requested runtime target.", detail), //$NON-NLS-1$ //$NON-NLS-2$
                toolName, context, parentProjectName, applicationId);
    }

    public static ToolResult runtimeServiceUnavailable(String toolName, ResolvedProjectContext context,
            String parentProjectName, String detail)
    {
        return error(CATEGORY_EXTENSION_RUNTIME_SERVICE_UNAVAILABLE,
                buildMessage("Tool '" + toolName + "' could not access the EDT runtime execution services.", detail), //$NON-NLS-1$ //$NON-NLS-2$
                toolName, context, parentProjectName, null);
    }

    public static ToolResult runtimeCheckFailed(String toolName, ResolvedProjectContext context, String parentProjectName,
            String applicationId, String detail)
    {
        return error(CATEGORY_EXTENSION_RUNTIME_CHECK_FAILED,
                buildMessage("Tool '" + toolName + "' failed while executing the extension runtime check.", detail), //$NON-NLS-1$ //$NON-NLS-2$
                toolName, context, parentProjectName, applicationId);
    }

    public static ToolResult runtimeAccessSettingsRequired(String toolName, ResolvedProjectContext context,
            String parentProjectName, String applicationId, String detail)
    {
        return error(CATEGORY_EXTENSION_RUNTIME_ACCESS_SETTINGS_REQUIRED,
                buildMessage("Tool '" + toolName + "' requires valid infobase access settings before EDT can use the extension runtime bridge.", detail), //$NON-NLS-1$ //$NON-NLS-2$
                toolName, context, parentProjectName, applicationId);
    }

    public static ToolResult runtimeBridgeBusy(String toolName, ResolvedProjectContext context, String parentProjectName,
            String applicationId, String detail)
    {
        return error(CATEGORY_EXTENSION_RUNTIME_BRIDGE_BUSY,
                buildMessage("Tool '" + toolName + "' cannot start because the EDT extension runtime bridge is already busy or stuck.", detail), //$NON-NLS-1$ //$NON-NLS-2$
                toolName, context, parentProjectName, applicationId);
    }

    public static ToolResult runtimeHeadlessUnsafe(String toolName, ResolvedProjectContext context,
            String parentProjectName, String applicationId, String detail)
    {
        return error(CATEGORY_EXTENSION_RUNTIME_HEADLESS_UNSAFE,
                buildMessage(
                        "Tool '" + toolName + "' is disabled because the current EDT runtime path is not safe for non-interactive MCP use.", //$NON-NLS-1$ //$NON-NLS-2$
                        detail),
                toolName, context, parentProjectName, applicationId);
    }

    public static ToolResult applyFailed(String toolName, ResolvedProjectContext context, String parentProjectName,
            String applicationId, String detail)
    {
        return error(CATEGORY_EXTENSION_APPLY_FAILED,
                buildMessage(
                        "Tool '" + toolName + "' failed while applying the extension project to the selected infobase target.", //$NON-NLS-1$ //$NON-NLS-2$
                        detail),
                toolName, context, parentProjectName, applicationId);
    }

    public static ToolResult modelUnavailable(String toolName, ResolvedProjectContext context, String detail)
    {
        return error(CATEGORY_EXTENSION_MODEL_UNAVAILABLE,
                buildMessage("Tool '" + toolName + "' could not access a compatible extension model.", detail), //$NON-NLS-1$ //$NON-NLS-2$
                toolName, context, null, null);
    }

    private static ToolResult error(String category, String message, String toolName, ResolvedProjectContext context,
            String parentProjectName, String applicationId)
    {
        ToolResult result = ToolResult.error(message)
                .put("category", category); //$NON-NLS-1$
        if (context != null && context.getProjectName() != null)
        {
            result.put("projectName", context.getProjectName()); //$NON-NLS-1$
        }
        if (context != null)
        {
            result.put("projectKind", context.getProjectKind().getWireValue()); //$NON-NLS-1$
        }
        if (toolName != null)
        {
            result.put("tool", toolName); //$NON-NLS-1$
        }
        if (parentProjectName != null)
        {
            result.put("parentProjectName", parentProjectName); //$NON-NLS-1$
        }
        if (applicationId != null)
        {
            result.put("applicationId", applicationId); //$NON-NLS-1$
        }
        return result;
    }

    private static String buildMessage(String base, String detail)
    {
        if (detail == null || detail.isBlank())
        {
            return base;
        }
        return base + " " + detail; //$NON-NLS-1$
    }
}
