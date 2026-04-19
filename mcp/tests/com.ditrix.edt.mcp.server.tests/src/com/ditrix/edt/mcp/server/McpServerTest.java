/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class McpServerTest
{
    @Test
    public void testConfigureHttpServerPropertiesUsesMillisecondRequestAndResponseTimeouts()
    {
        Map<String, String> previousValues = new LinkedHashMap<>();
        remember(previousValues, McpServer.HTTP_SERVER_IDLE_INTERVAL_PROPERTY);
        remember(previousValues, McpServer.HTTP_SERVER_MAX_IDLE_CONNECTIONS_PROPERTY);
        remember(previousValues, McpServer.HTTP_SERVER_MAX_REQUEST_TIME_PROPERTY);
        remember(previousValues, McpServer.HTTP_SERVER_MAX_RESPONSE_TIME_PROPERTY);

        try
        {
            McpServer.configureHttpServerProperties();

            assertEquals(McpServer.HTTP_SERVER_IDLE_INTERVAL_SECONDS,
                    System.getProperty(McpServer.HTTP_SERVER_IDLE_INTERVAL_PROPERTY));
            assertEquals(McpServer.HTTP_SERVER_MAX_IDLE_CONNECTIONS,
                    System.getProperty(McpServer.HTTP_SERVER_MAX_IDLE_CONNECTIONS_PROPERTY));
            assertEquals(McpServer.HTTP_SERVER_MAX_REQUEST_TIME_MS,
                    System.getProperty(McpServer.HTTP_SERVER_MAX_REQUEST_TIME_PROPERTY));
            assertEquals(McpServer.HTTP_SERVER_MAX_RESPONSE_TIME_MS,
                    System.getProperty(McpServer.HTTP_SERVER_MAX_RESPONSE_TIME_PROPERTY));
        }
        finally
        {
            restore(previousValues);
        }
    }

    private static void remember(Map<String, String> previousValues, String propertyName)
    {
        previousValues.put(propertyName, System.getProperty(propertyName));
    }

    private static void restore(Map<String, String> previousValues)
    {
        for (Map.Entry<String, String> entry : previousValues.entrySet())
        {
            if (entry.getValue() == null)
            {
                System.clearProperty(entry.getKey());
            }
            else
            {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
