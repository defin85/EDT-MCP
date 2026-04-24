/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import com.google.gson.JsonParser;

public class YaxUnitRemoteReportConverterTest
{
    @Test
    public void testConvertsYaxUnitRpcReportPayloadToJUnitXml()
            throws IOException
    {
        String payload = "[" //$NON-NLS-1$
                + "{\"name\":\"Suite\",\"tests\":2,\"failures\":1,\"errors\":0,\"skipped\":0,\"time\":0.25," //$NON-NLS-1$
                + "\"testcase\":[{\"name\":\"Pass\",\"classname\":\"Module.Pass\",\"time\":0.1}," //$NON-NLS-1$
                + "{\"name\":\"Fail\",\"classname\":\"Module.Fail\",\"time\":0.15," //$NON-NLS-1$
                + "\"failure\":[{\"message\":\"bad <value>\",\"trace\":\"line 1\"}]}]}]"; //$NON-NLS-1$

        String xml = YaxUnitRemoteReportConverter.toJUnitXml(JsonParser.parseString(payload));
        JUnitReportParser.ParsedJUnitReport parsed = JUnitReportParser.parse(xml);

        assertEquals(2, parsed.getTotal());
        assertEquals(1, parsed.getPassed());
        assertEquals(1, parsed.getFailed());
        assertEquals(0, parsed.getErrored());
        assertEquals(0, parsed.getSkipped());
        assertEquals(250L, parsed.getDurationMs());
    }
}
