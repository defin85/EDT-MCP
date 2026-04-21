/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticThreadDumpWatchdogTest
{
    @Test
    public void testBuildWarningMessageIncludesCorrelationAndTargetThread()
    {
        try (DiagnosticThreadDumpWatchdog watchdog = DiagnosticThreadDumpWatchdog.start("tool=revalidate_objects, project=Demo", //$NON-NLS-1$
                "refresh", 60000L)) //$NON-NLS-1$
        {
            String message = watchdog.buildWarningMessage();

            assertTrue(message.contains("[diag] watchdog fired for tool=revalidate_objects, project=Demo")); //$NON-NLS-1$
            assertTrue(message.contains("stage=refresh")); //$NON-NLS-1$
            assertTrue(message.contains("after 60000ms")); //$NON-NLS-1$
            assertTrue(message.contains("targetThread=" + Thread.currentThread().getName())); //$NON-NLS-1$
            assertTrue(message.contains("state=")); //$NON-NLS-1$
        }
    }

    @Test
    public void testBuildThreadDumpMessageIncludesHeaderAndCurrentThread()
    {
        try (DiagnosticThreadDumpWatchdog watchdog = DiagnosticThreadDumpWatchdog.start("tool=revalidate_objects, project=Demo", //$NON-NLS-1$
                "refresh", 60000L)) //$NON-NLS-1$
        {
            String dump = watchdog.buildThreadDumpMessage();

            assertTrue(dump.contains("[diag] thread dump for tool=revalidate_objects, project=Demo stage=refresh")); //$NON-NLS-1$
            assertTrue(dump.contains("\"" + Thread.currentThread().getName() + "\"")); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(dump.contains("\tat ")); //$NON-NLS-1$
        }
    }
}
