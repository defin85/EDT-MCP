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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.ResolvedConfigurationRuntimeContext;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;
import com.google.gson.JsonElement;

/**
 * YAxUnit RPC/WebSocket bridge for persistent warm test sessions.
 */
public final class YaxUnitWarmSessionProviderBridge implements UnitTestSessionProviderBridge, AutoCloseable
{
    private static final String RPC_TRANSPORT = "ws"; //$NON-NLS-1$
    private static final String RPC_PROTOCOL_VERSION = "1.0.0"; //$NON-NLS-1$
    private static final String WARMUP_MODULE = "__edt_mcp_warmup__"; //$NON-NLS-1$
    private static final String DEFAULT_TEST_EXTENSION = "tests"; //$NON-NLS-1$
    private static final long PREPARE_TIMEOUT_MS = 60_000L;

    private final ConcurrentMap<String, WarmSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getProvider()
    {
        return YaxUnitRuntimeAdapter.PROVIDER;
    }

    @Override
    public PrepareResult prepare(PrepareRequest request) throws Exception
    {
        String sessionId = sessionId(request);
        if (sessionId == null)
        {
            return PrepareResult.failed(ToolResult.error("sessionId provider parameter is required")); //$NON-NLS-1$
        }

        RuntimeResolution resolution = resolveRuntime(request.getToolName(), request.getTarget());
        if (resolution.failure != null)
        {
            return PrepareResult.failed(resolution.failure);
        }

        YaxUnitRemoteControlServer controlServer = new YaxUnitRemoteControlServer();
        String rpcKey = UUID.randomUUID().toString();
        Path runDirectory = Files.createTempDirectory("edt-mcp-yaxunit-warm-"); //$NON-NLS-1$
        try
        {
            controlServer.start();
            Path configPath = runDirectory.resolve(YaxUnitRuntimeAdapter.CONFIG_FILE_NAME);
            Path reportPath = runDirectory.resolve(YaxUnitRuntimeAdapter.DEFAULT_REPORT_FILE_NAME);
            YaxUnitRuntimeAdapter.RunRequest warmupRequest = new YaxUnitRuntimeAdapter.RunRequest(
                    "warmup-" + sessionId, "module", DEFAULT_TEST_EXTENSION, WARMUP_MODULE, null, null, List.of(), //$NON-NLS-1$ //$NON-NLS-2$
                    (int)(PREPARE_TIMEOUT_MS / 1000L), UnitTestSessionRegistry.DEFAULT_TTL_MS);
            Files.writeString(configPath, YaxUnitRuntimeAdapter.buildConfigJson(warmupRequest, reportPath, false,
                    Map.of("enable", Boolean.TRUE, "transport", RPC_TRANSPORT, "port", controlServer.getPort(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            "key", rpcKey))); //$NON-NLS-1$

            RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();
            arguments.setDisableStartupMessages(true);
            YaxUnitRuntimeAdapter.applyStoredAccessSettings(resolution.infobaseApplication, arguments);
            arguments.setLogFile(runDirectory.resolve("1cv8-warm.log").toFile()); //$NON-NLS-1$
            arguments.setClear(true);
            arguments.setStartupOption("RunUnitTests=" + configPath.toAbsolutePath()); //$NON-NLS-1$

            ConfigurationRuntimeBridgeSupport.InvocationResult<Process> invocation = ConfigurationRuntimeBridgeSupport
                    .invokeWithGuard(request.getToolName(), resolution.context, resolution.application.getId(),
                            PREPARE_TIMEOUT_MS, () -> {
                                Process process = YaxUnitRuntimeAdapter.invokeRunClient(resolution.thickClientResolution,
                                        resolution.infobaseApplication, arguments);
                                controlServer.awaitClient(rpcKey, PREPARE_TIMEOUT_MS);
                                return process;
                            });
            if (!invocation.isSuccess())
            {
                controlServer.close();
                return PrepareResult.failed(invocation.getFailureResult());
            }

            Process process = invocation.getValue();
            UnitTestSessionTarget target = new UnitTestSessionTarget(YaxUnitRuntimeAdapter.PROVIDER,
                    resolution.context.getProjectName(), resolution.application.getId(), resolution.application.getName());
            Instant now = Instant.now();
            UnitTestSessionSnapshot snapshot = new UnitTestSessionSnapshot(sessionId, request.getOwnerSessionId(),
                    target, UnitTestSessionState.READY, null, null, now, now, now,
                    now.plusMillis(UnitTestSessionRegistry.DEFAULT_TTL_MS),
                    providerCorrelation(controlServer.getPort(), process));
            sessions.put(sessionId, new WarmSession(rpcKey, controlServer, process, resolution.context,
                    resolution.application));
            return PrepareResult.prepared(snapshot);
        }
        catch (Exception e)
        {
            controlServer.close();
            throw e;
        }
    }

    @Override
    public ExecuteResult execute(ExecuteRequest request) throws Exception
    {
        UnitTestSessionSnapshot snapshot = request.getCurrentSnapshot();
        if (snapshot == null)
        {
            return ExecuteResult.failed(ToolResult.error("Warm session snapshot is required")); //$NON-NLS-1$
        }
        WarmSession session = sessions.get(snapshot.getSessionId());
        if (session == null)
        {
            return ExecuteResult.failed(ToolResult.error("Warm YAxUnit session is not attached to the provider bridge") //$NON-NLS-1$
                    .put(UnitTestSessionToolContract.FIELD_SESSION_ID, snapshot.getSessionId()));
        }
        if (session.process != null && !session.process.isAlive())
        {
            sessions.remove(snapshot.getSessionId(), session);
            return ExecuteResult.failed(ToolResult.error("Warm YAxUnit Enterprise process is not alive") //$NON-NLS-1$
                    .put(UnitTestSessionToolContract.FIELD_SESSION_ID, snapshot.getSessionId()));
        }

        WarmModuleRequest moduleRequest = buildModuleRequest(session, request.getRunRequest());
        long timeoutMs = Math.max(10_000L, request.getRunRequest().getTimeoutSeconds() * 1000L);
        JsonElement reportData = session.controlServer.runTest(session.rpcKey, moduleRequest.moduleSource,
                moduleRequest.moduleName, moduleRequest.methods, moduleRequest.server, moduleRequest.client,
                moduleRequest.ordinaryClient, timeoutMs);
        String junitXml = YaxUnitRemoteReportConverter.toJUnitXml(reportData);
        UnitTestRunRecord record = YaxUnitRuntimeAdapter.buildRecordFromJunit(junitXml, session.context,
                session.application, request.getRunRequest());
        return ExecuteResult.executed(record, null);
    }

    @Override
    public RecycleResult recycle(RecycleRequest request) throws Exception
    {
        WarmSession session = sessions.remove(request.getSessionId());
        if (session == null)
        {
            return RecycleResult.recycled(UnitTestSessionRecycleOutcome.MARKED_STALE, request.getCurrentSnapshot(),
                    null);
        }
        session.close();
        return RecycleResult.recycled(UnitTestSessionRecycleOutcome.TERMINATED, request.getCurrentSnapshot(), null);
    }

    @Override
    public void close()
    {
        sessions.values().forEach(WarmSession::close);
        sessions.clear();
    }

    private RuntimeResolution resolveRuntime(String toolName, UnitTestSessionTarget target)
    {
        ConfigurationRuntimeContextResolver.Resolution contextResolution = ConfigurationRuntimeContextResolver
                .resolve(toolName, target.getProjectName());
        if (!contextResolution.isResolved())
        {
            return RuntimeResolution.failure(contextResolution.getFailureResult());
        }
        ConfigurationRuntimeContextResolver.ApplicationResolution applicationResolution = ConfigurationRuntimeContextResolver
                .resolveApplication(toolName, contextResolution.getContext(), target.getApplicationId());
        if (!applicationResolution.isResolved())
        {
            return RuntimeResolution.failure(applicationResolution.getFailureResult());
        }
        ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution = ConfigurationRuntimeContextResolver
                .resolveThickClient(toolName, contextResolution.getContext(),
                        applicationResolution.getInfobaseApplication(), target.getApplicationId());
        if (!thickClientResolution.isResolved())
        {
            return RuntimeResolution.failure(thickClientResolution.getFailureResult());
        }
        ToolResult yaxUnitFailure = YaxUnitRuntimeAdapter.preflightYaxUnitAvailability(toolName,
                contextResolution.getContext(), applicationResolution.getInfobaseApplication(),
                target.getApplicationId(), thickClientResolution);
        if (yaxUnitFailure != null)
        {
            return RuntimeResolution.failure(yaxUnitFailure);
        }
        return new RuntimeResolution(contextResolution.getContext(), applicationResolution.getApplication(),
                applicationResolution.getInfobaseApplication(), thickClientResolution, null);
    }

    private WarmModuleRequest buildModuleRequest(WarmSession session, YaxUnitRuntimeAdapter.RunRequest request)
            throws IOException
    {
        String moduleName = moduleName(request);
        if (moduleName == null)
        {
            throw new IOException("Warm YAxUnit execution supports scope=module or scope=test only."); //$NON-NLS-1$
        }
        Path moduleDirectory = projectRoot(session.context.getProject()).resolve("src").resolve("CommonModules") //$NON-NLS-1$ //$NON-NLS-2$
                .resolve(moduleName);
        Path modulePath = moduleDirectory.resolve("Module.bsl"); //$NON-NLS-1$
        if (!Files.isRegularFile(modulePath))
        {
            throw new IOException("Common module source is not available for warm YAxUnit run: " + modulePath); //$NON-NLS-1$
        }
        ModuleFlags flags = readModuleFlags(moduleDirectory, moduleName);
        List<String> methods = "test".equals(request.getScope()) && request.getTestPath() != null //$NON-NLS-1$
                ? List.of(request.getTestPath())
                : List.of();
        return new WarmModuleRequest(moduleName, Files.readString(modulePath), methods, flags.server, flags.client,
                flags.ordinaryClient);
    }

    private static String moduleName(YaxUnitRuntimeAdapter.RunRequest request)
    {
        if ("module".equals(request.getScope()) && request.getTestModule() != null && !request.getTestModule().isBlank()) //$NON-NLS-1$
        {
            return request.getTestModule().trim();
        }
        if (!"test".equals(request.getScope()) || request.getTestPath() == null || request.getTestPath().isBlank()) //$NON-NLS-1$
        {
            return null;
        }
        String testPath = request.getTestPath().trim();
        int separator = firstSeparator(testPath);
        return separator > 0 ? testPath.substring(0, separator) : testPath;
    }

    private static int firstSeparator(String value)
    {
        int result = -1;
        for (String separator : List.of(".", "::", "/", "#")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        {
            int index = value.indexOf(separator);
            if (index > 0 && (result < 0 || index < result))
            {
                result = index;
            }
        }
        return result;
    }

    private static ModuleFlags readModuleFlags(Path moduleDirectory, String moduleName) throws IOException
    {
        Path mdo = moduleDirectory.resolve(moduleName + ".mdo"); //$NON-NLS-1$
        if (!Files.isRegularFile(mdo))
        {
            return new ModuleFlags(true, false, false);
        }
        String content = Files.readString(mdo);
        boolean server = containsTrueTag(content, "server"); //$NON-NLS-1$
        boolean client = containsTrueTag(content, "clientManagedApplication"); //$NON-NLS-1$
        boolean ordinaryClient = containsTrueTag(content, "clientOrdinaryApplication"); //$NON-NLS-1$
        if (!server && !client && !ordinaryClient)
        {
            server = true;
        }
        return new ModuleFlags(server, client, ordinaryClient);
    }

    private static boolean containsTrueTag(String content, String tag)
    {
        return content != null && content.contains("<" + tag + ">true</" + tag + ">"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static Path projectRoot(IProject project) throws IOException
    {
        if (project == null || project.getLocation() == null)
        {
            throw new IOException("EDT project location is not available for warm YAxUnit run"); //$NON-NLS-1$
        }
        return project.getLocation().toFile().toPath();
    }

    private static Map<String, Object> providerCorrelation(int port, Process process)
    {
        return Map.of("transport", RPC_TRANSPORT, "rpcPort", Integer.valueOf(port), "pid", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                Long.valueOf(process != null ? process.pid() : 0L), "protocolVersion", RPC_PROTOCOL_VERSION); //$NON-NLS-1$
    }

    private static String sessionId(PrepareRequest request)
    {
        Object value = request.getProviderParameters().get(UnitTestSessionToolContract.FIELD_SESSION_ID);
        return value != null ? String.valueOf(value) : null;
    }

    private static final class RuntimeResolution
    {
        private final ResolvedConfigurationRuntimeContext context;
        private final IApplication application;
        private final IInfobaseApplication infobaseApplication;
        private final ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution;
        private final ToolResult failure;

        private RuntimeResolution(ResolvedConfigurationRuntimeContext context, IApplication application,
                IInfobaseApplication infobaseApplication,
                ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution, ToolResult failure)
        {
            this.context = context;
            this.application = application;
            this.infobaseApplication = infobaseApplication;
            this.thickClientResolution = thickClientResolution;
            this.failure = failure;
        }

        static RuntimeResolution failure(ToolResult failure)
        {
            return new RuntimeResolution(null, null, null, null, failure);
        }
    }

    private static final class WarmSession
    {
        private final String rpcKey;
        private final YaxUnitRemoteControlServer controlServer;
        private final Process process;
        private final ResolvedConfigurationRuntimeContext context;
        private final IApplication application;

        private WarmSession(String rpcKey, YaxUnitRemoteControlServer controlServer, Process process,
                ResolvedConfigurationRuntimeContext context, IApplication application)
        {
            this.rpcKey = rpcKey;
            this.controlServer = controlServer;
            this.process = process;
            this.context = context;
            this.application = application;
        }

        private void close()
        {
            controlServer.close();
            if (process != null && process.isAlive())
            {
                process.destroy();
            }
        }
    }

    private static final class ModuleFlags
    {
        private final boolean server;
        private final boolean client;
        private final boolean ordinaryClient;

        private ModuleFlags(boolean server, boolean client, boolean ordinaryClient)
        {
            this.server = server;
            this.client = client;
            this.ordinaryClient = ordinaryClient;
        }
    }

    private static final class WarmModuleRequest
    {
        private final String moduleName;
        private final String moduleSource;
        private final List<String> methods;
        private final boolean server;
        private final boolean client;
        private final boolean ordinaryClient;

        private WarmModuleRequest(String moduleName, String moduleSource, List<String> methods, boolean server,
                boolean client, boolean ordinaryClient)
        {
            this.moduleName = moduleName;
            this.moduleSource = moduleSource;
            this.methods = methods;
            this.server = server;
            this.client = client;
            this.ordinaryClient = ordinaryClient;
        }
    }
}
