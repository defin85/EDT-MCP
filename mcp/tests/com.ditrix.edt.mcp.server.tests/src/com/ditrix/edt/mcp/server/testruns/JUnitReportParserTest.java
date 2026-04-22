/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JUnitReportParserTest
{
    @Test
    public void testParseSingleSuiteReport()
            throws Exception
    {
        String xml = "<testsuite name=\"suite\" tests=\"3\" failures=\"1\" errors=\"0\" skipped=\"1\" time=\"1.5\">" //$NON-NLS-1$
                + "<testcase classname=\"tests.Module\" name=\"Pass\"/>" //$NON-NLS-1$
                + "<testcase classname=\"tests.Module\" name=\"Fail\"><failure>boom</failure></testcase>" //$NON-NLS-1$
                + "<testcase classname=\"tests.Module\" name=\"Skip\"><skipped/></testcase>" //$NON-NLS-1$
                + "</testsuite>"; //$NON-NLS-1$

        JUnitReportParser.ParsedJUnitReport parsed = JUnitReportParser.parse(xml);

        assertEquals("failed", parsed.getStatus()); //$NON-NLS-1$
        assertEquals(3, parsed.getTotal());
        assertEquals(1, parsed.getPassed());
        assertEquals(1, parsed.getFailed());
        assertEquals(1, parsed.getSkipped());
        assertEquals(0, parsed.getErrored());
        assertEquals(1500L, parsed.getDurationMs());
        assertEquals("tests.Module.Fail", parsed.getFailedTestsSample().get(0)); //$NON-NLS-1$
    }

    @Test
    public void testParseTestsuitesReportAggregatesTotals()
            throws Exception
    {
        String xml = "<testsuites>" //$NON-NLS-1$
                + "<testsuite name=\"suite-a\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.2\">" //$NON-NLS-1$
                + "<testcase classname=\"tests.A\" name=\"PassA\"/>" //$NON-NLS-1$
                + "<testcase classname=\"tests.A\" name=\"PassB\"/>" //$NON-NLS-1$
                + "</testsuite>" //$NON-NLS-1$
                + "<testsuite name=\"suite-b\" tests=\"2\" failures=\"0\" errors=\"1\" skipped=\"0\" time=\"0.3\">" //$NON-NLS-1$
                + "<testcase classname=\"tests.B\" name=\"Pass\"/>" //$NON-NLS-1$
                + "<testcase classname=\"tests.B\" name=\"Error\"><error>boom</error></testcase>" //$NON-NLS-1$
                + "</testsuite>" //$NON-NLS-1$
                + "</testsuites>"; //$NON-NLS-1$

        JUnitReportParser.ParsedJUnitReport parsed = JUnitReportParser.parse(xml);

        assertEquals("failed", parsed.getStatus()); //$NON-NLS-1$
        assertEquals(4, parsed.getTotal());
        assertEquals(3, parsed.getPassed());
        assertEquals(0, parsed.getFailed());
        assertEquals(0, parsed.getSkipped());
        assertEquals(1, parsed.getErrored());
        assertEquals(500L, parsed.getDurationMs());
        assertTrue(parsed.getFailedTestsSample().contains("tests.B.Error")); //$NON-NLS-1$
    }
}
