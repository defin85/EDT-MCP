/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.ditrix.edt.mcp.server.tools.IMcpTool.ResponseType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for live acceptance-evidence guardrails.
 */
public class AcceptanceEvidenceLiveProbeGuardrailsTest
{
    @Test
    public void testFormCommandAvailabilityToolContract()
    {
        ProbeFormCommandAvailabilityTool tool = new ProbeFormCommandAvailabilityTool();

        assertEquals("probe_form_command_availability", tool.getName()); //$NON-NLS-1$
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getInputSchema().contains("\"projectName\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"applicationId\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"formPath\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"commandName\"")); //$NON-NLS-1$
        assertNotNull(tool.getAnnotations());
        assertEquals(Boolean.TRUE, tool.getAnnotations().getReadOnlyHint());
    }

    @Test
    public void testFormCommandAvailabilityReturnsUnsupportedWhenRuntimeStateIsUnknown()
    {
        JsonObject payload = ProbeFormCommandAvailabilityTool.buildUnsupportedResult(
                "TestProject", "app-1", //$NON-NLS-1$ //$NON-NLS-2$
                "Catalog.Products.Forms.ItemForm", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Form.form", //$NON-NLS-1$
                "Fill", 10, true); //$NON-NLS-1$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("unsupported", payload.get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(payload.get("readOnly").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("timeout").getAsBoolean()); //$NON-NLS-1$
        assertEquals("app-1", payload.getAsJsonObject("application").get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("form_command", payload.getAsJsonObject("target").get("type").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject evidence = payload.getAsJsonObject("evidence"); //$NON-NLS-1$
        assertTrue(evidence.get("metadataCommandFound").getAsBoolean()); //$NON-NLS-1$
        assertEquals("unknown", evidence.get("runtimeAvailability").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("runtime_form_command_api_unavailable", payload.getAsJsonArray("limitations") //$NON-NLS-1$ //$NON-NLS-2$
                .get(0).getAsJsonObject().get("id").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testDocumentDryRunToolContract()
    {
        ProbeDocumentWritePostDryRunTool tool = new ProbeDocumentWritePostDryRunTool();

        assertEquals("probe_document_write_post_dry_run", tool.getName()); //$NON-NLS-1$
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getInputSchema().contains("\"projectName\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"applicationId\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"documentRef\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"action\"")); //$NON-NLS-1$
        assertNotNull(tool.getAnnotations());
        assertEquals(Boolean.TRUE, tool.getAnnotations().getReadOnlyHint());
    }

    @Test
    public void testDocumentDryRunDefaultsToPostAndDoesNotMutate()
    {
        JsonObject payload = parse(new ProbeDocumentWritePostDryRunTool().execute(Map.of(
                "projectName", "TestProject", //$NON-NLS-1$ //$NON-NLS-2$
                "applicationId", "app-1", //$NON-NLS-1$ //$NON-NLS-2$
                "documentRef", "Document.SalesOrder:000000001"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("unsupported_safe_dry_run", payload.get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(payload.get("readOnly").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("performed").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("rollbackProven").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("timeout").getAsBoolean()); //$NON-NLS-1$
        assertEquals("post", payload.getAsJsonObject("target").get("action").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("document_write_post_rollback_not_proven", payload.getAsJsonArray("limitations") //$NON-NLS-1$ //$NON-NLS-2$
                .get(0).getAsJsonObject().get("id").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testDocumentDryRunRejectsUnsupportedAction()
    {
        JsonObject payload = parse(new ProbeDocumentWritePostDryRunTool().execute(Map.of(
                "projectName", "TestProject", //$NON-NLS-1$ //$NON-NLS-2$
                "applicationId", "app-1", //$NON-NLS-1$ //$NON-NLS-2$
                "documentRef", "Document.SalesOrder:000000001", //$NON-NLS-1$ //$NON-NLS-2$
                "action", "delete"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("action must be 'write' or 'post'", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
