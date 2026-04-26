/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugModelBridge;

/**
 * Evaluates a BSL expression in a suspended EDT runtime debug frame.
 */
public class EvaluateDebugExpressionTool implements IMcpTool
{
    public static final String NAME = "evaluate_debug_expression"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Capability: runtime debug control. Evaluate a bounded expression in a current suspended frame; fails closed for running or stale frameId values."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Evaluate debug expression") //$NON-NLS-1$
                .readOnlyHint(false)
                .destructiveHint(false)
                .idempotentHint(false)
                .openWorldHint(false)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("frameId", "Frame ID returned by get_debug_stack", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("expression", "BSL expression to evaluate in the suspended frame", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Bounded evaluation wait (default 5, max 60)") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("maxValueLength", "Maximum value string length (default 500, max 4000)") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("maxChildren", "Maximum child variables to include for object values (default 20, max 200)") //$NON-NLS-1$ //$NON-NLS-2$
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
        String frameId = JsonUtils.extractStringArgument(params, "frameId"); //$NON-NLS-1$
        String expression = JsonUtils.extractStringArgument(params, "expression"); //$NON-NLS-1$
        int timeoutSeconds = JsonUtils.extractIntArgument(params, "timeoutSeconds", 0); //$NON-NLS-1$
        int maxValueLength = JsonUtils.extractIntArgument(params, "maxValueLength", 0); //$NON-NLS-1$
        int maxChildren = JsonUtils.extractIntArgument(params, "maxChildren", 0); //$NON-NLS-1$
        return RuntimeDebugModelBridge.evaluateExpression(frameId, expression, timeoutSeconds, maxValueLength,
                maxChildren);
    }
}
