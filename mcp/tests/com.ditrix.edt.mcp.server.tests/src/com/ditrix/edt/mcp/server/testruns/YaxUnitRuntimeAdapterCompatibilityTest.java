/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import com._1c.g5.v8.dt.platform.services.core.infobases.InfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;
import com._1c.g5.v8.dt.platform.services.model.InfobaseAccess;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class YaxUnitRuntimeAdapterCompatibilityTest
{
    @Test
    public void testSupportedEdtBaselineExposesStartupOptionSetter()
    {
        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();

        arguments.setStartupOption("RunUnitTests=/tmp/yaxunit-config.json"); //$NON-NLS-1$

        assertEquals("RunUnitTests=/tmp/yaxunit-config.json", arguments.getStartupOption()); //$NON-NLS-1$
    }

    @Test
    public void testBuildConfigJsonUsesLegacyMinimalShape()
    {
        YaxUnitRuntimeAdapter.RunRequest request = new YaxUnitRuntimeAdapter.RunRequest("run-1", "module", "tests", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "CommonModule.UnitTests", null, null, List.of("smoke", "fast"), 30, 60_000L); //$NON-NLS-1$ //$NON-NLS-2$
        Path reportPath = Path.of("build", YaxUnitRuntimeAdapter.DEFAULT_REPORT_FILE_NAME); //$NON-NLS-1$

        String json = YaxUnitRuntimeAdapter.buildConfigJson(request, reportPath);
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("jUnit", parsed.get("reportFormat").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(reportPath.toAbsolutePath().toString(), parsed.get("reportPath").getAsString()); //$NON-NLS-1$
        assertTrue(parsed.get("closeAfterTests").getAsBoolean()); //$NON-NLS-1$
        assertFalse(parsed.has("showReport")); //$NON-NLS-1$
        assertFalse(parsed.has("exitCode")); //$NON-NLS-1$
        assertFalse(parsed.has("projectPath")); //$NON-NLS-1$
        assertFalse(parsed.has("workspacePath")); //$NON-NLS-1$
        assertFalse(parsed.has("logging")); //$NON-NLS-1$

        JsonObject filter = parsed.getAsJsonObject("filter"); //$NON-NLS-1$
        assertEquals("tests", filter.getAsJsonArray("extensions").get(0).getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("CommonModule.UnitTests", filter.getAsJsonArray("modules").get(0).getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("smoke", filter.getAsJsonArray("tags").get(0).getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("fast", filter.getAsJsonArray("tags").get(1).getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testBuildConfigJsonEnablesSmokeToggleForSmokeExtension()
    {
        YaxUnitRuntimeAdapter.RunRequest request = new YaxUnitRuntimeAdapter.RunRequest("run-2", "all", "Smoke", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                null, null, null, List.of(), 30, 60_000L);
        Path reportPath = Path.of("build", YaxUnitRuntimeAdapter.DEFAULT_REPORT_FILE_NAME); //$NON-NLS-1$

        String json = YaxUnitRuntimeAdapter.buildConfigJson(request, reportPath);
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

        JsonObject smokeSettings = parsed.getAsJsonObject("ДымовыеТесты"); //$NON-NLS-1$
        assertTrue(smokeSettings.get("Использовать").getAsBoolean()); //$NON-NLS-1$
        assertTrue(smokeSettings.get("ОткрытиеФорм").getAsBoolean()); //$NON-NLS-1$
        JsonObject filter = parsed.getAsJsonObject("filter"); //$NON-NLS-1$
        assertEquals("Smoke", filter.getAsJsonArray("extensions").get(0).getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testApplyAccessSettingsCopiesInfobaseCredentials()
    {
        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();

        YaxUnitRuntimeAdapter.applyAccessSettings(arguments,
                new InfobaseAccessSettings(InfobaseAccess.INFOBASE, "Admin", "Secret", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertEquals(InfobaseAccess.INFOBASE, arguments.getAccess());
        assertEquals("Admin", arguments.getUsername()); //$NON-NLS-1$
        assertEquals("Secret", arguments.getPassword()); //$NON-NLS-1$
    }

    @Test
    public void testApplyAccessSettingsDoesNotCopyCredentialsForOsAccess()
    {
        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();

        YaxUnitRuntimeAdapter.applyAccessSettings(arguments,
                new InfobaseAccessSettings(InfobaseAccess.OS, "Admin", "Secret", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertEquals(InfobaseAccess.OS, arguments.getAccess());
        assertNull(arguments.getUsername());
        assertNull(arguments.getPassword());
    }

    @Test
    public void testResolveProducedReportPathPrefersExpectedFile()
            throws Exception
    {
        Path runDirectory = Files.createTempDirectory("yaxunit-report-exact-"); //$NON-NLS-1$
        Path expectedReportPath = runDirectory.resolve(YaxUnitRuntimeAdapter.DEFAULT_REPORT_FILE_NAME);
        Path fallbackReportPath = runDirectory.resolve("report.xml"); //$NON-NLS-1$
        try
        {
            Files.writeString(expectedReportPath, "<testsuite/>"); //$NON-NLS-1$
            Files.writeString(fallbackReportPath, "<testsuite/>"); //$NON-NLS-1$

            Path resolved = YaxUnitRuntimeAdapter.resolveProducedReportPath(runDirectory, expectedReportPath);

            assertEquals(expectedReportPath, resolved);
        }
        finally
        {
            Files.deleteIfExists(expectedReportPath);
            Files.deleteIfExists(fallbackReportPath);
            Files.deleteIfExists(runDirectory);
        }
    }

    @Test
    public void testResolveProducedReportPathFallsBackToLegacyCandidate()
            throws Exception
    {
        Path runDirectory = Files.createTempDirectory("yaxunit-report-fallback-"); //$NON-NLS-1$
        Path expectedReportPath = runDirectory.resolve(YaxUnitRuntimeAdapter.DEFAULT_REPORT_FILE_NAME);
        Path fallbackReportPath = runDirectory.resolve("report.xml"); //$NON-NLS-1$
        try
        {
            Files.writeString(fallbackReportPath, "<testsuite/>"); //$NON-NLS-1$

            Path resolved = YaxUnitRuntimeAdapter.resolveProducedReportPath(runDirectory, expectedReportPath);

            assertEquals(fallbackReportPath, resolved);
        }
        finally
        {
            Files.deleteIfExists(expectedReportPath);
            Files.deleteIfExists(fallbackReportPath);
            Files.deleteIfExists(runDirectory);
        }
    }
}
