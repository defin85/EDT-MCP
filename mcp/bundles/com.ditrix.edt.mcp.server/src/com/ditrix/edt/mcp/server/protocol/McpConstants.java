/**
 * Copyright (c) 2025 DitriX
 */
package com.ditrix.edt.mcp.server.protocol;

import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

/**
 * MCP protocol constants.
 * Implements MCP 2025-11-25 specification.
 */
public final class McpConstants
{
    /** JSON-RPC version */
    public static final String JSONRPC_VERSION = "2.0"; //$NON-NLS-1$
    
    /** MCP protocol version - updated to 2025-11-25 */
    public static final String PROTOCOL_VERSION = "2025-11-25"; //$NON-NLS-1$
    
    /** Server name */
    public static final String SERVER_NAME = "edt-mcp-server"; //$NON-NLS-1$
    
    /** Plugin author */
    public static final String AUTHOR = "DitriX, Diversus23"; //$NON-NLS-1$
    
    /** Plugin version - read from Bundle-Version at runtime, set by tycho-versions-plugin */
    public static final String PLUGIN_VERSION;

    static
    {
        org.osgi.framework.Bundle bundle = FrameworkUtil.getBundle(McpConstants.class);
        if (bundle != null)
        {
            Version v = bundle.getVersion();
            PLUGIN_VERSION = v.getMajor() + "." + v.getMinor() + "." + v.getMicro(); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else
        {
            PLUGIN_VERSION = "unknown"; //$NON-NLS-1$
        }
    }
    
    // JSON-RPC error codes
    /** Parse error */
    public static final int ERROR_PARSE = -32700;
    
    /** Invalid request */
    public static final int ERROR_INVALID_REQUEST = -32600;
    
    /** Method not found */
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    
    /** Invalid params */
    public static final int ERROR_INVALID_PARAMS = -32602;
    
    /** Internal error */
    public static final int ERROR_INTERNAL = -32603;
    
    // HTTP Headers
    /** MCP Protocol Version header */
    public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"; //$NON-NLS-1$
    
    /** MCP Session ID header */
    public static final String HEADER_SESSION_ID = "MCP-Session-Id"; //$NON-NLS-1$
    
    // MCP methods
    /** Initialize method */
    public static final String METHOD_INITIALIZE = "initialize"; //$NON-NLS-1$
    
    /** Initialized notification */
    public static final String METHOD_INITIALIZED = "notifications/initialized"; //$NON-NLS-1$

    /** Progress notification */
    public static final String METHOD_NOTIFICATION_PROGRESS = "notifications/progress"; //$NON-NLS-1$

    /** Task status notification */
    public static final String METHOD_NOTIFICATION_TASKS_STATUS = "notifications/tasks/status"; //$NON-NLS-1$
    
    /** Tools list method */
    public static final String METHOD_TOOLS_LIST = "tools/list"; //$NON-NLS-1$
    
    /** Tools call method */
    public static final String METHOD_TOOLS_CALL = "tools/call"; //$NON-NLS-1$

    /** Tasks get method */
    public static final String METHOD_TASKS_GET = "tasks/get"; //$NON-NLS-1$

    /** Tasks list method */
    public static final String METHOD_TASKS_LIST = "tasks/list"; //$NON-NLS-1$

    /** Tasks result method */
    public static final String METHOD_TASKS_RESULT = "tasks/result"; //$NON-NLS-1$

    /** Tasks cancel method */
    public static final String METHOD_TASKS_CANCEL = "tasks/cancel"; //$NON-NLS-1$

    /** Related task metadata key */
    public static final String META_RELATED_TASK = "io.modelcontextprotocol/related-task"; //$NON-NLS-1$

    /** Immediate-response metadata key for models */
    public static final String META_MODEL_IMMEDIATE_RESPONSE = "io.modelcontextprotocol/model-immediate-response"; //$NON-NLS-1$

    /** Detached continuation metadata key */
    public static final String META_DETACHED_CONTINUATION = "io.ditrix.edt.mcp/detached-continuation"; //$NON-NLS-1$

    /** Blocking operation metadata key */
    public static final String META_BLOCKING_OPERATION = "io.ditrix.edt.mcp/blocking-operation"; //$NON-NLS-1$
    
    private McpConstants()
    {
        // Utility class
    }
}
