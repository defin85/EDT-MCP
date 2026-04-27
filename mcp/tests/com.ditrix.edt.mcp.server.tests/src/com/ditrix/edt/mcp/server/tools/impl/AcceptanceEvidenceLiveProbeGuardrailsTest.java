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

    @Test
    public void testDocumentMovementsToolContract()
    {
        ProbeDocumentMovementsTool tool = new ProbeDocumentMovementsTool();

        assertEquals("probe_document_movements", tool.getName()); //$NON-NLS-1$
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getInputSchema().contains("\"projectName\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"applicationId\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"recorder\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"registers\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"timeoutSeconds\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"sampleLimit\"")); //$NON-NLS-1$
        assertFalse(tool.getInputSchema().contains("queryText")); //$NON-NLS-1$
        assertNotNull(tool.getAnnotations());
        assertEquals(Boolean.TRUE, tool.getAnnotations().getReadOnlyHint());
    }

    @Test
    public void testDocumentMovementsUnsupportedResultDoesNotReadOrMutate()
    {
        JsonObject payload = ProbeDocumentMovementsTool.buildUnsupportedResult(
                "TestProject", "app-1", null, //$NON-NLS-1$ //$NON-NLS-2$
                "Document.SalesOrder:000000001", //$NON-NLS-1$
                java.util.List.of("AccumulationRegister.Stock"), //$NON-NLS-1$
                10, 5, "document_movement_read_transport_unavailable", "unsupported", //$NON-NLS-1$ //$NON-NLS-2$
                "No read transport", "Add a proven read-only transport"); //$NON-NLS-1$ //$NON-NLS-2$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("unsupported", payload.get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(payload.get("readOnly").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("performed").getAsBoolean()); //$NON-NLS-1$
        assertFalse(payload.get("timeout").getAsBoolean()); //$NON-NLS-1$
        assertEquals(5, payload.get("sampleLimit").getAsInt()); //$NON-NLS-1$
        assertEquals("document_movements", payload.getAsJsonObject("target").get("type").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("AccumulationRegister.Stock", payload.getAsJsonObject("target") //$NON-NLS-1$ //$NON-NLS-2$
                .getAsJsonArray("registers").get(0).getAsString()); //$NON-NLS-1$
        JsonObject preflight = payload.getAsJsonObject("preflight"); //$NON-NLS-1$
        assertEquals("resolved", preflight.get("target").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("valid", preflight.get("accessSettings").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("unsupported", preflight.get("runtimeReadTransport").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("not_started", preflight.get("runtimeBridge").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(preflight.get("unsupportedTarget").getAsBoolean()); //$NON-NLS-1$
        JsonObject transport = payload.getAsJsonObject("transport"); //$NON-NLS-1$
        assertEquals("unsupported", transport.get("status").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(transport.get("readOnlyProven").getAsBoolean()); //$NON-NLS-1$
        assertEquals("not_attempted", transport.get("queryExecution").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(transport.get("clientSuppliedQueryAccepted").getAsBoolean()); //$NON-NLS-1$
        JsonObject evidence = payload.getAsJsonObject("evidence"); //$NON-NLS-1$
        assertFalse(evidence.get("rowCountKnown").getAsBoolean()); //$NON-NLS-1$
        assertEquals(0, evidence.get("registerCount").getAsInt()); //$NON-NLS-1$
        assertEquals("document_movement_read_transport_unavailable", payload.getAsJsonArray("limitations") //$NON-NLS-1$ //$NON-NLS-2$
                .get(0).getAsJsonObject().get("id").getAsString()); //$NON-NLS-1$
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
