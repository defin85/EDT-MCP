/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;

import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;
import com.ditrix.edt.mcp.server.protocol.GsonProvider;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.ResolvedConfigurationRuntimeContext;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * YAxUnit-specific runtime adapter over the configuration-project EDT runtime bridge.
 */
public final class YaxUnitRuntimeAdapter
{
    public static final String PROVIDER = "yaxunit"; //$NON-NLS-1$
    public static final String ENGINE_EXTENSION_NAME = "YAXUNIT"; //$NON-NLS-1$

    public static final class RunRequest
    {
        private final String runId;
        private final String scope;
        private final String testExtension;
        private final String testModule;
        private final String testPath;
        private final String suiteName;
        private final List<String> tagsInclude;
        private final int timeoutSeconds;
        private final long retentionTtlMs;

        public RunRequest(String runId, String scope, String testExtension, String testModule, String testPath,
                String suiteName, List<String> tagsInclude, int timeoutSeconds, long retentionTtlMs)
        {
            this.runId = runId;
            this.scope = scope;
            this.testExtension = testExtension;
            this.testModule = testModule;
            this.testPath = testPath;
            this.suiteName = suiteName;
            this.tagsInclude = tagsInclude != null ? List.copyOf(tagsInclude) : List.of();
            this.timeoutSeconds = timeoutSeconds;
            this.retentionTtlMs = retentionTtlMs;
        }

        public String getRunId()
        {
            return runId;
        }

        public String getScope()
        {
            return scope;
        }

        public String getTestExtension()
        {
            return testExtension;
        }

        public String getTestModule()
        {
            return testModule;
        }

        public String getTestPath()
        {
            return testPath;
        }

        public String getSuiteName()
        {
            return suiteName;
        }

        public List<String> getTagsInclude()
        {
            return tagsInclude;
        }

        public int getTimeoutSeconds()
        {
            return timeoutSeconds;
        }

        public long getRetentionTtlMs()
        {
            return retentionTtlMs;
        }
    }

    public static final class ExecutionResult
    {
        private final UnitTestRunRecord record;

        public ExecutionResult(UnitTestRunRecord record)
        {
            this.record = record;
        }

        public UnitTestRunRecord getRecord()
        {
            return record;
        }
    }

    private YaxUnitRuntimeAdapter()
    {
    }

    public static ToolResult preflightYaxUnitAvailability(String toolName, ResolvedConfigurationRuntimeContext context,
            IInfobaseApplication infobaseApplication, String applicationId,
            ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution)
    {
        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();
        arguments.setDisableStartupMessages(true);

        ConfigurationRuntimeBridgeSupport.InvocationResult<List<String>> invocation = ConfigurationRuntimeBridgeSupport
                .invokeWithGuard(toolName, context, applicationId,
                        () -> thickClientResolution.getLauncher().listConfigurationExtensions(
                                thickClientResolution.getComponent(),
                                infobaseApplication.getInfobase(), arguments));
        if (!invocation.isSuccess())
        {
            return invocation.getFailureResult();
        }

        List<String> installedExtensions = invocation.getValue();
        if (installedExtensions == null)
        {
            installedExtensions = List.of();
        }
        if (!installedExtensions.contains(ENGINE_EXTENSION_NAME))
        {
            return ToolResult.error("YAxUnit engine extension '" + ENGINE_EXTENSION_NAME //$NON-NLS-1$
                    + "' is not installed in the selected infobase target."); //$NON-NLS-1$
        }
        return null;
    }

    public static ExecutionResult execute(String toolName, ResolvedConfigurationRuntimeContext context,
            IApplication application, IInfobaseApplication infobaseApplication,
            ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution, RunRequest request)
            throws Exception
    {
        Path runDirectory = Files.createTempDirectory("edt-mcp-yaxunit-"); //$NON-NLS-1$
        Path configPath = runDirectory.resolve("yaxunit-config.json"); //$NON-NLS-1$
        Path reportPath = runDirectory.resolve("junit-report.xml"); //$NON-NLS-1$
        Path logPath = runDirectory.resolve("yaxunit.log"); //$NON-NLS-1$
        writeConfig(context, request, configPath, reportPath, logPath);

        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();
        arguments.setDisableStartupMessages(true);
        arguments.setStartupOption("RunUnitTests=" + configPath.toAbsolutePath()); //$NON-NLS-1$

        long timeoutMs = Math.max(10_000L, request.getTimeoutSeconds() * 1000L);
        ConfigurationRuntimeBridgeSupport.InvocationResult<Object> invocation = ConfigurationRuntimeBridgeSupport
                .invokeWithGuard(toolName, context, application.getId(), timeoutMs,
                        () -> invokeRunClient(thickClientResolution, infobaseApplication, arguments));
        if (!invocation.isSuccess())
        {
            throw new IOException("YAxUnit launch failed: " + invocation.getFailureResult().toJson()); //$NON-NLS-1$
        }

        if (!Files.isRegularFile(reportPath))
        {
            throw new IOException("YAxUnit run did not produce a readable JUnit report at " //$NON-NLS-1$
                    + reportPath.toAbsolutePath());
        }

        String junitXml = Files.readString(reportPath);
        JUnitReportParser.ParsedJUnitReport parsed = JUnitReportParser.parse(junitXml);
        Instant completedAt = Instant.now();
        Instant expiresAt = completedAt.plusMillis(request.getRetentionTtlMs());
        Map<String, Object> filters = buildPublicFilters(request);
        UnitTestRunRecord record = new UnitTestRunRecord(request.getRunId(), PROVIDER, context.getProjectName(),
                application.getId(), application.getName(), request.getScope(), parsed.getStatus(),
                parsed.getStatus().equals("passed") ? "All tests passed" : "One or more unit tests failed", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                parsed.getTotal(), parsed.getPassed(), parsed.getFailed(), parsed.getSkipped(), parsed.getErrored(),
                parsed.getDurationMs(), completedAt, expiresAt,
                List.of(UnitTestRunRecord.FORMAT_SUMMARY, UnitTestRunRecord.FORMAT_MANIFEST,
                        UnitTestRunRecord.FORMAT_JUNIT),
                parsed.getFailedTestsSample(), filters, junitXml);
        return new ExecutionResult(record);
    }

    private static void writeConfig(ResolvedConfigurationRuntimeContext context, RunRequest request, Path configPath,
            Path reportPath, Path logPath) throws IOException
    {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("reportFormat", "jUnit"); //$NON-NLS-1$ //$NON-NLS-2$
        config.put("reportPath", reportPath.toAbsolutePath().toString()); //$NON-NLS-1$
        config.put("closeAfterTests", Boolean.TRUE); //$NON-NLS-1$
        config.put("showReport", Boolean.FALSE); //$NON-NLS-1$
        config.put("exitCode", Boolean.TRUE); //$NON-NLS-1$
        if (context != null && context.getProject() != null && context.getProject().getLocation() != null)
        {
            config.put("projectPath", context.getProject().getLocation().toOSString()); //$NON-NLS-1$
        }
        if (ResourcesPlugin.getWorkspace() != null && ResourcesPlugin.getWorkspace().getRoot() != null
                && ResourcesPlugin.getWorkspace().getRoot().getLocation() != null)
        {
            config.put("workspacePath", ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString()); //$NON-NLS-1$
        }
        config.put("logging", Map.of("file", logPath.toAbsolutePath().toString(), "level", "INFO")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Map<String, Object> filter = buildConfigFilter(request);
        if (!filter.isEmpty())
        {
            config.put("filter", filter); //$NON-NLS-1$
        }
        Files.writeString(configPath, GsonProvider.toJson(config));
    }

    private static Map<String, Object> buildConfigFilter(RunRequest request)
    {
        Map<String, Object> filter = new LinkedHashMap<>();
        String testExtension = request.getTestExtension();
        if (testExtension != null && !testExtension.isBlank())
        {
            filter.put("extensions", List.of(testExtension)); //$NON-NLS-1$
        }
        if ("module".equals(request.getScope()) && request.getTestModule() != null && !request.getTestModule().isBlank()) //$NON-NLS-1$
        {
            filter.put("modules", List.of(request.getTestModule())); //$NON-NLS-1$
        }
        if ("test".equals(request.getScope()) && request.getTestPath() != null && !request.getTestPath().isBlank()) //$NON-NLS-1$
        {
            filter.put("tests", List.of(request.getTestPath())); //$NON-NLS-1$
        }
        if ("suite".equals(request.getScope()) && request.getSuiteName() != null && !request.getSuiteName().isBlank()) //$NON-NLS-1$
        {
            filter.put("suites", List.of(request.getSuiteName())); //$NON-NLS-1$
        }
        if (!request.getTagsInclude().isEmpty())
        {
            filter.put("tags", new ArrayList<>(request.getTagsInclude())); //$NON-NLS-1$
        }
        return filter;
    }

    private static Map<String, Object> buildPublicFilters(RunRequest request)
    {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("scope", request.getScope()); //$NON-NLS-1$
        if (request.getTestExtension() != null && !request.getTestExtension().isBlank())
        {
            filters.put("testExtension", request.getTestExtension()); //$NON-NLS-1$
        }
        if (request.getTestModule() != null && !request.getTestModule().isBlank())
        {
            filters.put("testModule", request.getTestModule()); //$NON-NLS-1$
        }
        if (request.getTestPath() != null && !request.getTestPath().isBlank())
        {
            filters.put("testPath", request.getTestPath()); //$NON-NLS-1$
        }
        if (request.getSuiteName() != null && !request.getSuiteName().isBlank())
        {
            filters.put("suiteName", request.getSuiteName()); //$NON-NLS-1$
        }
        if (!request.getTagsInclude().isEmpty())
        {
            filters.put("tagsInclude", request.getTagsInclude()); //$NON-NLS-1$
        }
        return filters;
    }

    private static Object invokeRunClient(ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution,
            IInfobaseApplication infobaseApplication, RuntimeExecutionArguments arguments) throws Exception
    {
        return thickClientResolution.getLauncher().runClient(thickClientResolution.getComponent(),
                infobaseApplication.getInfobase(), arguments);
    }
}
