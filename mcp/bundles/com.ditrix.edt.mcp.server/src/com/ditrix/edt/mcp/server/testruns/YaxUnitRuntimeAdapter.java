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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.impl.ThickClientLauncher;
import com._1c.g5.v8.dt.platform.services.model.InfobaseAccess;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.GsonProvider;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.ReflectionUtils;
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
    static final String SMOKE_EXTENSION_NAME = "Smoke"; //$NON-NLS-1$

    static final String CONFIG_FILE_NAME = "xUnitParams.json"; //$NON-NLS-1$
    static final String DEFAULT_REPORT_FILE_NAME = "junit.xml"; //$NON-NLS-1$

    private static final long REPORT_POLL_INTERVAL_MS = 250L;
    private static final long REPORT_SETTLE_AFTER_EXIT_MS = 2_000L;

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
        Path configPath = runDirectory.resolve(CONFIG_FILE_NAME);
        Path reportPath = runDirectory.resolve(DEFAULT_REPORT_FILE_NAME);
        writeConfig(request, configPath, reportPath);

        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();
        arguments.setDisableStartupMessages(true);
        applyStoredAccessSettings(infobaseApplication, arguments);
        arguments.setLogFile(runDirectory.resolve("1cv8.log").toFile()); //$NON-NLS-1$
        arguments.setClear(true);
        arguments.setStartupOption("RunUnitTests=" + configPath.toAbsolutePath()); //$NON-NLS-1$

        long timeoutMs = Math.max(10_000L, request.getTimeoutSeconds() * 1000L);
        ConfigurationRuntimeBridgeSupport.InvocationResult<Path> invocation = ConfigurationRuntimeBridgeSupport
                .invokeWithGuard(toolName, context, application.getId(), timeoutMs, () -> {
                    Process process = invokeRunClient(thickClientResolution, infobaseApplication, arguments);
                    return waitForJUnitReport(process, runDirectory, reportPath, timeoutMs);
                });
        if (!invocation.isSuccess())
        {
            throw new IOException("YAxUnit launch failed: " + invocation.getFailureResult().toJson()); //$NON-NLS-1$
        }

        String junitXml = Files.readString(invocation.getValue());
        return new ExecutionResult(buildRecordFromJunit(junitXml, context, application, request));
    }

    static String buildConfigJson(RunRequest request, Path reportPath)
    {
        return buildConfigJson(request, reportPath, true, null);
    }

    static String buildConfigJson(RunRequest request, Path reportPath, boolean closeAfterTests,
            Map<String, Object> rpc)
    {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("reportFormat", "jUnit"); //$NON-NLS-1$ //$NON-NLS-2$
        config.put("reportPath", reportPath.toAbsolutePath().toString()); //$NON-NLS-1$
        config.put("closeAfterTests", Boolean.valueOf(closeAfterTests)); //$NON-NLS-1$
        config.put("showReport", Boolean.FALSE); //$NON-NLS-1$
        if (rpc != null && !rpc.isEmpty())
        {
            config.put("rpc", new LinkedHashMap<>(rpc)); //$NON-NLS-1$
        }
        if (isSmokeExtension(request.getTestExtension()))
        {
            config.put("ДымовыеТесты", buildSmokeSettings()); //$NON-NLS-1$
        }
        Map<String, Object> filter = buildConfigFilter(request);
        if (!filter.isEmpty())
        {
            config.put("filter", filter); //$NON-NLS-1$
        }
        return GsonProvider.toJson(config);
    }

    static UnitTestRunRecord buildRecordFromJunit(String junitXml, ResolvedConfigurationRuntimeContext context,
            IApplication application, RunRequest request) throws IOException
    {
        JUnitReportParser.ParsedJUnitReport parsed = JUnitReportParser.parse(junitXml);
        Instant completedAt = Instant.now();
        Instant expiresAt = completedAt.plusMillis(request.getRetentionTtlMs());
        Map<String, Object> filters = buildPublicFilters(request);
        return new UnitTestRunRecord(request.getRunId(), PROVIDER, context.getProjectName(), application.getId(),
                application.getName(), request.getScope(), parsed.getStatus(),
                parsed.getStatus().equals("passed") ? "All tests passed" : "One or more unit tests failed", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                parsed.getTotal(), parsed.getPassed(), parsed.getFailed(), parsed.getSkipped(), parsed.getErrored(),
                parsed.getDurationMs(), completedAt, expiresAt,
                List.of(UnitTestRunRecord.FORMAT_SUMMARY, UnitTestRunRecord.FORMAT_MANIFEST,
                        UnitTestRunRecord.FORMAT_JUNIT),
                parsed.getFailedTestsSample(), filters, junitXml);
    }

    static boolean isSmokeExtension(String testExtension)
    {
        return testExtension != null && SMOKE_EXTENSION_NAME.equalsIgnoreCase(testExtension.trim());
    }

    private static Map<String, Object> buildSmokeSettings()
    {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("Использовать", Boolean.TRUE); //$NON-NLS-1$
        settings.put("ОткрытиеФорм", Boolean.TRUE); //$NON-NLS-1$
        return settings;
    }

    private static void writeConfig(RunRequest request, Path configPath, Path reportPath) throws IOException
    {
        Files.writeString(configPath, buildConfigJson(request, reportPath));
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

    static Map<String, Object> buildPublicFilters(RunRequest request)
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

    static Path resolveProducedReportPath(Path runDirectory, Path expectedReportPath) throws IOException
    {
        if (expectedReportPath != null && Files.isRegularFile(expectedReportPath) && Files.size(expectedReportPath) > 0)
        {
            return expectedReportPath;
        }
        if (runDirectory == null || !Files.exists(runDirectory))
        {
            return null;
        }

        String[] candidates = {DEFAULT_REPORT_FILE_NAME, "report.xml", "test-report.xml"}; //$NON-NLS-1$ //$NON-NLS-2$
        for (String candidate : candidates)
        {
            Path candidatePath = runDirectory.resolve(candidate);
            if (expectedReportPath != null && candidatePath.equals(expectedReportPath))
            {
                continue;
            }
            if (Files.isRegularFile(candidatePath) && Files.size(candidatePath) > 0)
            {
                return candidatePath;
            }
        }

        try (var paths = Files.list(runDirectory))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml")) //$NON-NLS-1$
                    .filter(path -> {
                        try
                        {
                            return Files.size(path) > 0;
                        }
                        catch (IOException e)
                        {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path waitForJUnitReport(Process process, Path runDirectory, Path expectedReportPath, long timeoutMs)
            throws Exception
    {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 10_000L);
        Long exitObservedAt = null;
        Integer exitCode = null;

        while (System.currentTimeMillis() <= deadline)
        {
            Path reportPath = resolveProducedReportPath(runDirectory, expectedReportPath);
            if (reportPath != null)
            {
                return reportPath;
            }

            if (process != null && !process.isAlive())
            {
                if (exitObservedAt == null)
                {
                    exitObservedAt = Long.valueOf(System.currentTimeMillis());
                    exitCode = Integer.valueOf(process.exitValue());
                }
                if (System.currentTimeMillis() - exitObservedAt.longValue() >= REPORT_SETTLE_AFTER_EXIT_MS)
                {
                    break;
                }
            }

            try
            {
                TimeUnit.MILLISECONDS.sleep(REPORT_POLL_INTERVAL_MS);
            }
            catch (InterruptedException e)
            {
                if (process != null)
                {
                    process.destroy();
                    process.destroyForcibly();
                }
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        if (process != null && process.isAlive())
        {
            process.destroy();
            process.destroyForcibly();
            throw new IOException("YAxUnit process did not produce a JUnit report within " + timeoutMs //$NON-NLS-1$
                    + " ms. Expected " + expectedReportPath.toAbsolutePath()); //$NON-NLS-1$
        }

        String exitCodeSuffix = exitCode != null ? " (exitCode=" + exitCode + ")" : ""; //$NON-NLS-1$ //$NON-NLS-2$
        throw new IOException("YAxUnit run did not produce a readable JUnit report in " //$NON-NLS-1$
                + runDirectory.toAbsolutePath() + exitCodeSuffix + ". Expected " //$NON-NLS-1$
                + expectedReportPath.toAbsolutePath());
    }

    static Process invokeRunClient(ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution,
            IInfobaseApplication infobaseApplication, RuntimeExecutionArguments arguments) throws Exception
    {
        ThickClientLauncher directLauncher = resolveDirectThickClientLauncher(thickClientResolution);
        Activator.logInfo("Launching YAxUnit through managed direct ThickClientLauncher; resolved launcher=" //$NON-NLS-1$
                + thickClientResolution.getLauncher().getClass().getName() + ", directLauncher=" //$NON-NLS-1$
                + directLauncher.getClass().getName() + ", component=" //$NON-NLS-1$
                + thickClientResolution.getComponent().getClass().getName());
        Process process = directLauncher.runClient(thickClientResolution.getComponent(),
                infobaseApplication.getInfobase(), arguments);
        Activator.logInfo("YAxUnit thick client process started: pid=" + process.pid()); //$NON-NLS-1$
        return process;
    }

    private static ThickClientLauncher resolveDirectThickClientLauncher(
            ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution) throws Exception
    {
        if (thickClientResolution.getLauncher() instanceof ThickClientLauncher directLauncher)
        {
            return directLauncher;
        }

        Object delegate = ReflectionUtils.getFieldValue(thickClientResolution.getLauncher(), "thickClientLauncher"); //$NON-NLS-1$
        if (delegate instanceof ThickClientLauncher directLauncher)
        {
            return directLauncher;
        }

        throw new IOException("EDT managed direct ThickClientLauncher delegate is not available from " //$NON-NLS-1$
                + thickClientResolution.getLauncher().getClass().getName());
    }

    static void applyStoredAccessSettings(IInfobaseApplication infobaseApplication,
            RuntimeExecutionArguments arguments) throws Exception
    {
        if (infobaseApplication == null || arguments == null)
        {
            return;
        }

        IInfobaseAccessManager accessManager = Activator.getDefault() != null
                ? Activator.getDefault().getInfobaseAccessManager()
                : null;
        if (accessManager == null)
        {
            return;
        }

        IInfobaseAccessSettings settings = accessManager.getSettings(infobaseApplication.getInfobase());
        applyAccessSettings(arguments, settings);
        if (settings != null && !Objects.equals(settings, IInfobaseAccessSettings.NOT_DEFINED) && settings.access() != null)
        {
            Activator.logInfo("Hydrated YAxUnit runtime access settings: access=" + settings.access() //$NON-NLS-1$
                    + ", hasUsername=" + (settings.userName() != null && !settings.userName().isBlank())); //$NON-NLS-1$
        }
    }

    static void applyAccessSettings(RuntimeExecutionArguments arguments, IInfobaseAccessSettings settings)
    {
        if (arguments == null || settings == null || Objects.equals(settings, IInfobaseAccessSettings.NOT_DEFINED))
        {
            return;
        }

        InfobaseAccess access = settings.access();
        if (access != null)
        {
            arguments.setAccess(access);
        }
        if (InfobaseAccess.INFOBASE.equals(access))
        {
            arguments.setUsername(settings.userName());
            arguments.setPassword(settings.password());
        }
    }
}
