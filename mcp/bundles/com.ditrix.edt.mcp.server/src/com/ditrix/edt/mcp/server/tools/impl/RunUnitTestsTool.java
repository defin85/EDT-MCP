/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;

import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseEqualityState;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseSynchronizationState;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tasks.TaskCancellationToken;
import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionMode;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionProviderBridge;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRecycleOutcome;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRegistry;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRunOutcome;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionRunPolicy;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionSnapshot;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionStaleReason;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionState;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionTarget;
import com.ditrix.edt.mcp.server.testruns.UnitTestSessionToolContract;
import com.ditrix.edt.mcp.server.testruns.UnitTestRunRecord;
import com.ditrix.edt.mcp.server.testruns.UnitTestRunStore;
import com.ditrix.edt.mcp.server.testruns.YaxUnitRuntimeAdapter;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.InfobaseSyncUtils;
import com.ditrix.edt.mcp.server.utils.ProjectCapabilityFailure;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;

/**
 * Async-first YAxUnit-backed unit-test execution for configuration projects.
 */
public class RunUnitTestsTool implements IMcpTool
{
    public static final String NAME = "run_unit_tests"; //$NON-NLS-1$

    private static final String STAGE_VALIDATION = "validation"; //$NON-NLS-1$
    private static final String STAGE_PREFLIGHT = "preflight"; //$NON-NLS-1$
    private static final String STAGE_OPTIONAL_UPDATE = "optional_update"; //$NON-NLS-1$
    private static final String STAGE_PREPARE_PROVIDER = "prepare_provider"; //$NON-NLS-1$
    private static final String STAGE_LAUNCH = "launch"; //$NON-NLS-1$
    private static final String STAGE_PARSE_REPORT = "parse_report"; //$NON-NLS-1$
    private static final String STAGE_COMPLETION = "completion"; //$NON-NLS-1$
    private static final String STAGE_FAILURE = "failure"; //$NON-NLS-1$

    private static final String DEFAULT_SCOPE = "all"; //$NON-NLS-1$
    private static final String DEFAULT_TEST_EXTENSION = "tests"; //$NON-NLS-1$
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Run YAxUnit-backed unit tests for a configuration project and application target. " //$NON-NLS-1$
                + "Async-first at runtime: bare calls auto-promote into task-backed execution, " //$NON-NLS-1$
                + "and final report retrieval is available via get_test_run_report."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT configuration project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringEnumProperty("provider", "Supported unit-test provider (default: yaxunit).", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of(YaxUnitRuntimeAdapter.PROVIDER))
                .stringEnumProperty(UnitTestSessionToolContract.FIELD_SESSION_MODE,
                        "Warm-session policy: cold, prefer_warm, require_warm, or recycle_then_run (default: cold).", //$NON-NLS-1$
                        supportedSessionModes())
                .stringEnumProperty("scope", "Execution scope: all, module, suite, or test (default: all).", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of("all", "module", "suite", "test")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .stringProperty("testExtension", "Test extension/filter root for YAxUnit (default: tests).") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("testModule", "YAxUnit test module name for scope=module.") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("testPath", "YAxUnit test identifier/path for scope=test.") //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("suiteName", "YAxUnit suite name for scope=suite.") //$NON-NLS-1$ //$NON-NLS-2$
                .stringArrayProperty("tagsInclude", "Optional YAxUnit tags filter.") //$NON-NLS-1$ //$NON-NLS-2$
                .stringArrayProperty("tagsExclude", "Explicitly unsupported in the first rollout; request fails closed if present.") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("updateBeforeRun", "If true, perform incremental infobase update before test execution.") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Bounded runtime wait before the test run fails closed (default: 300).") //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public TaskSupport getTaskSupport()
    {
        return TaskSupport.OPTIONAL;
    }

    @Override
    public TaskSchedulingKey getTaskSchedulingKey(Map<String, String> params)
    {
        return TaskSchedulingKey.projectScoped(JsonUtils.extractStringArgument(params, "projectName")); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        String provider = normalizeProvider(JsonUtils.extractStringArgument(params, "provider")); //$NON-NLS-1$
        UnitTestSessionMode sessionMode = UnitTestSessionMode
                .fromWireValue(JsonUtils.extractStringArgument(params, UnitTestSessionToolContract.FIELD_SESSION_MODE));
        String scope = normalizeScope(JsonUtils.extractStringArgument(params, "scope")); //$NON-NLS-1$
        String testExtension = normalizeTestExtension(JsonUtils.extractStringArgument(params, "testExtension")); //$NON-NLS-1$
        String testModule = JsonUtils.extractStringArgument(params, "testModule"); //$NON-NLS-1$
        String testPath = JsonUtils.extractStringArgument(params, "testPath"); //$NON-NLS-1$
        String suiteName = JsonUtils.extractStringArgument(params, "suiteName"); //$NON-NLS-1$
        List<String> tagsInclude = JsonUtils.extractArrayArgument(params, "tagsInclude"); //$NON-NLS-1$
        List<String> tagsExclude = JsonUtils.extractArrayArgument(params, "tagsExclude"); //$NON-NLS-1$
        boolean updateBeforeRun = JsonUtils.extractBooleanArgument(params, "updateBeforeRun", false); //$NON-NLS-1$
        int timeoutSeconds = JsonUtils.extractIntArgument(params, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS); //$NON-NLS-1$
        String runId = UUID.randomUUID().toString();

        if (projectName == null || projectName.isBlank())
        {
            return ToolResult.error("projectName is required").put("runId", runId).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (applicationId == null || applicationId.isBlank())
        {
            return ToolResult.error("applicationId is required. Use get_applications first.").put("runId", runId) //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
        if (!YaxUnitRuntimeAdapter.PROVIDER.equals(provider))
        {
            return ToolResult.error("Unsupported provider: " + provider + ". The first rollout supports yaxunit only.") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("runId", runId) //$NON-NLS-1$
                    .put("provider", provider) //$NON-NLS-1$
                    .put("supportedProviders", List.of(YaxUnitRuntimeAdapter.PROVIDER)) //$NON-NLS-1$
                    .toJson();
        }
        if (sessionMode == null)
        {
            return ToolResult.error("Unsupported sessionMode. Supported values: " + supportedSessionModes()) //$NON-NLS-1$
                    .put("runId", runId) //$NON-NLS-1$
                    .put("supportedSessionModes", supportedSessionModes()) //$NON-NLS-1$
                    .toJson();
        }
        if (tagsExclude != null && !tagsExclude.isEmpty())
        {
            return ToolResult.error("tagsExclude is not supported in the first yaxunit rollout. The request failed closed.") //$NON-NLS-1$
                    .put("runId", runId) //$NON-NLS-1$
                    .put("provider", provider) //$NON-NLS-1$
                    .toJson();
        }
        if ("module".equals(scope) && (testModule == null || testModule.isBlank())) //$NON-NLS-1$
        {
            return ToolResult.error("testModule is required when scope=module").put("runId", runId).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if ("test".equals(scope) && (testPath == null || testPath.isBlank())) //$NON-NLS-1$
        {
            return ToolResult.error("testPath is required when scope=test").put("runId", runId).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if ("suite".equals(scope) && (suiteName == null || suiteName.isBlank())) //$NON-NLS-1$
        {
            return ToolResult.error("suiteName is required when scope=suite").put("runId", runId).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
        }

        ProjectCapabilityFailure.ValidationResult validation = ProjectCapabilityFailure
                .requireConfigurationProject(projectName, NAME,
                        "Extension-project targets, BDD/scenario flow, and debug-mode execution stay out of scope for this rollout."); //$NON-NLS-1$
        if (validation.hasFailure())
        {
            return validation.getFailure().toJson();
        }

        ToolResult notReadyResult = ProjectStateChecker.checkReadyOrErrorResult(projectName);
        if (notReadyResult != null)
        {
            return notReadyResult.put("runId", runId).toJson(); //$NON-NLS-1$
        }

        return runUnitTests(runId, projectName, applicationId, sessionMode, scope, testExtension, testModule, testPath,
                suiteName, tagsInclude != null ? tagsInclude : List.of(), updateBeforeRun, timeoutSeconds);
    }

    private String runUnitTests(String runId, String projectName, String applicationId, UnitTestSessionMode sessionMode,
            String scope, String testExtension, String testModule, String testPath, String suiteName,
            List<String> tagsInclude, boolean updateBeforeRun, int timeoutSeconds)
    {
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        if (server == null)
        {
            return ToolResult.error("MCP server is not available").put("runId", runId).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
        }
        ToolExecutionContext context = ToolExecutionContextHolder.get();
        OperationProgressReporter reporter = createProgressReporter(context, runId, projectName, applicationId, scope);
        TaskCancellationToken cancellationToken = context != null ? context.getCancellationToken() : null;
        registerActiveOperation(server, reporter, context);

        try
        {
            if (isCancelled(cancellationToken))
            {
                return cancelled(reporter, runId);
            }

            reporter.stage(STAGE_PREFLIGHT, "Resolving configuration runtime context"); //$NON-NLS-1$
            ConfigurationRuntimeContextResolver.Resolution contextResolution = ConfigurationRuntimeContextResolver
                    .resolve(NAME, projectName);
            if (!contextResolution.isResolved())
            {
                return fail(reporter, runId, contextResolution.getFailureResult());
            }
            ConfigurationRuntimeContextResolver.ApplicationResolution applicationResolution = ConfigurationRuntimeContextResolver
                    .resolveApplication(NAME, contextResolution.getContext(), applicationId);
            if (!applicationResolution.isResolved())
            {
                return fail(reporter, runId, applicationResolution.getFailureResult());
            }
            ConfigurationRuntimeContextResolver.ThickClientResolution thickClientResolution = ConfigurationRuntimeContextResolver
                    .resolveThickClient(NAME, contextResolution.getContext(),
                            applicationResolution.getInfobaseApplication(), applicationId);
            if (!thickClientResolution.isResolved())
            {
                return fail(reporter, runId, thickClientResolution.getFailureResult());
            }

            ToolResult accessSettingsFailure = ConfigurationRuntimeBridgeSupport.preflightAccessSettings(NAME,
                    contextResolution.getContext(), applicationResolution.getInfobaseApplication(), applicationId);
            if (accessSettingsFailure != null)
            {
                return fail(reporter, runId, accessSettingsFailure);
            }

            ToolResult yaxUnitFailure = YaxUnitRuntimeAdapter.preflightYaxUnitAvailability(NAME,
                    contextResolution.getContext(), applicationResolution.getInfobaseApplication(), applicationId,
                    thickClientResolution);
            if (yaxUnitFailure != null)
            {
                return fail(reporter, runId, yaxUnitFailure);
            }

            UnitTestSessionTarget sessionTarget = new UnitTestSessionTarget(YaxUnitRuntimeAdapter.PROVIDER,
                    projectName, applicationId, applicationResolution.getApplication().getName());

            if (isCancelled(cancellationToken))
            {
                return cancelled(reporter, runId);
            }

            if (updateBeforeRun)
            {
                reporter.stage(STAGE_OPTIONAL_UPDATE, "Updating infobase before test execution"); //$NON-NLS-1$
                ToolResult updateFailure = updateBeforeRun(contextResolution.getContext().getProject(), applicationId,
                        applicationResolution.getInfobaseApplication(), reporter);
                if (updateFailure != null)
                {
                    return fail(reporter, runId, updateFailure);
                }
                server.getUnitTestSessionRegistry().invalidateTarget(sessionTarget,
                        UnitTestSessionStaleReason.INFOBASE_SYNC_PERFORMED);
            }

            reporter.stage(STAGE_PREPARE_PROVIDER, "Preparing YAxUnit configuration"); //$NON-NLS-1$
            YaxUnitRuntimeAdapter.RunRequest request = new YaxUnitRuntimeAdapter.RunRequest(runId, scope,
                    testExtension, testModule, testPath, suiteName, tagsInclude, timeoutSeconds,
                    UnitTestRunStore.DEFAULT_TTL_MS);

            if (isCancelled(cancellationToken))
            {
                return cancelled(reporter, runId);
            }

            UnitTestSessionProviderBridge bridge = server.getUnitTestSessionProviderBridgeRegistry()
                    .get(YaxUnitRuntimeAdapter.PROVIDER);
            UnitTestSessionSnapshot matchingSession = server.getUnitTestSessionRegistry()
                    .findLatestForTarget(currentOwnerSessionId(), sessionTarget);
            UnitTestSessionRunPolicy.Decision decision = UnitTestSessionRunPolicy.decide(sessionMode, matchingSession,
                    bridge != null, Instant.now());
            String warmFallbackMessage = null;
            if (UnitTestSessionRunPolicy.Route.REJECT.equals(decision.getRoute()))
            {
                return fail(reporter, runId, warmRejected(runId, sessionMode, decision.getMatchingSession(),
                        "No reusable warm unit-test session is available for this target.")); //$NON-NLS-1$
            }
            if (UnitTestSessionRunPolicy.Route.RECYCLE_THEN_COLD.equals(decision.getRoute()))
            {
                ToolResult recycleFailure = recycleBeforeRun(server, bridge, decision.getMatchingSession());
                if (recycleFailure != null)
                {
                    return fail(reporter, runId, recycleFailure
                            .put(UnitTestSessionToolContract.FIELD_SESSION_MODE, sessionMode.wireValue())
                            .put(UnitTestSessionToolContract.FIELD_SESSION_OUTCOME,
                                    UnitTestSessionRunOutcome.STALE_REJECTED.wireValue()));
                }
            }
            if (UnitTestSessionRunPolicy.Route.WARM.equals(decision.getRoute()))
            {
                WarmExecutionAttempt warmAttempt = executeWarmSession(server, bridge, decision.getMatchingSession(),
                        request);
                if (warmAttempt.isSuccess())
                {
                    reporter.stage(STAGE_PARSE_REPORT, "Retaining warm-session unit-test report"); //$NON-NLS-1$
                    server.getUnitTestRunStore().put(warmAttempt.getRecord());

                    reporter.stage(STAGE_COMPLETION, "Warm unit-test run completed"); //$NON-NLS-1$
                    reporter.completed("Warm unit-test run completed"); //$NON-NLS-1$
                    return summaryResult(warmAttempt.getRecord(), sessionMode, UnitTestSessionRunOutcome.REUSED,
                            warmAttempt.getSessionSnapshot(), null).toJson();
                }
                if (sessionMode.requiresReusableWarmSession())
                {
                    return fail(reporter, runId, warmRejected(runId, sessionMode, warmAttempt.getSessionSnapshot(),
                            warmAttempt.getMessage()));
                }
                matchingSession = warmAttempt.getSessionSnapshot();
                warmFallbackMessage = warmAttempt.getMessage();
            }

            reporter.stage(STAGE_LAUNCH, "Launching YAxUnit via EDT runtime bridge"); //$NON-NLS-1$
            YaxUnitRuntimeAdapter.ExecutionResult executionResult = YaxUnitRuntimeAdapter.execute(NAME,
                    contextResolution.getContext(), applicationResolution.getApplication(),
                    applicationResolution.getInfobaseApplication(), thickClientResolution, request);

            reporter.stage(STAGE_PARSE_REPORT, "Parsing retained JUnit report"); //$NON-NLS-1$
            server.getUnitTestRunStore().put(executionResult.getRecord());

            reporter.stage(STAGE_COMPLETION, "Unit-test run completed"); //$NON-NLS-1$
            reporter.completed("Unit-test run completed"); //$NON-NLS-1$
            UnitTestSessionRunOutcome outcome = UnitTestSessionMode.RECYCLE_THEN_RUN.equals(sessionMode)
                    ? UnitTestSessionRunOutcome.RECYCLED
                    : UnitTestSessionRunOutcome.COLD_STARTED;
            return summaryResult(executionResult.getRecord(), sessionMode, outcome, matchingSession, warmFallbackMessage)
                    .toJson();
        }
        catch (Exception e)
        {
            Activator.logError("run_unit_tests failed", e); //$NON-NLS-1$
            return fail(reporter, runId, ToolResult.error("Failed to execute unit tests: " + e.getMessage())); //$NON-NLS-1$
        }
        finally
        {
            clearActiveOperation(server, context);
        }
    }

    private WarmExecutionAttempt executeWarmSession(McpServer server, UnitTestSessionProviderBridge bridge,
            UnitTestSessionSnapshot session, YaxUnitRuntimeAdapter.RunRequest request)
    {
        if (server == null || bridge == null || session == null)
        {
            return WarmExecutionAttempt.failed(session, "No provider bridge is registered for warm unit-test execution."); //$NON-NLS-1$
        }

        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        UnitTestSessionRegistry.BusyAcquisition acquisition = registry.tryAcquireBusy(session.getSessionId(),
                request.getRunId());
        if (!acquisition.isAcquired())
        {
            UnitTestSessionSnapshot current = acquisition.getSnapshot() != null ? acquisition.getSnapshot() : session;
            return WarmExecutionAttempt.failed(current,
                    "Warm unit-test session is not ready for reuse: " + current.getState().wireValue()); //$NON-NLS-1$
        }

        UnitTestSessionSnapshot busySnapshot = acquisition.getSnapshot();
        try
        {
            UnitTestSessionProviderBridge.ExecuteResult result = bridge.execute(
                    new UnitTestSessionProviderBridge.ExecuteRequest(NAME, busySnapshot, request, Map.of()));
            if (result == null)
            {
                UnitTestSessionSnapshot stale = registry.markStale(session.getSessionId(),
                        UnitTestSessionStaleReason.PROVIDER_ERROR);
                return WarmExecutionAttempt.failed(stale, "Provider bridge returned no warm execution result"); //$NON-NLS-1$
            }
            if (!result.isSuccess())
            {
                UnitTestSessionSnapshot stale = registry.markStale(session.getSessionId(),
                        UnitTestSessionStaleReason.PROVIDER_ERROR);
                String message = result.getFailureResult() != null ? result.getFailureResult().toJson()
                        : "Provider bridge failed warm unit-test execution"; //$NON-NLS-1$
                return WarmExecutionAttempt.failed(stale, message);
            }
            UnitTestSessionSnapshot updated = result.getUpdatedSnapshot();
            if (updated != null)
            {
                registry.put(updated);
            }
            UnitTestSessionSnapshot ready = registry.releaseBusy(session.getSessionId(), request.getRunId());
            return WarmExecutionAttempt.executed(result.getRecord(), ready != null ? ready : updated);
        }
        catch (Exception e)
        {
            Activator.logError("Warm run_unit_tests execution failed", e); //$NON-NLS-1$
            UnitTestSessionSnapshot stale = registry.markStale(session.getSessionId(),
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return WarmExecutionAttempt.failed(stale, "Failed to execute warm unit-test run: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    private ToolResult recycleBeforeRun(McpServer server, UnitTestSessionProviderBridge bridge,
            UnitTestSessionSnapshot matchingSession)
    {
        if (server == null || matchingSession == null)
        {
            return null;
        }
        UnitTestSessionRegistry registry = server.getUnitTestSessionRegistry();
        if (bridge == null)
        {
            markStaleWithoutDowngrade(registry, matchingSession, UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
            return null;
        }
        try
        {
            UnitTestSessionProviderBridge.RecycleResult result = bridge.recycle(
                    new UnitTestSessionProviderBridge.RecycleRequest(NAME, matchingSession.getSessionId(),
                            matchingSession));
            if (result == null)
            {
                UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, matchingSession,
                        UnitTestSessionStaleReason.PROVIDER_ERROR);
                return ToolResult.error("Provider bridge returned no recycle result").put("session", //$NON-NLS-1$ //$NON-NLS-2$
                        stale != null ? stale.toPublicMap() : null);
            }
            if (!result.isSuccess())
            {
                UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, matchingSession,
                        UnitTestSessionStaleReason.PROVIDER_ERROR);
                ToolResult failure = result.getFailureResult() != null ? result.getFailureResult()
                        : ToolResult.error("Provider bridge failed to recycle test session"); //$NON-NLS-1$
                return failure.put("session", stale != null ? stale.toPublicMap() : null); //$NON-NLS-1$
            }
            applyRecycleOutcome(registry, matchingSession, result.getOutcome(), result.getReplacementSnapshot());
            return null;
        }
        catch (Exception e)
        {
            UnitTestSessionSnapshot stale = markStaleWithoutDowngrade(registry, matchingSession,
                    UnitTestSessionStaleReason.PROVIDER_ERROR);
            return ToolResult.error("Failed to recycle warm unit-test session before run: " + e.getMessage()) //$NON-NLS-1$
                    .put("session", stale != null ? stale.toPublicMap() : null); //$NON-NLS-1$
        }
    }

    private static UnitTestSessionSnapshot applyRecycleOutcome(UnitTestSessionRegistry registry,
            UnitTestSessionSnapshot current, UnitTestSessionRecycleOutcome outcome, UnitTestSessionSnapshot replacement)
    {
        if (UnitTestSessionRecycleOutcome.TERMINATED.equals(outcome))
        {
            return registry.markDead(current.getSessionId(), UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
        }
        if (UnitTestSessionRecycleOutcome.REPLACED.equals(outcome))
        {
            UnitTestSessionSnapshot old = markStaleWithoutDowngrade(registry, current,
                    UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
            if (replacement != null)
            {
                registry.put(replacement);
            }
            return old;
        }
        if (UnitTestSessionRecycleOutcome.MARKED_STALE.equals(outcome))
        {
            return markStaleWithoutDowngrade(registry, current, UnitTestSessionStaleReason.EXPLICIT_RECYCLE);
        }
        return markStaleWithoutDowngrade(registry, current, UnitTestSessionStaleReason.PROVIDER_ERROR);
    }

    private static UnitTestSessionSnapshot markStaleWithoutDowngrade(UnitTestSessionRegistry registry,
            UnitTestSessionSnapshot current, UnitTestSessionStaleReason reason)
    {
        if (UnitTestSessionState.DEAD.equals(current.getState()))
        {
            return current;
        }
        return registry.markStale(current.getSessionId(), reason);
    }

    private static ToolResult summaryResult(UnitTestRunRecord record, UnitTestSessionMode mode,
            UnitTestSessionRunOutcome outcome, UnitTestSessionSnapshot session, String warmFallbackMessage)
    {
        ToolResult result = record.toSummaryResult()
                .put("provider", YaxUnitRuntimeAdapter.PROVIDER) //$NON-NLS-1$
                .put(UnitTestSessionToolContract.FIELD_SESSION_MODE, mode.wireValue())
                .put(UnitTestSessionToolContract.FIELD_SESSION_OUTCOME, outcome.wireValue());
        if (session != null)
        {
            result.put(UnitTestSessionToolContract.FIELD_SESSION_ID, session.getSessionId());
            result.put("session", session.toPublicMap()); //$NON-NLS-1$
        }
        if (warmFallbackMessage != null && !warmFallbackMessage.isBlank())
        {
            result.put("warmFallbackMessage", warmFallbackMessage); //$NON-NLS-1$
        }
        return result;
    }

    private static ToolResult warmRejected(String runId, UnitTestSessionMode mode, UnitTestSessionSnapshot session,
            String message)
    {
        ToolResult result = ToolResult.error(message)
                .put("runId", runId) //$NON-NLS-1$
                .put(UnitTestSessionToolContract.FIELD_SESSION_MODE, mode.wireValue())
                .put(UnitTestSessionToolContract.FIELD_SESSION_OUTCOME,
                        UnitTestSessionRunOutcome.STALE_REJECTED.wireValue());
        if (session != null)
        {
            result.put(UnitTestSessionToolContract.FIELD_SESSION_ID, session.getSessionId());
            result.put("session", session.toPublicMap()); //$NON-NLS-1$
        }
        return result;
    }

    private ToolResult updateBeforeRun(IProject project, String applicationId,
            com.e1c.g5.dt.applications.infobases.IInfobaseApplication infobaseApplication,
            OperationProgressReporter reporter)
    {
        IInfobaseSynchronizationManager synchronizationManager = Activator.getDefault() != null
                ? Activator.getDefault().getInfobaseSynchronizationManager()
                : null;
        if (synchronizationManager == null)
        {
            return ToolResult.error("IInfobaseSynchronizationManager service is not available"); //$NON-NLS-1$
        }

        try
        {
            InfobaseSynchronizationState synchronizationState = synchronizationManager.getSynchronizationState(project,
                    infobaseApplication.getInfobase());
            InfobaseEqualityState equalityState = synchronizationManager.getEqualityState(project,
                    infobaseApplication.getInfobase());
            String updateState = InfobaseSyncUtils.deriveUpdateState(synchronizationState, equalityState);
            if ("BEING_UPDATED".equals(updateState)) //$NON-NLS-1$
            {
                return ToolResult.error("Application synchronization is already in progress for application " //$NON-NLS-1$
                        + applicationId + "."); //$NON-NLS-1$
            }
            if ("UPDATED".equals(updateState)) //$NON-NLS-1$
            {
                reporter.indeterminate(STAGE_OPTIONAL_UPDATE, "Infobase is already updated; skipping pre-run update"); //$NON-NLS-1$
                return null;
            }
            String invalidModeMessage = InfobaseSyncUtils.validateRequestedUpdateMode(false, updateState);
            if (invalidModeMessage != null)
            {
                return ToolResult.error(invalidModeMessage);
            }

            boolean updated = synchronizationManager.updateInfobase(project, infobaseApplication.getInfobase(),
                    InfobaseSyncUtils.createUpdateCallback(true, reporter), true, new NullProgressMonitor());
            if (!updated)
            {
                return ToolResult.error("EDT did not confirm a successful pre-run infobase update."); //$NON-NLS-1$
            }
            return null;
        }
        catch (Exception e)
        {
            Activator.logError("Failed to update infobase before run_unit_tests", e); //$NON-NLS-1$
            return ToolResult.error("Failed to update infobase before unit-test execution: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    private OperationProgressReporter createProgressReporter(ToolExecutionContext context, String runId,
            String projectName, String applicationId, String scope)
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        reporter.start(context != null ? context.getOperationId() : null, NAME, STAGE_VALIDATION,
                "Preparing YAxUnit unit-test execution", context != null ? context.getRequestId() : null, //$NON-NLS-1$
                context != null ? context.getSessionId() : null, context != null ? context.getProgressToken() : null,
                Map.of("runId", runId, "projectName", projectName, "applicationId", applicationId, "provider", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        YaxUnitRuntimeAdapter.PROVIDER, "scope", scope)); //$NON-NLS-1$
        return reporter;
    }

    private void registerActiveOperation(McpServer server, OperationProgressReporter reporter, ToolExecutionContext context)
    {
        if (server == null)
        {
            return;
        }
        if (context != null && context.getOperationId() != null)
        {
            reporter.appendStateListener(state -> server.getTaskRegistry().updateProgress(context.getOperationId(), state));
        }
        server.setActiveOperation(reporter);
    }

    private void clearActiveOperation(McpServer server, ToolExecutionContext context)
    {
        if (server != null)
        {
            server.clearActiveOperation(context != null ? context.getOperationId() : null);
        }
    }

    private boolean isCancelled(TaskCancellationToken cancellationToken)
    {
        return (cancellationToken != null && cancellationToken.isCancellationRequested())
                || Thread.currentThread().isInterrupted();
    }

    private String cancelled(OperationProgressReporter reporter, String runId)
    {
        String message = "Unit-test execution cancelled"; //$NON-NLS-1$
        reporter.stage(STAGE_FAILURE, message);
        reporter.cancelled(message);
        return ToolResult.error(message).put("runId", runId).toJson(); //$NON-NLS-1$
    }

    private String fail(OperationProgressReporter reporter, String runId, ToolResult failure)
    {
        reporter.stage(STAGE_FAILURE, "Unit-test execution failed"); //$NON-NLS-1$
        reporter.failed("Unit-test execution failed", null); //$NON-NLS-1$
        ToolResult result = failure != null ? failure : ToolResult.error("Unit-test execution failed"); //$NON-NLS-1$
        result.put("runId", runId); //$NON-NLS-1$
        return result.toJson();
    }

    private static String normalizeProvider(String provider)
    {
        return provider == null || provider.isBlank() ? YaxUnitRuntimeAdapter.PROVIDER : provider.trim().toLowerCase();
    }

    private static String normalizeScope(String scope)
    {
        return scope == null || scope.isBlank() ? DEFAULT_SCOPE : scope.trim().toLowerCase();
    }

    private static String normalizeTestExtension(String testExtension)
    {
        return testExtension == null || testExtension.isBlank() ? DEFAULT_TEST_EXTENSION : testExtension.trim();
    }

    private static String currentOwnerSessionId()
    {
        ToolExecutionContext context = ToolExecutionContextHolder.get();
        return context != null ? context.getSessionId() : null;
    }

    private static List<String> supportedSessionModes()
    {
        return List.of(UnitTestSessionMode.COLD.wireValue(), UnitTestSessionMode.PREFER_WARM.wireValue(),
                UnitTestSessionMode.REQUIRE_WARM.wireValue(), UnitTestSessionMode.RECYCLE_THEN_RUN.wireValue());
    }

    private static final class WarmExecutionAttempt
    {
        private final UnitTestRunRecord record;
        private final UnitTestSessionSnapshot sessionSnapshot;
        private final String message;

        private WarmExecutionAttempt(UnitTestRunRecord record, UnitTestSessionSnapshot sessionSnapshot, String message)
        {
            this.record = record;
            this.sessionSnapshot = sessionSnapshot;
            this.message = message;
        }

        static WarmExecutionAttempt executed(UnitTestRunRecord record, UnitTestSessionSnapshot sessionSnapshot)
        {
            return new WarmExecutionAttempt(record, sessionSnapshot, null);
        }

        static WarmExecutionAttempt failed(UnitTestSessionSnapshot sessionSnapshot, String message)
        {
            return new WarmExecutionAttempt(null, sessionSnapshot, message);
        }

        boolean isSuccess()
        {
            return record != null;
        }

        UnitTestRunRecord getRecord()
        {
            return record;
        }

        UnitTestSessionSnapshot getSessionSnapshot()
        {
            return sessionSnapshot;
        }

        String getMessage()
        {
            return message;
        }
    }
}
