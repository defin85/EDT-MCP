/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol;

import java.util.Set;

import com.ditrix.edt.mcp.server.progress.OperationProgressState;
import com.ditrix.edt.mcp.server.tools.impl.GetOperationSnapshotTool;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Helpers for machine-readable detached continuation metadata.
 */
public final class DetachedContinuationMeta
{
    private static final Set<String> CLEAN_PROJECT_HINT_STAGES = Set.of(
            "clean_build", //$NON-NLS-1$
            "lifecycle_wait", //$NON-NLS-1$
            "derived_data"); //$NON-NLS-1$
    private static final Set<String> CLEAN_PROJECT_PRE_START_STAGES = Set.of(
            "validation", //$NON-NLS-1$
            "lifecycle_prepare", //$NON-NLS-1$
            "completion", //$NON-NLS-1$
            "failure"); //$NON-NLS-1$

    private static final Set<String> REVALIDATE_HINT_STAGES = Set.of(
            "full_revalidation", //$NON-NLS-1$
            "waiting_for_build"); //$NON-NLS-1$
    private static final Set<String> REVALIDATE_PRE_START_STAGES = Set.of(
            "validation", //$NON-NLS-1$
            "refresh", //$NON-NLS-1$
            "object_lookup", //$NON-NLS-1$
            "object_validation", //$NON-NLS-1$
            "completion", //$NON-NLS-1$
            "failure"); //$NON-NLS-1$

    private static final Set<String> UPDATE_DATABASE_HINT_STAGES = Set.of(
            "update_start", //$NON-NLS-1$
            "waiting_for_edt", //$NON-NLS-1$
            "final_state_check"); //$NON-NLS-1$

    private DetachedContinuationMeta()
    {
        // Utility class
    }

    public static boolean isSupportedTool(String toolName)
    {
        return "clean_project".equals(toolName) //$NON-NLS-1$
                || "revalidate_objects".equals(toolName) //$NON-NLS-1$
                || "update_database".equals(toolName); //$NON-NLS-1$
    }

    public static boolean shouldExposeForCancellation(String toolName, String stage)
    {
        if (!hasText(toolName) || !hasText(stage))
        {
            return false;
        }
        switch (toolName)
        {
            case "clean_project": //$NON-NLS-1$
                return CLEAN_PROJECT_HINT_STAGES.contains(stage);
            case "revalidate_objects": //$NON-NLS-1$
                return REVALIDATE_HINT_STAGES.contains(stage);
            case "update_database": //$NON-NLS-1$
                return UPDATE_DATABASE_HINT_STAGES.contains(stage);
            default:
                return false;
        }
    }

    public static boolean shouldExposeForCancellation(String toolName, OperationProgressState progressState)
    {
        if (progressState == null)
        {
            return false;
        }

        String stage = progressState.getStage();
        if (shouldExposeForCancellation(toolName, stage))
        {
            return true;
        }

        if (!hasText(toolName) || !hasText(stage))
        {
            return false;
        }

        switch (toolName)
        {
            case "clean_project": //$NON-NLS-1$
                return !CLEAN_PROJECT_PRE_START_STAGES.contains(stage);
            case "revalidate_objects": //$NON-NLS-1$
                return !REVALIDATE_PRE_START_STAGES.contains(stage);
            default:
                return false;
        }
    }

    public static JsonElement attach(JsonElement payload, String operationId)
    {
        if (payload == null || !payload.isJsonObject() || !hasText(operationId))
        {
            return payload;
        }

        JsonObject copy = payload.getAsJsonObject().deepCopy();
        JsonObject meta = copy.has("_meta") && copy.get("_meta").isJsonObject() //$NON-NLS-1$
                ? copy.getAsJsonObject("_meta") : new JsonObject(); //$NON-NLS-1$
        JsonObject continuation = new JsonObject();
        continuation.addProperty("operationId", operationId); //$NON-NLS-1$
        continuation.addProperty("detached", true); //$NON-NLS-1$
        continuation.addProperty("pollTool", GetOperationSnapshotTool.NAME); //$NON-NLS-1$
        meta.add(McpConstants.META_DETACHED_CONTINUATION, continuation);
        copy.add("_meta", meta); //$NON-NLS-1$
        return copy;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
