/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.List;
import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Guardrail for document write/post dry-run requests.
 */
public class ProbeDocumentWritePostDryRunTool implements IMcpTool
{
    public static final String NAME = "probe_document_write_post_dry_run"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Guarded live-evidence probe for document write/post dry-run. Returns an explicit " //$NON-NLS-1$
                + "unsupported-safe-dry-run outcome unless rollback semantics are proven."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("documentRef", "Document reference or identity to dry-run (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringEnumProperty("action", "Requested action. Default: post", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of("write", "post")) //$NON-NLS-1$ //$NON-NLS-2$
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
        return ToolAnnotations.readOnly("Probe document write/post dry-run"); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        String documentRef = JsonUtils.extractStringArgument(params, "documentRef"); //$NON-NLS-1$
        String action = JsonUtils.extractStringArgument(params, "action"); //$NON-NLS-1$
        int timeoutSeconds = clamp(JsonUtils.extractIntArgument(params, "timeoutSeconds", 10), 1, 60); //$NON-NLS-1$

        if (!hasText(projectName))
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(applicationId))
        {
            return ToolResult.error("applicationId is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(documentRef))
        {
            return ToolResult.error("documentRef is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(action))
        {
            action = "post"; //$NON-NLS-1$
        }
        if (!"write".equals(action) && !"post".equals(action)) //$NON-NLS-1$ //$NON-NLS-2$
        {
            return ToolResult.error("action must be 'write' or 'post'").toJson(); //$NON-NLS-1$
        }

        return buildUnsupportedResult(projectName, applicationId, documentRef, action, timeoutSeconds).toString();
    }

    static JsonObject buildUnsupportedResult(String projectName, String applicationId, String documentRef,
            String action, int timeoutSeconds)
    {
        JsonObject result = new JsonObject();
        result.addProperty("success", true); //$NON-NLS-1$
        result.addProperty("status", "unsupported_safe_dry_run"); //$NON-NLS-1$ //$NON-NLS-2$
        result.addProperty("readOnly", true); //$NON-NLS-1$
        result.addProperty("performed", false); //$NON-NLS-1$
        result.addProperty("rollbackProven", false); //$NON-NLS-1$
        result.addProperty("timeoutSeconds", timeoutSeconds); //$NON-NLS-1$
        result.addProperty("timeout", false); //$NON-NLS-1$

        JsonObject application = new JsonObject();
        application.addProperty("projectName", projectName); //$NON-NLS-1$
        application.addProperty("applicationId", applicationId); //$NON-NLS-1$
        result.add("application", application); //$NON-NLS-1$

        JsonObject target = new JsonObject();
        target.addProperty("type", "document_write_post_dry_run"); //$NON-NLS-1$ //$NON-NLS-2$
        target.addProperty("documentRef", documentRef); //$NON-NLS-1$
        target.addProperty("action", action); //$NON-NLS-1$
        result.add("target", target); //$NON-NLS-1$

        JsonArray limitations = new JsonArray();
        JsonObject limitation = new JsonObject();
        limitation.addProperty("id", "document_write_post_rollback_not_proven"); //$NON-NLS-1$ //$NON-NLS-2$
        limitation.addProperty("severity", "blocker"); //$NON-NLS-1$ //$NON-NLS-2$
        limitation.addProperty("message", //$NON-NLS-1$
                "Document write/post dry-run is not executed because rollback and side-effect isolation are not proven."); //$NON-NLS-1$
        limitation.addProperty("recommendation", //$NON-NLS-1$
                "Use a read-only probe or a dedicated test infobase path with proven rollback before enabling this operation."); //$NON-NLS-1$
        limitations.add(limitation);
        result.add("limitations", limitations); //$NON-NLS-1$
        return result;
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
