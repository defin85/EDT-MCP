/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Converts YAxUnit RPC report payloads into retained JUnit XML.
 */
final class YaxUnitRemoteReportConverter
{
    private YaxUnitRemoteReportConverter()
    {
    }

    static String toJUnitXml(JsonElement reportData)
    {
        JsonArray suites = asArray(reportData);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"); //$NON-NLS-1$
        xml.append("<testsuites>"); //$NON-NLS-1$
        for (JsonElement suiteElement : suites)
        {
            if (suiteElement != null && suiteElement.isJsonObject())
            {
                appendSuite(xml, suiteElement.getAsJsonObject());
            }
        }
        xml.append("</testsuites>"); //$NON-NLS-1$
        return xml.toString();
    }

    private static JsonArray asArray(JsonElement reportData)
    {
        if (reportData != null && reportData.isJsonArray())
        {
            return reportData.getAsJsonArray();
        }
        JsonArray array = new JsonArray();
        if (reportData != null && reportData.isJsonObject())
        {
            array.add(reportData);
        }
        return array;
    }

    private static void appendSuite(StringBuilder xml, JsonObject suite)
    {
        xml.append("<testsuite"); //$NON-NLS-1$
        appendAttribute(xml, "id", stringValue(suite, "id")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "name", stringValue(suite, "name")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "package", stringValue(suite, "package")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "tests", stringValue(suite, "tests")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "failures", stringValue(suite, "failures")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "errors", stringValue(suite, "errors")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "skipped", stringValue(suite, "skipped")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "time", stringValue(suite, "time")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "timestamp", stringValue(suite, "timestamp")); //$NON-NLS-1$ //$NON-NLS-2$
        xml.append(">"); //$NON-NLS-1$

        appendErrors(xml, suite, "error"); //$NON-NLS-1$
        JsonArray testCases = arrayValue(suite, "testcase"); //$NON-NLS-1$
        for (JsonElement testCaseElement : testCases)
        {
            if (testCaseElement != null && testCaseElement.isJsonObject())
            {
                appendTestCase(xml, testCaseElement.getAsJsonObject());
            }
        }
        xml.append("</testsuite>"); //$NON-NLS-1$
    }

    private static void appendTestCase(StringBuilder xml, JsonObject testCase)
    {
        xml.append("<testcase"); //$NON-NLS-1$
        appendAttribute(xml, "name", stringValue(testCase, "name")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "classname", stringValue(testCase, "classname")); //$NON-NLS-1$ //$NON-NLS-2$
        appendAttribute(xml, "time", stringValue(testCase, "time")); //$NON-NLS-1$ //$NON-NLS-2$
        xml.append(">"); //$NON-NLS-1$
        appendErrors(xml, testCase, "failure"); //$NON-NLS-1$
        appendErrors(xml, testCase, "error"); //$NON-NLS-1$
        appendErrors(xml, testCase, "skipped"); //$NON-NLS-1$
        xml.append("</testcase>"); //$NON-NLS-1$
    }

    private static void appendErrors(StringBuilder xml, JsonObject owner, String nodeName)
    {
        JsonArray values = arrayValue(owner, nodeName);
        for (JsonElement value : values)
        {
            JsonObject object = value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
            xml.append('<').append(nodeName);
            appendAttribute(xml, "message", stringValue(object, "message")); //$NON-NLS-1$ //$NON-NLS-2$
            appendAttribute(xml, "type", stringValue(object, "type")); //$NON-NLS-1$ //$NON-NLS-2$
            xml.append(">"); //$NON-NLS-1$
            String trace = stringValue(object, "trace"); //$NON-NLS-1$
            if (trace != null && !trace.isBlank())
            {
                xml.append(escapeText(trace));
            }
            xml.append("</").append(nodeName).append('>'); //$NON-NLS-1$
        }
    }

    private static JsonArray arrayValue(JsonObject object, String key)
    {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return new JsonArray();
        }
        JsonElement value = object.get(key);
        if (value.isJsonArray())
        {
            return value.getAsJsonArray();
        }
        JsonArray array = new JsonArray();
        array.add(value);
        return array;
    }

    private static void appendAttribute(StringBuilder xml, String name, String value)
    {
        if (value == null)
        {
            return;
        }
        xml.append(' ').append(name).append("=\"").append(escapeAttribute(value)).append('"'); //$NON-NLS-1$
    }

    private static String stringValue(JsonObject object, String key)
    {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return null;
        }
        JsonElement value = object.get(key);
        if (value.isJsonPrimitive())
        {
            return value.getAsString();
        }
        return value.toString();
    }

    private static String escapeAttribute(String value)
    {
        return escapeText(value).replace("\"", "&quot;"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String escapeText(String value)
    {
        return value.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
