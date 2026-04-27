/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ditrix.edt.mcp.server.tools.IMcpTool.ResponseType;
import com.ditrix.edt.mcp.server.tools.McpToolRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for installed runtime capability discovery.
 */
public class DescribeCapabilitiesToolTest
{
    private McpToolRegistry registry;

    @Before
    public void setUp()
    {
        registry = McpToolRegistry.getInstance();
        registry.clear();
    }

    @After
    public void tearDown()
    {
        registry.clear();
    }

    @Test
    public void testToolContract()
    {
        DescribeCapabilitiesTool tool = new DescribeCapabilitiesTool();

        assertEquals("describe_capabilities", tool.getName()); //$NON-NLS-1$
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getInputSchema().contains("\"type\":\"object\"")); //$NON-NLS-1$
        assertNotNull(tool.getAnnotations());
        assertEquals(Boolean.TRUE, tool.getAnnotations().getReadOnlyHint());
    }

    @Test
    public void testReportsRegisteredToolsResourcesAndCapabilityStatuses()
    {
        registerDiscoveryFixture();

        JsonObject payload = parse(new DescribeCapabilitiesTool().execute(Map.of()));

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("edt-mcp-server", payload.getAsJsonObject("server").get("serverName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject tools = payload.getAsJsonObject("tools"); //$NON-NLS-1$
        assertEquals(9, tools.get("count").getAsInt()); //$NON-NLS-1$
        assertArrayContains(tools.getAsJsonArray("names"), "describe_capabilities"); //$NON-NLS-1$ //$NON-NLS-2$
        assertArrayContains(tools.getAsJsonArray("names"), "probe_document_write_post_dry_run"); //$NON-NLS-1$ //$NON-NLS-2$
        assertArrayContains(tools.getAsJsonArray("names"), "probe_document_movements"); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject describeItem = findToolItem(tools.getAsJsonArray("items"), "describe_capabilities"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("JSON", describeItem.get("responseType").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("forbidden", describeItem.get("taskSupport").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(describeItem.getAsJsonObject("annotations").get("readOnlyHint").getAsBoolean()); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject resources = payload.getAsJsonObject("resources"); //$NON-NLS-1$
        assertTrue(resources.get("count").getAsInt() > 0); //$NON-NLS-1$
        assertArrayContains(resources.getAsJsonArray("uris"), //$NON-NLS-1$
                "edt-mcp://capabilities/live-acceptance-evidence"); //$NON-NLS-1$

        JsonObject capabilities = payload.getAsJsonObject("capabilities"); //$NON-NLS-1$
        assertEquals("supported", capabilities.getAsJsonObject("asyncTasks").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("unsupported", capabilities.getAsJsonObject("runtimeDebug").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("partial", capabilities.getAsJsonObject("liveEvidence").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertArrayContains(capabilities.getAsJsonObject("liveEvidence").getAsJsonArray("readOnlyProbes"), //$NON-NLS-1$ //$NON-NLS-2$
                "probe_form_command_availability"); //$NON-NLS-1$
        assertArrayContains(capabilities.getAsJsonObject("liveEvidence").getAsJsonArray("readOnlyProbes"), //$NON-NLS-1$ //$NON-NLS-2$
                "probe_document_movements"); //$NON-NLS-1$
        assertEquals("unsupported", capabilities.getAsJsonObject("liveEvidence") //$NON-NLS-1$ //$NON-NLS-2$
                .getAsJsonObject("mutationDryRun").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("unsupported", capabilities.getAsJsonObject("liveEvidence") //$NON-NLS-1$ //$NON-NLS-2$
                .getAsJsonObject("documentMovements").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertLimitation(payload.getAsJsonArray("limitations"), "document_movement_read_transport_unavailable"); //$NON-NLS-1$ //$NON-NLS-2$
        assertLimitation(payload.getAsJsonArray("limitations"), "document_write_post_rollback_not_proven"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testReportsUnsupportedAreasWhenNoAreaToolsAreRegistered()
    {
        registry.register(new DescribeCapabilitiesTool());

        JsonObject payload = parse(new DescribeCapabilitiesTool().execute(Map.of()));
        JsonObject capabilities = payload.getAsJsonObject("capabilities"); //$NON-NLS-1$

        assertEquals("unsupported", capabilities.getAsJsonObject("asyncTasks").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("unsupported", capabilities.getAsJsonObject("runtimeDebug").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("unsupported", capabilities.getAsJsonObject("yaxUnit").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("unsupported", capabilities.getAsJsonObject("extensionLifecycle").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("unsupported", capabilities.getAsJsonObject("liveEvidence").get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private void registerDiscoveryFixture()
    {
        registry.register(new DescribeCapabilitiesTool());
        registry.register(new ListTasksTool());
        registry.register(new GetTaskResultTool());
        registry.register(new WaitTaskTool());
        registry.register(new BslQueryDiagnosticsTool());
        registry.register(new FormEventContractTool());
        registry.register(new ProbeFormCommandAvailabilityTool());
        registry.register(new ProbeDocumentWritePostDryRunTool());
        registry.register(new ProbeDocumentMovementsTool());
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonObject findToolItem(JsonArray items, String name)
    {
        for (int i = 0; i < items.size(); i++)
        {
            JsonObject item = items.get(i).getAsJsonObject();
            if (name.equals(item.get("name").getAsString())) //$NON-NLS-1$
            {
                return item;
            }
        }
        fail("Tool item not found: " + name); //$NON-NLS-1$
        return null;
    }

    private static void assertArrayContains(JsonArray array, String expected)
    {
        for (int i = 0; i < array.size(); i++)
        {
            if (expected.equals(array.get(i).getAsString()))
            {
                return;
            }
        }
        fail("Array does not contain: " + expected); //$NON-NLS-1$
    }

    private static void assertLimitation(JsonArray limitations, String expectedId)
    {
        for (int i = 0; i < limitations.size(); i++)
        {
            if (expectedId.equals(limitations.get(i).getAsJsonObject().get("id").getAsString())) //$NON-NLS-1$
            {
                return;
            }
        }
        fail("Limitation not found: " + expectedId); //$NON-NLS-1$
    }
}
