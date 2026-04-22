/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;

public class YaxUnitRuntimeAdapterCompatibilityTest
{
    @Test
    public void testSupportedEdtBaselineExposesStartupOptionSetter()
    {
        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();

        arguments.setStartupOption("RunUnitTests=/tmp/yaxunit-config.json"); //$NON-NLS-1$

        assertEquals("RunUnitTests=/tmp/yaxunit-config.json", arguments.getStartupOption()); //$NON-NLS-1$
    }
}
