/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provider bridge lookup without exposing provider transport details through MCP.
 */
public final class UnitTestSessionProviderBridgeRegistry
{
    private final ConcurrentMap<String, UnitTestSessionProviderBridge> bridges = new ConcurrentHashMap<>();

    public void register(UnitTestSessionProviderBridge bridge)
    {
        if (bridge == null || bridge.getProvider() == null || bridge.getProvider().isBlank())
        {
            throw new IllegalArgumentException("provider bridge must declare provider"); //$NON-NLS-1$
        }
        bridges.put(normalizeProvider(bridge.getProvider()), bridge);
    }

    public UnitTestSessionProviderBridge get(String provider)
    {
        String normalized = normalizeProvider(provider);
        return normalized != null ? bridges.get(normalized) : null;
    }

    public Map<String, UnitTestSessionProviderBridge> snapshot()
    {
        return Map.copyOf(bridges);
    }

    public void clear()
    {
        bridges.clear();
    }

    public void closeAll()
    {
        bridges.values().forEach(bridge -> {
            if (bridge instanceof AutoCloseable closeable)
            {
                try
                {
                    closeable.close();
                }
                catch (Exception e)
                {
                    // Provider bridge shutdown must not block MCP server shutdown.
                }
            }
        });
    }

    private static String normalizeProvider(String provider)
    {
        if (provider == null)
        {
            return null;
        }
        String trimmed = provider.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }
}
