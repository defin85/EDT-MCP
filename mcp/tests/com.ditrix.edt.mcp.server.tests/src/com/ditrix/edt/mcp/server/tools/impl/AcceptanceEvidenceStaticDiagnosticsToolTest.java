/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import com.ditrix.edt.mcp.server.tools.IMcpTool.ResponseType;

/**
 * Contract tests for static acceptance-evidence helpers.
 */
public class AcceptanceEvidenceStaticDiagnosticsToolTest
{
    @Test
    public void testBslQueryDiagnosticsToolContract()
    {
        BslQueryDiagnosticsTool tool = new BslQueryDiagnosticsTool();

        assertEquals("diagnose_bsl_queries", tool.getName()); //$NON-NLS-1$
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getInputSchema().contains("\"projectName\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"modulePath\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"methodName\"")); //$NON-NLS-1$
        assertNotNull(tool.getAnnotations());
        assertEquals(Boolean.TRUE, tool.getAnnotations().getReadOnlyHint());
    }

    @Test
    public void testExtractsStaticQueryTextWithSourceLocation()
    {
        List<String> lines = List.of(
                "Procedure BuildQuery()", //$NON-NLS-1$
                "    Query.Text = \"SELECT Ref \" +", //$NON-NLS-1$
                "        \"FROM Catalog.Products\";", //$NON-NLS-1$
                "EndProcedure"); //$NON-NLS-1$

        BslQueryDiagnosticsTool.ExtractionResult result = BslQueryDiagnosticsTool.extractQueries(lines,
                "CommonModules/Test/Module.bsl", "BuildQuery", 20); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(1, result.queries.size());
        BslQueryDiagnosticsTool.QueryExtraction query = result.queries.get(0);
        assertTrue(query.supportedExtraction);
        assertEquals("Query", query.queryIdentifier); //$NON-NLS-1$
        assertEquals("BuildQuery", query.methodName); //$NON-NLS-1$
        assertEquals(2, query.startLine);
        assertEquals(3, query.endLine);
        assertEquals("SELECT Ref FROM Catalog.Products", query.queryText); //$NON-NLS-1$
        assertTrue(result.limitations.isEmpty());
    }

    @Test
    public void testReportsDynamicQueryTextLimitation()
    {
        List<String> lines = List.of(
                "Procedure BuildQuery()", //$NON-NLS-1$
                "    Query.Text = BaseText + \" WHERE Ref = &Ref\";", //$NON-NLS-1$
                "EndProcedure"); //$NON-NLS-1$

        BslQueryDiagnosticsTool.ExtractionResult result = BslQueryDiagnosticsTool.extractQueries(lines,
                "CommonModules/Test/Module.bsl", null, 20); //$NON-NLS-1$

        assertEquals(1, result.queries.size());
        assertFalse(result.queries.get(0).supportedExtraction);
        assertEquals("dynamic_query_text", result.queries.get(0).limitationReason); //$NON-NLS-1$
        assertEquals(1, result.limitations.size());
    }

    @Test
    public void testReportsMissingMethodScope()
    {
        BslQueryDiagnosticsTool.ExtractionResult result = BslQueryDiagnosticsTool.extractQueries(
                List.of("Procedure Existing()", "EndProcedure"), //$NON-NLS-1$ //$NON-NLS-2$
                "CommonModules/Test/Module.bsl", "Missing", 20); //$NON-NLS-1$ //$NON-NLS-2$

        assertTrue(result.queries.isEmpty());
        assertEquals(1, result.limitations.size());
        assertEquals("method_not_found", result.limitations.get(0).id); //$NON-NLS-1$
    }

    @Test
    public void testFormEventContractToolContract()
    {
        FormEventContractTool tool = new FormEventContractTool();

        assertEquals("check_form_event_contract", tool.getName()); //$NON-NLS-1$
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getInputSchema().contains("\"formPath\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"eventName\"")); //$NON-NLS-1$
        assertTrue(tool.getInputSchema().contains("\"handlerName\"")); //$NON-NLS-1$
        assertNotNull(tool.getAnnotations());
        assertEquals(Boolean.TRUE, tool.getAnnotations().getReadOnlyHint());
    }

    @Test
    public void testValidFormEventBinding()
    {
        FormEventContractTool.ContractResult result = FormEventContractTool.checkContract(
                "Catalog.Products.Forms.ItemForm", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Form.form", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Module.bsl", //$NON-NLS-1$
                eventXml("BeforeWriteAtServer", "BeforeWriteAtServer"), //$NON-NLS-1$ //$NON-NLS-2$
                List.of("<events>", "<name>BeforeWriteAtServer</name>", "<handler>BeforeWriteAtServer</handler>", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "</events>"), //$NON-NLS-1$
                List.of("Procedure BeforeWriteAtServer(Cancel, WriteParameters)", "EndProcedure"), //$NON-NLS-1$ //$NON-NLS-2$
                "BeforeWriteAtServer", "BeforeWriteAtServer"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals("valid", result.status); //$NON-NLS-1$
        assertNotNull(result.binding);
        assertNotNull(result.procedure);
        assertEquals(1, result.procedure.startLine);
    }

    @Test
    public void testUnboundEventWhenHandlerExists()
    {
        FormEventContractTool.ContractResult result = FormEventContractTool.checkContract(
                "Catalog.Products.Forms.ItemForm", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Form.form", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Module.bsl", //$NON-NLS-1$
                "<form:Form/>", List.of("<form:Form/>"), //$NON-NLS-1$ //$NON-NLS-2$
                List.of("Procedure BeforeWriteAtServer(Cancel, WriteParameters)", "EndProcedure"), //$NON-NLS-1$ //$NON-NLS-2$
                "BeforeWriteAtServer", "BeforeWriteAtServer"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals("unbound_event", result.status); //$NON-NLS-1$
        assertNull(result.binding);
        assertNotNull(result.procedure);
    }

    @Test
    public void testMismatchedHandlerName()
    {
        FormEventContractTool.ContractResult result = FormEventContractTool.checkContract(
                "Catalog.Products.Forms.ItemForm", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Form.form", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Module.bsl", //$NON-NLS-1$
                eventXml("BeforeWriteAtServer", "OtherHandler"), //$NON-NLS-1$ //$NON-NLS-2$
                List.of("<handler>OtherHandler</handler>"), //$NON-NLS-1$
                List.of("Procedure BeforeWriteAtServer(Cancel, WriteParameters)", "EndProcedure"), //$NON-NLS-1$ //$NON-NLS-2$
                "BeforeWriteAtServer", "BeforeWriteAtServer"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals("mismatched_handler_name", result.status); //$NON-NLS-1$
    }

    @Test
    public void testMissingBoundProcedure()
    {
        FormEventContractTool.ContractResult result = FormEventContractTool.checkContract(
                "Catalog.Products.Forms.ItemForm", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Form.form", //$NON-NLS-1$
                "src/Catalogs/Products/Forms/ItemForm/Module.bsl", //$NON-NLS-1$
                eventXml("BeforeWriteAtServer", "BeforeWriteAtServer"), //$NON-NLS-1$ //$NON-NLS-2$
                List.of("<handler>BeforeWriteAtServer</handler>"), //$NON-NLS-1$
                List.of(), "BeforeWriteAtServer", null); //$NON-NLS-1$

        assertEquals("missing_procedure", result.status); //$NON-NLS-1$
        assertNotNull(result.binding);
        assertNull(result.procedure);
    }

    private static String eventXml(String eventName, String handlerName)
    {
        return "<form:Form xmlns:form=\"http://g5.1c.ru/v8/dt/form\">" //$NON-NLS-1$
                + "<events><name>" + eventName + "</name><handler>" + handlerName + "</handler></events>" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "</form:Form>"; //$NON-NLS-1$
    }
}
