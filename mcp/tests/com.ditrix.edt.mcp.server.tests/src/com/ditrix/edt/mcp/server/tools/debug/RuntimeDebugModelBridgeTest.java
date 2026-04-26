/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Proxy;

import org.eclipse.debug.core.model.IStackFrame;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RuntimeDebugModelBridgeTest
{
    @Test
    public void testEvaluateExpressionReportsUnsupportedBackend()
    {
        String frameId = "frame-unsupported-backend"; //$NON-NLS-1$
        RuntimeDebugModelBridge.cacheFrameForTesting(frameId, stackFrame(true, false, "unsupported.debug.model")); //$NON-NLS-1$

        try
        {
            JsonObject payload = parse(RuntimeDebugModelBridge.evaluateExpression(frameId, "1 + 1", 1, 20, 0)); //$NON-NLS-1$

            assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
            assertEquals("unsupported_backend_capability", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
            assertFalse(payload.get("sideEffectFreeGuaranteed").getAsBoolean()); //$NON-NLS-1$
        }
        finally
        {
            RuntimeDebugModelBridge.removeFrameForTesting(frameId);
        }
    }

    @Test
    public void testEvaluateExpressionRejectsRunningFrame()
    {
        String frameId = "frame-running"; //$NON-NLS-1$
        RuntimeDebugModelBridge.cacheFrameForTesting(frameId, stackFrame(false, false, "unsupported.debug.model")); //$NON-NLS-1$

        try
        {
            JsonObject payload = parse(RuntimeDebugModelBridge.evaluateExpression(frameId, "1 + 1", 1, 20, 0)); //$NON-NLS-1$

            assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
            assertEquals("frame_not_suspended", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
            assertFalse(payload.get("sideEffectFreeGuaranteed").getAsBoolean()); //$NON-NLS-1$
        }
        finally
        {
            RuntimeDebugModelBridge.removeFrameForTesting(frameId);
        }
    }

    @Test
    public void testEvaluateExpressionReportsTimeout()
    {
        IStackFrame frame = stackFrame(true, false, "test.debug.model"); //$NON-NLS-1$

        JsonObject payload = parse(RuntimeDebugModelBridge.evaluateExpressionWithDelegate("frame-timeout", //$NON-NLS-1$
                "1 + 1", frame, (expression, context, listener) -> { //$NON-NLS-1$
                    // Simulate a backend that accepted the request but never completed it.
                }, "test.debug.model", 1, 20, 0)); //$NON-NLS-1$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("expression_evaluation_timeout", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(payload.get("sideEffectFreeGuaranteed").getAsBoolean()); //$NON-NLS-1$
    }

    private static IStackFrame stackFrame(boolean suspended, boolean terminated, String modelIdentifier)
    {
        return (IStackFrame)Proxy.newProxyInstance(RuntimeDebugModelBridgeTest.class.getClassLoader(),
                new Class<?>[] { IStackFrame.class }, (proxy, method, args) -> {
                    String name = method.getName();
                    if ("isSuspended".equals(name)) //$NON-NLS-1$
                    {
                        return suspended;
                    }
                    if ("isTerminated".equals(name)) //$NON-NLS-1$
                    {
                        return terminated;
                    }
                    if ("getModelIdentifier".equals(name)) //$NON-NLS-1$
                    {
                        return modelIdentifier;
                    }
                    if ("toString".equals(name)) //$NON-NLS-1$
                    {
                        return "TestStackFrame"; //$NON-NLS-1$
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType)
    {
        if (!returnType.isPrimitive())
        {
            return null;
        }
        if (returnType == boolean.class)
        {
            return false;
        }
        if (returnType == int.class)
        {
            return 0;
        }
        if (returnType == long.class)
        {
            return 0L;
        }
        return null;
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
