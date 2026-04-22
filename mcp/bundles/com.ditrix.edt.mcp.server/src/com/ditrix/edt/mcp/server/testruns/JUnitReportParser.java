/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Parser for retained JUnit XML reports.
 */
public final class JUnitReportParser
{
    private JUnitReportParser()
    {
    }

    public static ParsedJUnitReport parse(Path reportPath) throws IOException
    {
        return parse(Files.readString(reportPath));
    }

    public static ParsedJUnitReport parse(String xml) throws IOException
    {
        if (xml == null || xml.isBlank())
        {
            throw new IOException("JUnit report is empty"); //$NON-NLS-1$
        }
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (root == null)
            {
                throw new IOException("JUnit report does not contain a document element"); //$NON-NLS-1$
            }

            int tests = 0;
            int failures = 0;
            int errors = 0;
            int skipped = 0;
            long durationMs = 0L;
            List<String> failedTestsSample = new ArrayList<>();

            if ("testsuite".equals(root.getTagName())) //$NON-NLS-1$
            {
                tests = intAttribute(root, "tests"); //$NON-NLS-1$
                failures = intAttribute(root, "failures"); //$NON-NLS-1$
                errors = intAttribute(root, "errors"); //$NON-NLS-1$
                skipped = skippedCount(root);
                durationMs = durationMs(root);
                collectFailedTests(root.getElementsByTagName("testcase"), failedTestsSample); //$NON-NLS-1$
            }
            else
            {
                NodeList suites = root.getElementsByTagName("testsuite"); //$NON-NLS-1$
                for (int index = 0; index < suites.getLength(); index++)
                {
                    Element suite = (Element) suites.item(index);
                    tests += intAttribute(suite, "tests"); //$NON-NLS-1$
                    failures += intAttribute(suite, "failures"); //$NON-NLS-1$
                    errors += intAttribute(suite, "errors"); //$NON-NLS-1$
                    skipped += skippedCount(suite);
                    durationMs += durationMs(suite);
                }
                collectFailedTests(root.getElementsByTagName("testcase"), failedTestsSample); //$NON-NLS-1$
            }

            int passed = Math.max(0, tests - failures - errors - skipped);
            String status = failures == 0 && errors == 0 ? "passed" : "failed"; //$NON-NLS-1$ //$NON-NLS-2$
            return new ParsedJUnitReport(status, tests, passed, failures, skipped, errors, durationMs,
                    failedTestsSample);
        }
        catch (IOException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IOException("Failed to parse JUnit report: " + e.getMessage(), e); //$NON-NLS-1$
        }
    }

    private static int skippedCount(Element suite)
    {
        int skipped = intAttribute(suite, "skipped"); //$NON-NLS-1$
        if (skipped > 0)
        {
            return skipped;
        }
        return intAttribute(suite, "disabled"); //$NON-NLS-1$
    }

    private static int intAttribute(Element element, String name)
    {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank())
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static long durationMs(Element element)
    {
        String value = element.getAttribute("time"); //$NON-NLS-1$
        if (value == null || value.isBlank())
        {
            return 0L;
        }
        try
        {
            return Math.round(Double.parseDouble(value) * 1000d);
        }
        catch (NumberFormatException e)
        {
            return 0L;
        }
    }

    private static void collectFailedTests(NodeList testCases, List<String> failedTestsSample)
    {
        for (int index = 0; index < testCases.getLength(); index++)
        {
            Element testCase = (Element) testCases.item(index);
            boolean failed = testCase.getElementsByTagName("failure").getLength() > 0 //$NON-NLS-1$
                    || testCase.getElementsByTagName("error").getLength() > 0; //$NON-NLS-1$
            if (!failed)
            {
                continue;
            }
            if (failedTestsSample.size() >= 20)
            {
                break;
            }
            String className = testCase.getAttribute("classname"); //$NON-NLS-1$
            String testName = testCase.getAttribute("name"); //$NON-NLS-1$
            if (className != null && !className.isBlank())
            {
                failedTestsSample.add(className + "." + testName); //$NON-NLS-1$
            }
            else
            {
                failedTestsSample.add(testName);
            }
        }
    }

    public static final class ParsedJUnitReport
    {
        private final String status;
        private final int total;
        private final int passed;
        private final int failed;
        private final int skipped;
        private final int errored;
        private final long durationMs;
        private final List<String> failedTestsSample;

        public ParsedJUnitReport(String status, int total, int passed, int failed, int skipped, int errored,
                long durationMs, List<String> failedTestsSample)
        {
            this.status = status;
            this.total = total;
            this.passed = passed;
            this.failed = failed;
            this.skipped = skipped;
            this.errored = errored;
            this.durationMs = durationMs;
            this.failedTestsSample = failedTestsSample != null ? List.copyOf(failedTestsSample) : List.of();
        }

        public String getStatus()
        {
            return status;
        }

        public int getTotal()
        {
            return total;
        }

        public int getPassed()
        {
            return passed;
        }

        public int getFailed()
        {
            return failed;
        }

        public int getSkipped()
        {
            return skipped;
        }

        public int getErrored()
        {
            return errored;
        }

        public long getDurationMs()
        {
            return durationMs;
        }

        public List<String> getFailedTestsSample()
        {
            return failedTestsSample;
        }
    }
}
