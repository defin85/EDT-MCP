/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tasks;

import com.ditrix.edt.mcp.server.protocol.jsonrpc.JsonRpcError;
import com.google.gson.JsonElement;

/**
 * Final stored result for a completed task.
 */
public final class TaskResultEnvelope
{
    private final JsonElement result;
    private final JsonRpcError error;

    private TaskResultEnvelope(JsonElement result, JsonRpcError error)
    {
        this.result = result;
        this.error = error;
    }

    public static TaskResultEnvelope success(JsonElement result)
    {
        return new TaskResultEnvelope(result, null);
    }

    public static TaskResultEnvelope error(JsonRpcError error)
    {
        return new TaskResultEnvelope(null, error);
    }

    public JsonElement getResult()
    {
        return result;
    }

    public JsonRpcError getError()
    {
        return error;
    }
}
